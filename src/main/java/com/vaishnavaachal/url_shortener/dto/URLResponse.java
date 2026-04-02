package com.vaishnavaachal.url_shortener.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class URLResponse {

    private String fullURL;
    private String shortURL;

}