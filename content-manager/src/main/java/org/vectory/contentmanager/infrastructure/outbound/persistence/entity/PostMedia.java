package org.vectory.contentmanager.infrastructure.outbound.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.vectory.contentmanager.domain.enums.PostMediaType;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class PostMedia {

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type")
    private PostMediaType mediaType;

    @Column(name = "media_url")
    private String mediaUrl;
}
