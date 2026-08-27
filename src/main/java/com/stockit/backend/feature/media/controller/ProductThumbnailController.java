package com.stockit.backend.feature.media.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.feature.media.service.ProductThumbnailService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/media")
@Validated
public class ProductThumbnailController {

    private static final CacheControl THUMBNAIL_CACHE = CacheControl.maxAge(Duration.ofDays(30))
            .cachePrivate()
            .immutable();

    private final ProductThumbnailService productThumbnailService;

    public ProductThumbnailController(ProductThumbnailService productThumbnailService) {
        this.productThumbnailService = productThumbnailService;
    }

    @GetMapping(value = "/product-thumbnail", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> thumbnail(
            @RequestParam("url") @NotBlank @Size(max = 1200) String sourceUrl
    ) {
        ProductThumbnailService.Thumbnail thumbnail = productThumbnailService.get(sourceUrl);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(thumbnail.bytes().length)
                .cacheControl(THUMBNAIL_CACHE)
                .body(thumbnail.bytes());
    }
}
