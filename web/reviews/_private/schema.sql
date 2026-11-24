-- Import once with phpMyAdmin into the dedicated review database. MySQL 5.7+/MariaDB.
CREATE TABLE IF NOT EXISTS wize_review_guard (
    id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
    schema_version TINYINT UNSIGNED NOT NULL
) ENGINE=InnoDB;
INSERT IGNORE INTO wize_review_guard (id, schema_version) VALUES (1, 4);

CREATE TABLE IF NOT EXISTS wize_reviews (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    rating TINYINT UNSIGNED NOT NULL,
    nickname VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    app_version VARCHAR(128) NOT NULL,
    delete_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    state ENUM('pending', 'published') NOT NULL,
    created_day CHAR(10) CHARACTER SET ascii NOT NULL,
    submitted_at BIGINT NOT NULL,
    INDEX wize_review_state_date (state, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wize_review_replies (
    review_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    content TEXT NOT NULL,
    updated_day CHAR(10) CHARACTER SET ascii NOT NULL,
    CONSTRAINT fk_review_reply_author FOREIGN KEY (review_id)
        REFERENCES wize_reviews(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wize_review_edits (
    review_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    rating TINYINT UNSIGNED NOT NULL,
    nickname VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    app_version VARCHAR(128) NOT NULL,
    submitted_at BIGINT NOT NULL,
    CONSTRAINT fk_review_edit_author FOREIGN KEY (review_id)
        REFERENCES wize_reviews(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wize_review_images (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    review_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    width INT UNSIGNED NOT NULL,
    height INT UNSIGNED NOT NULL,
    bytes INT UNSIGNED NOT NULL,
    thumb_bytes INT UNSIGNED NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE KEY wize_review_image_owner (review_id, id),
    CONSTRAINT fk_review_image_author FOREIGN KEY (review_id)
        REFERENCES wize_reviews(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS wize_review_image_links (
    review_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    image_set ENUM('current', 'edit') NOT NULL,
    position TINYINT UNSIGNED NOT NULL,
    image_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (review_id, image_set, position),
    UNIQUE KEY wize_review_image_once (review_id, image_set, image_id),
    CONSTRAINT fk_review_image_link_owner FOREIGN KEY (review_id, image_id)
        REFERENCES wize_review_images(review_id, id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Files are deleted only after the corresponding database transaction commits.
CREATE TABLE IF NOT EXISTS wize_review_image_gc (
    image_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS wize_review_challenges (
    digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    expires_at BIGINT NOT NULL,
    INDEX wize_challenge_expiry (expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS wize_review_limits (
    bucket_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    attempts INT UNSIGNED NOT NULL,
    expires_at BIGINT NOT NULL,
    INDEX wize_limit_expiry (expires_at)
) ENGINE=InnoDB;
