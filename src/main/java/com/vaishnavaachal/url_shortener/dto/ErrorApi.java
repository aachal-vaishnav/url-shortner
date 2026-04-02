package com.vaishnavaachal.url_shortener.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorApi {

    private int statusCode;
    private String type;
    private String message;
    private Date timestamp; // renamed
}