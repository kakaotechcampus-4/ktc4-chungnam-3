CREATE TABLE place (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    geocoding_provider VARCHAR(20) NOT NULL,
    geocoding_place_id VARCHAR(100) NOT NULL,
    verification_provider VARCHAR(20) NOT NULL,
    verification_place_id VARCHAR(100) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_place_verification_provider_place_id
        UNIQUE (verification_provider, verification_place_id),
    CONSTRAINT ck_place_geocoding_provider
        CHECK (geocoding_provider = 'LOCATIONIQ'),
    CONSTRAINT ck_place_verification_provider
        CHECK (verification_provider = 'KAKAO'),
    CONSTRAINT ck_place_latitude
        CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_place_longitude
        CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE content_place (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL,
    place_id UUID NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_content_place_content_place UNIQUE (content_id, place_id),
    CONSTRAINT fk_content_place_content
        FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE,
    CONSTRAINT fk_content_place_place
        FOREIGN KEY (place_id) REFERENCES place(id) ON DELETE CASCADE
);
