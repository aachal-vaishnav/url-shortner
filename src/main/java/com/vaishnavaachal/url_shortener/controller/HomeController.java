package com.vaishnavaachal.url_shortener.controller;

import com.vaishnavaachal.url_shortener.dto.URLResponse;
import com.vaishnavaachal.url_shortener.exception.NotFoundException;
import com.vaishnavaachal.url_shortener.service.URLService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@RestController
@RequestMapping("/api") // better API design
public class HomeController {

    @Autowired
    private URLService service;

    //  REDIRECT API
    @GetMapping("/{shortURL}")
    public RedirectView redirect(@PathVariable String shortURL) throws NotFoundException {
        return new RedirectView(service.get(shortURL));
    }

    //  SHORTEN API
    @PostMapping("/shorten")
    public ResponseEntity<URLResponse> saveNewURL(@RequestBody Map<String, String> request) {

        String url = request.get("url");

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        URLResponse response = service.save(url);

        return ResponseEntity.status(HttpStatus.CREATED).body(response); //  201 CREATED
    }
}