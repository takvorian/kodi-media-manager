package de.tiktak.kmm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import de.tiktak.kmm.plugin.PluginController;
import de.tiktak.kmm.plugin.PluginInfo;
import de.tiktak.kmm.plugin.PluginService;
import de.tiktak.kmm.system.ReleaseInfoService;
import de.tiktak.kmm.system.SystemInfo;
import de.tiktak.kmm.system.SystemInfoController;
import de.tiktak.kmm.system.SystemInfoService;
import jakarta.servlet.http.Cookie;

class SecurityConfigurationTest {

    private static final String USER = "tester";
    private static final String PASSWORD = "secret";
    private static final String PLUGIN_ID = "testdata";
    private static final String ENABLE_PATH = PluginController.BASE_PATH + "/" + PLUGIN_ID + "/enable";
    private static final PluginInfo PLUGIN = new PluginInfo(PLUGIN_ID, "1.0.0", "Test", "tiktak.de", "STARTED");

    @Nested
    @WebMvcTest(controllers = {SystemInfoController.class, PluginController.class, AuthController.class},
            properties = {
                    "kmm.security.enabled=true",
                    "spring.security.user.name=" + USER,
                    "spring.security.user.password=" + PASSWORD})
    @Import(WebSecurityTestConfiguration.class)
    class WhenEnabled {

        @Autowired
        private MockMvcTester mockMvc;

        @MockitoBean
        private SystemInfoService systemInfoService;

        @MockitoBean
        private ReleaseInfoService releaseInfoService;

        @MockitoBean
        private PluginService pluginService;

        @Test
        void allowsSystemInfoWithoutAuthentication() {
            when(systemInfoService.currentSystemInfo()).thenReturn(new SystemInfo("KMM", "1", "25", List.of(), "H2"));

            assertThat(mockMvc.get().uri(SystemInfoController.INFO_PATH)).hasStatusOk();
        }

        @Test
        void rejectsApiRequestWithoutAuthentication() {
            assertThat(mockMvc.get().uri(PluginController.BASE_PATH)).hasStatus(401);
        }

        @Test
        void neverMakesTheBrowserOpenItsOwnLoginDialog() {
            assertThat(mockMvc.get().uri(PluginController.BASE_PATH)).hasStatus(401)
                    .doesNotContainHeader(HttpHeaders.WWW_AUTHENTICATE);
        }

        @Test
        void tellsTheUserInterfaceThatNobodyIsSignedIn() {
            assertThat(mockMvc.get().uri(AuthController.ME_PATH)).hasStatusOk().bodyJson()
                    .isStrictlyEqualTo("{\"securityEnabled\": true, \"authenticated\": false, \"username\": null}");
        }

        @Test
        void tellsTheUserInterfaceWhoIsSignedIn() {
            assertThat(mockMvc.get().uri(AuthController.ME_PATH).with(user(USER))).hasStatusOk().bodyJson()
                    .isStrictlyEqualTo("{\"securityEnabled\": true, \"authenticated\": true, \"username\": \"%s\"}"
                            .formatted(USER));
        }

        @Test
        void signsInWithASessionThatGivesAccessToTheApi() {
            when(pluginService.installedPlugins()).thenReturn(List.of(PLUGIN));

            MvcTestResult login = login(PASSWORD, false);

            assertThat(login).hasStatusOk().bodyJson().extractingPath("$.username").isEqualTo(USER);
            assertThat(login).cookies().doesNotContainCookie(SecurityConfiguration.REMEMBER_ME_COOKIE);
            assertThat(mockMvc.get().uri(PluginController.BASE_PATH).session(sessionOf(login))).hasStatusOk();
        }

        @Test
        void rejectsALoginWithAWrongPassword() {
            assertThat(login("wrong", true)).hasStatus(401).bodyJson()
                    .isLenientlyEqualTo("{\"status\": 401, \"detailText\": {\"key\": \"%s\"}}"
                            .formatted(JsonLoginHandlers.FAILED_DETAIL.key()));
        }

        @Test
        void rejectsALoginWithoutCsrfToken() {
            assertThat(mockMvc.post().uri(AuthController.LOGIN_PATH).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("username", USER).param("password", PASSWORD)).hasStatus(403);
        }

        @Test
        void keepsTheUserSignedInWithTheRememberMeCookie() {
            when(pluginService.installedPlugins()).thenReturn(List.of(PLUGIN));

            MvcTestResult login = login(PASSWORD, true);

            Cookie rememberMe = login.getResponse().getCookie(SecurityConfiguration.REMEMBER_ME_COOKIE);
            assertThat(rememberMe).isNotNull();
            assertThat(rememberMe.getMaxAge()).isEqualTo(SecurityConfiguration.REMEMBER_ME_VALIDITY.toSeconds());
            assertThat(mockMvc.get().uri(PluginController.BASE_PATH).cookie(rememberMe)).hasStatusOk();
        }

        @Test
        void keepsTheRememberMeCookieValidAfterARestartButNotAfterAPasswordChange(
                @Value("${kmm.home}") Path home) {
            Cookie rememberMe = login(PASSWORD, true).getResponse()
                    .getCookie(SecurityConfiguration.REMEMBER_ME_COOKIE);

            assertThat(autoLoginAfterRestart(home, PASSWORD, rememberMe)).isNotNull()
                    .extracting(authentication -> authentication.getName()).isEqualTo(USER);
            assertThat(autoLoginAfterRestart(home, "changed", rememberMe)).isNull();
        }

