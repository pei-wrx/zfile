-- Z-File V1 initial schema.
-- Relationships are maintained by application logic; no physical foreign keys are used.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS users
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    username      VARCHAR(32)  NOT NULL COMMENT 'Unique login name',
    email         VARCHAR(128) NOT NULL COMMENT 'Unique email address',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt or Argon2 password hash',
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER or ADMIN',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE or DISABLED',
    quota_bytes   BIGINT       NOT NULL DEFAULT 0 COMMENT 'Storage quota in bytes',
    used_bytes    BIGINT       NOT NULL DEFAULT 0 COMMENT 'Used storage in bytes',
    version       INT          NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time in UTC',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                           ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time in UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_status_created_at (status, created_at),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_users_quota_bytes CHECK (quota_bytes >= 0),
    CONSTRAINT chk_users_used_bytes CHECK (used_bytes >= 0),
    CONSTRAINT chk_users_version CHECK (version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Users and storage quotas';

CREATE TABLE IF NOT EXISTS file_nodes
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    owner_id             BIGINT       NOT NULL COMMENT 'Logical reference to users.id',
    parent_id            BIGINT       NULL COMMENT 'Logical self-reference; NULL means root directory',
    node_type            VARCHAR(16)  NOT NULL COMMENT 'FILE or FOLDER',
    name                 VARCHAR(255) NOT NULL COMMENT 'User-visible node name',
    size_bytes           BIGINT       NOT NULL DEFAULT 0 COMMENT 'File size in bytes; folders use 0',
    content_type         VARCHAR(128) NULL COMMENT 'MIME type; NULL for folders',
    storage_key          VARCHAR(512) NULL COMMENT 'Random physical storage key; NULL for folders',
    checksum             CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'SHA-256 in lowercase hexadecimal',
    status               VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE or TRASHED',
    original_parent_id   BIGINT       NULL COMMENT 'Logical parent reference used when restoring from trash',
    deleted_at           DATETIME(3)  NULL COMMENT 'Time moved to trash in UTC',
    version              INT          NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time in UTC',
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                              ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time in UTC',
    active_parent_key    BIGINT GENERATED ALWAYS AS
                             (CASE WHEN status = 'ACTIVE' THEN COALESCE(parent_id, 0) ELSE NULL END) STORED
                             COMMENT 'Normalizes root and ignores trashed rows for active-name uniqueness',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_nodes_active_name (owner_id, active_parent_key, name),
    UNIQUE KEY uk_file_nodes_storage_key (storage_key),
    KEY idx_file_nodes_owner_parent_status (owner_id, parent_id, status),
    KEY idx_file_nodes_owner_status_deleted_at (owner_id, status, deleted_at),
    KEY idx_file_nodes_original_parent_id (original_parent_id),
    KEY idx_file_nodes_checksum (checksum),
    CONSTRAINT chk_file_nodes_node_type CHECK (node_type IN ('FILE', 'FOLDER')),
    CONSTRAINT chk_file_nodes_status CHECK (status IN ('ACTIVE', 'TRASHED')),
    CONSTRAINT chk_file_nodes_size_bytes CHECK (size_bytes >= 0),
    CONSTRAINT chk_file_nodes_version CHECK (version >= 0),
    CONSTRAINT chk_file_nodes_parent_id CHECK (parent_id IS NULL OR parent_id > 0),
    CONSTRAINT chk_file_nodes_original_parent_id CHECK (original_parent_id IS NULL OR original_parent_id > 0),
    CONSTRAINT chk_file_nodes_storage_fields CHECK
        ((node_type = 'FOLDER'
            AND size_bytes = 0
            AND content_type IS NULL
            AND storage_key IS NULL
            AND checksum IS NULL)
         OR (node_type = 'FILE' AND storage_key IS NOT NULL)),
    CONSTRAINT chk_file_nodes_deleted_at CHECK
        ((status = 'ACTIVE' AND deleted_at IS NULL)
         OR (status = 'TRASHED' AND deleted_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Unified file and folder tree';

CREATE TABLE IF NOT EXISTS shares
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    owner_id       BIGINT       NOT NULL COMMENT 'Logical reference to users.id',
    share_code     VARCHAR(32)  NOT NULL COMMENT 'Public random share code',
    title          VARCHAR(128) NOT NULL COMMENT 'Share title',
    password_hash  VARCHAR(255) NULL COMMENT 'Optional access password hash',
    expires_at     DATETIME(3)  NULL COMMENT 'Expiry time in UTC; NULL means permanent',
    download_limit INT          NULL COMMENT 'Maximum downloads; NULL means unlimited',
    download_count INT          NOT NULL DEFAULT 0 COMMENT 'Completed download count',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, CANCELLED or EXPIRED',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time in UTC',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                          ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time in UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_shares_share_code (share_code),
    KEY idx_shares_owner_status_created_at (owner_id, status, created_at),
    KEY idx_shares_status_expires_at (status, expires_at),
    CONSTRAINT chk_shares_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_shares_download_limit CHECK (download_limit IS NULL OR download_limit > 0),
    CONSTRAINT chk_shares_download_count CHECK (download_count >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'File and folder shares';

CREATE TABLE IF NOT EXISTS share_items
(
    id       BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    share_id BIGINT NOT NULL COMMENT 'Logical reference to shares.id',
    node_id  BIGINT NOT NULL COMMENT 'Logical reference to file_nodes.id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_share_items_share_node (share_id, node_id),
    KEY idx_share_items_node_id (node_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Nodes included in each share';

CREATE TABLE IF NOT EXISTS operation_logs
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    operator_id BIGINT      NULL COMMENT 'Logical reference to users.id; NULL for anonymous or system actions',
    action      VARCHAR(64) NOT NULL COMMENT 'Operation type',
    target_type VARCHAR(32) NULL COMMENT 'Target resource type',
    target_id   BIGINT      NULL COMMENT 'Logical target resource ID',
    detail_json JSON        NULL COMMENT 'Structured operation details',
    ip          VARCHAR(45) NULL COMMENT 'IPv4 or IPv6 address',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time in UTC',
    PRIMARY KEY (id),
    KEY idx_operation_logs_operator_created_at (operator_id, created_at),
    KEY idx_operation_logs_target (target_type, target_id),
    KEY idx_operation_logs_action_created_at (action, created_at),
    KEY idx_operation_logs_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Audit log; written independently from core business transactions';
