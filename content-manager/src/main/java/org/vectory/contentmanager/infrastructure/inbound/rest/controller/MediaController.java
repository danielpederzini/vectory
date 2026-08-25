package org.vectory.contentmanager.infrastructure.inbound.rest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vectory.contentmanager.application.service.MediaService;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.MediaUploadRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.MediaUploadResponseDto;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/uploads")
    public ResponseEntity<MediaUploadResponseDto> createUpload(@Valid @RequestBody MediaUploadRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.createUpload(request));
    }

    @GetMapping("/{*objectKey}")
    public ResponseEntity<Void> download(@PathVariable String objectKey) {
        String key = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(mediaService.resolveDownloadUrl(key)))
                .build();
    }
}
