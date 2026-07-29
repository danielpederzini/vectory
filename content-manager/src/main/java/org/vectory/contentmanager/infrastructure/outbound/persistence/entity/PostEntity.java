package org.vectory.contentmanager.infrastructure.outbound.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "posts")
public class PostEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String text;

    @Column(name = "creation_date_time", nullable = false)
    private LocalDateTime creationDateTime;

    @Embedded
    private PostMedia media;
}
