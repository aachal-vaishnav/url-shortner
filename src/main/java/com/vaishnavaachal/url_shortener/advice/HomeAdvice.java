package com.vaishnavaachal.url_shortener.advice;

import com.vaishnavaachal.url_shortener.dto.ErrorApi;
import com.vaishnavaachal.url_shortener.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class HomeAdvice {

    // VALIDATION ERROR HANDLER
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalidArgument(MethodArgumentNotValidException ex) {

        Map<String, String> errorMap = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errorMap.put(error.getField(), error.getDefaultMessage())
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMap);
    }

    // NOT FOUND HANDLER
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorApi> handleNotFound(NotFoundException ex) {

        ErrorApi errorApi = new ErrorApi();
        errorApi.setTimestamp(new Date());
        errorApi.setType("NOT_FOUND");
        errorApi.setStatusCode(HttpStatus.NOT_FOUND.value());
        errorApi.setMessage(ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorApi);
    }

    // GENERIC EXCEPTION HANDLER (VERY IMPORTANT)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorApi> handleGeneralException(Exception ex) {

        ErrorApi errorApi = new ErrorApi();
        errorApi.setTimestamp(new Date());
        errorApi.setType("INTERNAL_SERVER_ERROR");
        errorApi.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorApi.setMessage(ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorApi);
    }
}