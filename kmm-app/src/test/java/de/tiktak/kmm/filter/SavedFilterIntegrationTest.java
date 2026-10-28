package de.tiktak.kmm.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class SavedFilterIntegrationTest {

    private static final String JSON = "application/json";
    private static final String UNWATCHED = """
            {"mediaType": "MOVIE", "name": "Ungesehen", "definition": {"matchAll": true,
             "conditions": [{"field": "WATCHED", "operator": "IS", "value": "false"}]}}""";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SavedFilterService service;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("DELETE FROM saved_filter").update();
    }

    @Test
    void createsListsRenamesAndDeletesFilters() {
        MvcTestResult created = post(UNWATCHED);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        long id = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();
        assertThat(post(UNWATCHED.replace("Ungesehen", "UNGESEHEN"))).hasStatus(HttpStatus.CONFLICT);
        assertThat(post(UNWATCHED.replace("MOVIE", "TV_SHOW"))).hasStatus(HttpStatus.CREATED);

        assertThat(mockMvc.put().uri(SavedFilterController.BASE_PATH + "/{id}", id).with(csrf()).contentType(JSON)
                .content(UNWATCHED.replace("Ungesehen", "Noch nicht gesehen"))).hasStatusOk();

        assertThat(mockMvc.get().uri(SavedFilterController.BASE_PATH + "?mediaType=MOVIE")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        [{"name": "Noch nicht gesehen", "mediaType": "MOVIE",
                          "definition": {"matchAll": true, "conditions": [{"field": "WATCHED", "value": "false"}]}}]""");
        assertThat(mockMvc.put().uri(SavedFilterController.BASE_PATH + "/{id}", id).with(csrf()).contentType(JSON)
                .content(UNWATCHED.replace("MOVIE", "TV_SHOW"))).hasStatus(HttpStatus.NOT_FOUND);

        assertThat(mockMvc.delete().uri(SavedFilterController.BASE_PATH + "/{id}", id).with(csrf()))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mockMvc.delete().uri(SavedFilterController.BASE_PATH + "/{id}", id).with(csrf()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsFiltersWithoutNameOrDefinitionAndTooLargeOnes() {
        assertThat(post("{\"mediaType\": \"MOVIE\", \"name\": \" \", \"definition\": {}}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("{\"mediaType\": \"MOVIE\", \"name\": \"X\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("{\"mediaType\": \"MOVIE\", \"name\": \"X\", \"definition\": {\"search\": \"%s\"}}"
                .formatted("x".repeat(SavedFilterService.MAX_DEFINITION_LENGTH)))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void importReplacesAFilterOfTheSameName() {
        service.importFilter(new SavedFilterData(FilterMediaType.MOVIE, "Ember", objectMapper.readTree("{\"a\": 1}")));
        service.importFilter(new SavedFilterData(FilterMediaType.MOVIE, "ember", objectMapper.readTree("{\"a\": 2}")));

        assertThat(service.findAll(FilterMediaType.MOVIE)).singleElement()
                .satisfies(filter -> assertThat(filter.definition().get("a").asInt()).isEqualTo(2));
    }

    private MvcTestResult post(String body) {
        return mockMvc.post().uri(SavedFilterController.BASE_PATH).with(csrf()).contentType(JSON).content(body)
                .exchange();
    }
}
