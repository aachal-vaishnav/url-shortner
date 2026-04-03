package com.vaishnavaachal.url_shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the URL Shortener application.
 *
 * Key features enabled here:
 *   @EnableCaching  → Activates Spring's Cache-Aside pattern with Redis
 *   @EnableAsync    → Enables asynchronous click-count updates (Blind Updates)
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
public class UrlShortenerApplication {

	public static void main(String[] args) {
		SpringApplication.run(UrlShortenerApplication.class, args);
	}
}
