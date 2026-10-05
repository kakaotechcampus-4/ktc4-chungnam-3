CREATE TABLE trigger (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL,
    place_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_trigger_member_place UNIQUE (member_id, place_id),
    CONSTRAINT fk_trigger_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    CONSTRAINT fk_trigger_place
        FOREIGN KEY (place_id) REFERENCES place(id) ON DELETE CASCADE
);

CREATE INDEX ix_trigger_place_id ON trigger (place_id);
