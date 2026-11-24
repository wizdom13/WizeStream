-- Back up the review database before upgrading schema v3 to v4.
-- Run after 001_developer_replies.sql and 002_pending_review_edits.sql.
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

UPDATE wize_review_guard SET schema_version = 4 WHERE id = 1 AND schema_version = 3;
