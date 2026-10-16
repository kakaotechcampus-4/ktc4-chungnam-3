CREATE TABLE business_license (
    id UUID PRIMARY KEY,
    source_id VARCHAR(100) NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    category VARCHAR(100),
    license_status VARCHAR(20) NOT NULL,
    road_address VARCHAR(500),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_business_license_source_id UNIQUE (source_id)
);

CREATE INDEX idx_business_license_lat_lon ON business_license (latitude, longitude);
CREATE INDEX idx_business_license_status ON business_license (license_status);
