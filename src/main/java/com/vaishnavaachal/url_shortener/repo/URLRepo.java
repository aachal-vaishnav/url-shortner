package com.vaishnavaachal.url_shortener.repo;

import com.vaishnavaachal.url_shortener.entity.URL;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface URLRepo extends JpaRepository<URL, String> {

    // Find existing URL to avoid duplicates
    Optional<URL> findByFullURL(String fullURL);

}