        /** A new instance like after a restart: the stored password is {noop} again, not the re-encoded one. */
        private static Authentication autoLoginAfterRestart(Path home, String password, Cookie rememberMe) {
            StableRememberMeServices services = new StableRememberMeServices(RememberMeKey.readOrCreate(home),
                    new InMemoryUserDetailsManager(User.withUsername(USER).password("{noop}" + password).build()),
                    password);
            services.setCookieName(SecurityConfiguration.REMEMBER_ME_COOKIE);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(rememberMe);
            return services.autoLogin(request, new MockHttpServletResponse());
        }

        @Test
        void signsOutAndForgetsTheRememberMeCookie() {
            MockHttpSession session = sessionOf(login(PASSWORD, true));

            MvcTestResult logout = mockMvc.post().uri(AuthController.LOGOUT_PATH).session(session).with(csrf())
                    .exchange();

            assertThat(logout).hasStatusOk();
            assertThat(logout.getResponse().getCookie(SecurityConfiguration.REMEMBER_ME_COOKIE).getMaxAge()).isZero();
            assertThat(session.isInvalid()).isTrue();
        }

        private MvcTestResult login(String password, boolean rememberMe) {
            return mockMvc.post().uri(AuthController.LOGIN_PATH).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("username", USER).param("password", password)
                    .param(SecurityConfiguration.REMEMBER_ME_PARAMETER, String.valueOf(rememberMe))
                    .with(csrf()).exchange();
        }

        private static MockHttpSession sessionOf(MvcTestResult result) {
            return (MockHttpSession) result.getMvcResult().getRequest().getSession(false);
        }

        @Test
        void rejectsWrongPassword() {
            assertThat(mockMvc.get().uri(PluginController.BASE_PATH).with(httpBasic(USER, "wrong"))).hasStatus(401);
        }

        @Test
        void allowsApiRequestWithValidCredentials() {
            when(pluginService.installedPlugins()).thenReturn(List.of(PLUGIN));

            assertThat(mockMvc.get().uri(PluginController.BASE_PATH).with(httpBasic(USER, PASSWORD))).hasStatusOk();
        }

        @Test
        void rejectsStateChangingRequestWithoutCsrfToken() {
            assertThat(mockMvc.post().uri(ENABLE_PATH).with(httpBasic(USER, PASSWORD))).hasStatus(403)
                    .bodyJson().isLenientlyEqualTo("{\"status\": 403, \"detailText\": {\"key\": \"%s\"}}"
                            .formatted(ProblemAccessDeniedHandler.CSRF_DETAIL.key()));
        }

        @Test
        void issuesItsOwnCsrfCookie() {
            assertThat(mockMvc.get().uri("/movies")).cookies().containsCookie(SecurityConfiguration.CSRF_COOKIE);
        }

        @Test
        void ignoresTheCsrfCookieOfAnotherAppOnTheSameHost() {
            when(pluginService.enable(PLUGIN_ID)).thenReturn(PLUGIN);

            assertThat(mockMvc.post().uri(ENABLE_PATH).with(httpBasic(USER, PASSWORD))
                    .cookie(new Cookie("XSRF-TOKEN", "fremd"), new Cookie(SecurityConfiguration.CSRF_COOKIE, "eigen"))
                    .header(SecurityConfiguration.CSRF_HEADER, "eigen")).hasStatusOk();
        }

        @Test
        void rejectsATokenThatDoesNotMatchItsCookie() {
            assertThat(mockMvc.post().uri(ENABLE_PATH).with(httpBasic(USER, PASSWORD))
                    .cookie(new Cookie(SecurityConfiguration.CSRF_COOKIE, "eigen"))
                    .header(SecurityConfiguration.CSRF_HEADER, "fremd")).hasStatus(403);
        }

        @Test
        void allowsStateChangingRequestWithCsrfToken() {
            when(pluginService.enable(PLUGIN_ID)).thenReturn(PLUGIN);

            assertThat(mockMvc.post().uri(ENABLE_PATH).with(httpBasic(USER, PASSWORD)).with(csrf())).hasStatusOk();
        }

        @Test
        void allowsClientRoutesWithoutAuthentication() {
            assertThat(mockMvc.get().uri("/movies")).hasStatusOk();
        }
    }

    @Nested
    @WebMvcTest(controllers = {PluginController.class, AuthController.class},
            properties = "kmm.security.enabled=false")
    @Import(WebSecurityTestConfiguration.class)
    class WhenDisabled {

        @Autowired
        private MockMvcTester mockMvc;

        @MockitoBean
        private PluginService pluginService;

        @Test
        void allowsApiRequestWithoutAuthentication() {
            when(pluginService.installedPlugins()).thenReturn(List.of(PLUGIN));

            assertThat(mockMvc.get().uri(PluginController.BASE_PATH)).hasStatusOk();
        }

        @Test
        void tellsTheUserInterfaceThatNoLoginIsNeeded() {
            assertThat(mockMvc.get().uri(AuthController.ME_PATH)).hasStatusOk().bodyJson()
                    .isStrictlyEqualTo("{\"securityEnabled\": false, \"authenticated\": true, \"username\": null}");
        }

        @Test
        void stillRequiresCsrfTokenForStateChangingRequests() {
            assertThat(mockMvc.post().uri(ENABLE_PATH)).hasStatus(403);
        }
    }
}
