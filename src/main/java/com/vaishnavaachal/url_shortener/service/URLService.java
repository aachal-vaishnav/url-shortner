package com.vaishnavaachal.url_shortener.service;

import com.vaishnavaachal.url_shortener.dto.URLResponse;
import com.vaishnavaachal.url_shortener.entity.URL;
import com.vaishnavaachal.url_shortener.exception.NotFoundException;
import com.vaishnavaachal.url_shortener.repo.URLRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;
import java.util.Random;

@Service
public class URLService {

    @Autowired
    private URLRepo repo;

    @Value("${app.base-url}")
    private String baseUrl;

    // SAVE URL
    public URLResponse save(String urlStr) {

        Optional<URL> existing = repo.findByFullURL(urlStr);

        if (existing.isPresent()) {
            return buildResponse(existing.get());
        }

        URL url = URL.builder()
                .fullURL(urlStr)
                .shortenedURL(generateShortURL())
                .createdAt(new Date())
                .build();

        URL saved = repo.save(url);

        return buildResponse(saved);
    }

    // GET ORIGINAL URL
    public String get(String shortUrl) throws NotFoundException {
        Optional<URL> url = repo.findById(shortUrl);

        if (url.isEmpty()) {
            throw new NotFoundException("URL not found");
        }

        return url.get().getFullURL();
    }

    // BUILD RESPONSE (IMPORTANT)
    private URLResponse buildResponse(URL url) {
        return new URLResponse(
                url.getFullURL(),
                baseUrl + "/" + url.getShortenedURL()
        );
    }

    // GENERATE UNIQUE SHORT URL
    private String generateShortURL() {
        String validCharacters = "123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int idLength = 8;

        String shortUrl;

        do {
            StringBuilder randomId = new StringBuilder();
            Random random = new Random();

            for (int i = 0; i < idLength; i++) {
                randomId.append(validCharacters.charAt(random.nextInt(validCharacters.length())));
            }

            shortUrl = randomId.toString();

        } while (repo.existsById(shortUrl)); // ensure uniqueness

        return shortUrl;
    }
}