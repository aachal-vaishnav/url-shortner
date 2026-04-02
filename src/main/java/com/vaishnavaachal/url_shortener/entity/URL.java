package com.vaishnavaachal.url_shortener.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "url")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class URL {

    @Id
    @Column(name = "short_url", nullable = false, updatable = false)
    private String shortenedURL;

    @Column(name = "full_url", nullable = false, unique = true)
    private String fullURL;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;
}