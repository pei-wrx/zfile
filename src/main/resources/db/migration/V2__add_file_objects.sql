-- Split physical storage objects from user-visible file nodes.
CREATE TABLE IF NOT EXISTS file_objects
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    checksum         CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    size_bytes       BIGINT       NOT NULL,
    content_type     VARCHAR(128) NULL,
    storage_key      VARCHAR(512) NOT NULL,
    reference_count  BIGINT       NOT NULL DEFAULT 0,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                             ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_objects_checksum (checksum),
    UNIQUE KEY uk_file_objects_storage_key (storage_key),
    KEY idx_file_objects_reference_count (reference_count),
    CONSTRAINT chk_file_objects_size_bytes CHECK (size_bytes >= 0),
    CONSTRAINT chk_file_objects_reference_count CHECK (reference_count >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Shared physical file objects';

ALTER TABLE file_nodes
    ADD COLUMN file_object_id BIGINT NULL AFTER size_bytes,
    ADD KEY idx_file_nodes_file_object_id (file_object_id),
    DROP INDEX uk_file_nodes_storage_key;

-- A non-null checksum identifies one shared physical object. The smallest
-- existing storage key is kept as the canonical object; the startup cleanup
-- removes the other historical copies after all references are migrated.
INSERT INTO file_objects (checksum, size_bytes, content_type, storage_key, reference_count)
SELECT checksum, MAX(size_bytes), MAX(content_type), MIN(storage_key), COUNT(*)
FROM file_nodes
WHERE node_type = 'FILE'
  AND checksum IS NOT NULL
GROUP BY checksum;

-- Legacy rows without a checksum cannot be safely merged. Keep one object
-- per storage key so their contents remain addressable after migration.
INSERT INTO file_objects (checksum, size_bytes, content_type, storage_key, reference_count)
SELECT NULL, size_bytes, content_type, storage_key, 1
FROM file_nodes
WHERE node_type = 'FILE'
  AND checksum IS NULL
  AND storage_key IS NOT NULL;

UPDATE file_nodes n
JOIN file_objects o
  ON o.checksum = n.checksum
SET n.file_object_id = o.id
WHERE n.node_type = 'FILE'
  AND n.checksum IS NOT NULL;

UPDATE file_nodes n
JOIN file_objects o
  ON o.checksum IS NULL
 AND o.storage_key = n.storage_key
SET n.file_object_id = o.id
WHERE n.node_type = 'FILE'
  AND n.checksum IS NULL
  AND n.storage_key IS NOT NULL;
