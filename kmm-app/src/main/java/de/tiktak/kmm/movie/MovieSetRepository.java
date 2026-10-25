package de.tiktak.kmm.movie;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface MovieSetRepository extends JpaRepository<MovieSet, Long> {

    Optional<MovieSet> findByName(String name);
}
