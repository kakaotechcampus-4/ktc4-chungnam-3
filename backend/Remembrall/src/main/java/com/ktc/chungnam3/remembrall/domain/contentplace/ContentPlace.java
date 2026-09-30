package com.ktc.chungnam3.remembrall.domain.contentplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.ktc.chungnam3.remembrall.domain.content.Content;
import com.ktc.chungnam3.remembrall.domain.place.Place;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "content_place", uniqueConstraints = @UniqueConstraint(
        name = "uk_content_place_content_place",
        columnNames = {"content_id", "place_id"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentPlace {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "content_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_content_place_content")
    )
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "place_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_content_place_place")
    )
    private Place place;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private ContentPlace(
            UUID id,
            Content content,
            Place place,
            String description,
            Instant createdAt
    ) {
        this.id = id;
        this.content = content;
        this.place = place;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static ContentPlace create(
            Content content,
            Place place,
            String description,
            Instant createdAt
    ) {
        return new ContentPlace(
                UUID.randomUUID(),
                content,
                place,
                description,
                createdAt
        );
    }
}
