package de.tiktak.kmm.kodi;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface KodiHostRepository extends JpaRepository<KodiHost, Long> {

    Optional<KodiHost> findByNameIgnoreCase(String name);
}
