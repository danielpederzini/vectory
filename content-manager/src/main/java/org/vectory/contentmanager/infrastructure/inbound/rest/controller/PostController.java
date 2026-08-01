package org.vectory.contentmanager.infrastructure.inbound.rest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vectory.contentmanager.application.service.PostService;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostResponseDto;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<PostResponseDto> create(@Valid @RequestBody PostCreationRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.create(request));
    }
}
