# CloudBox 私有文件管理与分享系统

> 参考项目：[zfile-dev/zfile](https://github.com/zfile-dev/zfile)
>
> 文档版本：V1.0 | 日期：2026-07-30 | 面向：Java 后端练手项目

## 1. 项目定位

CloudBox 是一个面向个人或小团队的私有文件管理系统。用户可以建立目录、上传和下载文件、移动或重命名文件、创建受密码和有效期保护的分享链接，并通过回收站恢复误删内容。

本项目借鉴 ZFile 的文件浏览和分享思路，但不复制其完整能力。V1 采用单体架构，只实现一条完整、可靠、可演示的文件管理主链路。

### 1.1 V1 必做范围

- 用户注册、登录、刷新令牌、退出登录
- 用户资料、修改密码、存储用量与配额
- 目录创建、列表查询、重命名、移动、复制
- 文件上传、下载、预览和元数据查询
- 批量删除、回收站、恢复和彻底删除
- 文件或目录分享、访问口令、有效期和下载次数限制
- 管理员查看用户、启停用户和调整配额
- 统一异常、参数校验、接口文档和 Docker Compose 部署

### 1.2 暂不实现

- OneDrive、阿里云盘、115 等第三方网盘接入
- 多存储源管理、SSO、OnlyOffice 在线编辑
- 文件版本历史、团队空间、复杂 ACL 权限
- 分布式部署、微服务、消息队列和分布式事务
- WebDAV、客户端同步盘和端到端加密

这些能力不是被永久删除，而是放到 V2 以后。V1 完整性比技术数量更重要。

## 2. 推荐技术栈

| 层次 | 技术 | 用途 |
|---|---|---|
| 运行环境 | Java 21 | LTS 版本 |
| Web 框架 | Spring Boot 3.3+ | REST API、依赖注入 |
| 权限 | Spring Security 6 + JWT | 登录认证、角色鉴权 |
| 持久层 | MyBatis-Plus | 数据访问与分页 |
| 数据库 | MySQL 8 | 用户、文件元数据、分享记录 |
| 缓存 | Redis 7 | Refresh Token、分享验证票据、限流 |
| 文件存储 | 本地磁盘 | V1 默认实现 |
| 对象存储 | MinIO | V1.1 可选实现 |
| 数据库迁移 | Flyway | 版本化维护建表脚本 |
| API 文档 | springdoc-openapi + Swagger UI | 在线调试接口 |
| 测试 | JUnit 5、Mockito、Testcontainers | 单元和集成测试 |
| 部署 | Docker Compose、Nginx | 一键运行和反向代理 |

前端可以使用 `Vue 3 + TypeScript + Vite + Pinia + Element Plus`。若当前重点是后端，可先用 Swagger UI 完成联调。

## 3. 总体架构

```text
Browser / Vue
      | HTTP/JWT
      v
Spring Boot API
  |-- auth        登录与令牌
  |-- user        用户、配额
  |-- file        目录和文件元数据
  |-- storage     文件字节存储抽象
  |-- share       分享及公开访问
  |-- trash       回收站
  `-- admin       用户管理
      |       |
      v       v
    MySQL    Redis
      |
      v
LocalStorage / MinIO
```

建议采用按业务模块组织的包结构：

```text
com.example.cloudbox
├── common
│   ├── exception
│   ├── response
│   ├── security
│   └── web
├── auth
├── user
├── file
├── storage
│   ├── StorageService.java
│   ├── LocalStorageService.java
│   └── MinioStorageService.java       # V1.1
├── share
├── trash
└── admin
```

`StorageService` 至少提供 `store`、`load`、`delete`、`copy` 和 `exists`。Controller 和文件业务层不允许直接调用 `Files` 或 MinIO SDK。

## 4. 核心数据模型

数据库主键统一使用 `BIGINT`。API 中的 ID 使用 JSON number；如果前端出现 JavaScript 精度问题，统一改为字符串输出。时间使用 UTC 存储，接口采用 ISO 8601，如 `2026-07-30T10:30:00Z`。

### 4.1 `users` 用户表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| username | VARCHAR(32) UNIQUE | 用户名 |
| email | VARCHAR(128) UNIQUE | 邮箱 |
| password_hash | VARCHAR(255) | BCrypt/Argon2 密文 |
| role | VARCHAR(16) | `USER` / `ADMIN` |
| status | VARCHAR(16) | `ACTIVE` / `DISABLED` |
| quota_bytes | BIGINT | 总配额 |
| used_bytes | BIGINT | 已使用空间 |
| version | INT | 乐观锁版本 |
| created_at | DATETIME(3) | 创建时间 |
| updated_at | DATETIME(3) | 更新时间 |

### 4.2 `file_nodes` 文件节点表

目录和文件统一建模为节点，避免分别维护两棵结构。根目录不保存实体记录，使用 `parentId=null` 表示根目录。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| owner_id | BIGINT | 所有者 |
| parent_id | BIGINT NULL | 父目录，根目录为 NULL |
| node_type | VARCHAR(16) | `FILE` / `FOLDER` |
| name | VARCHAR(255) | 节点名称 |
| size_bytes | BIGINT | 目录为 0 |
| content_type | VARCHAR(128) NULL | MIME 类型 |
| storage_key | VARCHAR(512) NULL | 物理存储键，目录为空 |
| checksum | CHAR(64) NULL | SHA-256，V1.1 可用于秒传 |
| status | VARCHAR(16) | `ACTIVE` / `TRASHED` |
| original_parent_id | BIGINT NULL | 回收站恢复位置 |
| deleted_at | DATETIME(3) NULL | 移入回收站时间 |
| version | INT | 乐观锁版本 |
| created_at | DATETIME(3) | 创建时间 |
| updated_at | DATETIME(3) | 更新时间 |

关键约束：

- 同一用户、同一父目录下，活动节点名称不得重复。
- 父节点必须属于同一用户且类型必须是目录。
- 移动目录时，目标不能是自身或自身后代。
- 文件在磁盘上的名称必须使用随机 `storage_key`，不能直接使用用户提交的文件名。
- 删除目录时，将整棵子树标记为回收站；彻底删除才释放物理文件和配额。

### 4.3 `shares` 分享表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| owner_id | BIGINT | 创建者 |
| share_code | VARCHAR(32) UNIQUE | 对外分享码 |
| title | VARCHAR(128) | 分享标题 |
| password_hash | VARCHAR(255) NULL | 访问口令哈希 |
| expires_at | DATETIME(3) NULL | 过期时间，NULL 为永久 |
| download_limit | INT NULL | 最大下载次数 |
| download_count | INT | 当前下载次数 |
| status | VARCHAR(16) | `ACTIVE` / `CANCELLED` |
| created_at | DATETIME(3) | 创建时间 |
| updated_at | DATETIME(3) | 更新时间 |

### 4.4 `share_items` 分享节点关系表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| share_id | BIGINT | 分享 ID |
| node_id | BIGINT | 被分享的文件或目录 |

`(share_id, node_id)` 添加唯一索引。V1 创建分享后，被分享节点移入回收站或彻底删除时，该节点不可访问。

### 4.5 `operation_logs` 操作日志表

记录登录、上传、下载、删除、恢复、创建分享、取消分享和管理员操作。字段建议为 `operator_id`、`action`、`target_type`、`target_id`、`detail_json`、`ip`、`created_at`。该表不参与主业务事务失败回滚，可在 V1 后半段补充。

## 5. 关键业务规则

### 5.1 身份与权限

- Access Token 有效期 30 分钟；Refresh Token 有效期 7 天。
- Access Token 放在 `Authorization: Bearer <token>`。
- Refresh Token 存 Redis，退出后立即删除。
- 普通用户只能访问 `owner_id` 为自己的节点和分享。
- 管理员不能通过普通文件接口读取其他用户文件，只能管理账户和配额。这样能保持权限边界清晰。

### 5.2 上传一致性

上传顺序建议如下：

1. 校验用户、父目录、文件名、冲突策略和配额。
2. 将文件写入临时目录，同时计算 SHA-256。
3. 原子移动到正式存储位置。
4. 在数据库事务中写入文件元数据并增加 `used_bytes`。
5. 数据库失败时删除已经写入的物理文件。

V1 单文件上限建议为 100 MiB。不要把整个文件读取成 `byte[]`，必须流式写入。

### 5.3 文件名冲突

上传、复制和移动接口均接收 `conflictPolicy`：

- `REJECT`：默认，返回 `FILE_NAME_CONFLICT`。
- `RENAME`：自动生成 `文件名 (1).ext`。
- `REPLACE`：仅 V1.1 支持，V1 返回参数错误。

### 5.4 删除与配额

- 普通删除只移入回收站，不减少 `used_bytes`。
- 恢复时优先放回 `original_parent_id`；父目录不存在则恢复到根目录。
- 彻底删除文件后才删除物理对象并减少配额。
- 彻底删除目录需要统计其所有文件大小，并在同一事务中扣减配额。
- 管理员降低配额时可以低于已用空间，但用户在释放空间前不能继续上传。

### 5.5 分享安全

- 分享口令只保存哈希，不保存明文。
- 正确输入口令后签发 30 分钟的 `shareToken`，后续放在 `X-Share-Token` 请求头。
- 分享码应使用安全随机数生成，不能暴露数据库主键。
- 下载前必须再次检查分享状态、过期时间、节点状态和下载次数。
- 下载次数更新使用 SQL 条件更新或乐观锁，避免并发突破限制。

## 6. REST API 约定

### 6.1 基础信息

- Base URL：`/api/v1`
- Content-Type：除上传外均为 `application/json`
- 上传：`multipart/form-data`
- 时间：ISO 8601 UTC
- 分页从 1 开始，默认 `page=1&size=20`，最大 `size=100`

### 6.2 成功响应

```json
{
  "code": "OK",
  "message": "success",
  "data": {}
}
```

分页响应的 `data`：

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "total": 0,
  "pages": 0
}
```

删除等无返回数据的接口仍返回 HTTP 200，`data` 为 `null`。文件流接口例外，不使用统一 JSON。

### 6.3 错误响应

```json
{
  "code": "FILE_NAME_CONFLICT",
  "message": "目标目录中已存在同名文件",
  "requestId": "01J4...",
  "errors": null
}
```

| HTTP | 错误码 | 场景 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 参数校验失败 |
| 400 | `INVALID_OPERATION` | 目录移动到自身后代等非法操作 |
| 401 | `UNAUTHORIZED` | 未登录或令牌失效 |
| 401 | `INVALID_CREDENTIALS` | 用户名或密码错误 |
| 403 | `FORBIDDEN` | 无权限访问资源 |
| 403 | `SHARE_PASSWORD_REQUIRED` | 分享需要口令 |
| 403 | `SHARE_PASSWORD_INVALID` | 分享口令错误 |
| 404 | `USER_NOT_FOUND` | 用户不存在 |
| 404 | `FILE_NOT_FOUND` | 节点不存在 |
| 404 | `SHARE_NOT_FOUND` | 分享不存在 |
| 409 | `FILE_NAME_CONFLICT` | 同目录名称冲突 |
| 409 | `USERNAME_EXISTS` | 用户名已存在 |
| 409 | `EMAIL_EXISTS` | 邮箱已存在 |
| 410 | `SHARE_EXPIRED` | 分享已过期或取消 |
| 410 | `DOWNLOAD_LIMIT_REACHED` | 达到下载上限 |
| 413 | `FILE_TOO_LARGE` | 文件超过限制 |
| 422 | `QUOTA_EXCEEDED` | 用户空间不足 |
| 429 | `TOO_MANY_REQUESTS` | 登录或分享口令尝试过多 |
| 500 | `STORAGE_ERROR` | 物理存储异常 |

## 7. 接口清单

详细字段、请求体和响应 Schema 见同目录的 `cloudbox-openapi.yaml`。

### 7.1 认证

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/auth/register` | 否 | 注册 |
| POST | `/auth/login` | 否 | 登录 |
| POST | `/auth/refresh` | 否 | 刷新令牌 |
| POST | `/auth/logout` | 是 | 退出并吊销 Refresh Token |

### 7.2 当前用户

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/users/me` | 获取个人资料 |
| PATCH | `/users/me` | 修改邮箱等资料 |
| PUT | `/users/me/password` | 修改密码 |
| GET | `/users/me/storage` | 查询配额和用量 |

### 7.3 文件与目录

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/nodes` | 查询目录内容或搜索节点 |
| GET | `/nodes/{nodeId}` | 查询节点详情和面包屑 |
| POST | `/folders` | 新建目录 |
| PATCH | `/nodes/{nodeId}` | 重命名 |
| POST | `/nodes/{nodeId}/move` | 移动节点 |
| POST | `/nodes/copy` | 批量复制 |
| DELETE | `/nodes/{nodeId}` | 移入回收站 |
| POST | `/nodes/batch-delete` | 批量移入回收站 |
| POST | `/files/upload` | 上传单文件 |
| GET | `/files/{fileId}/content` | 下载文件，支持 Range |
| GET | `/files/{fileId}/preview` | 浏览器内联预览 |

### 7.4 回收站

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/trash` | 查询回收站根节点 |
| POST | `/trash/{nodeId}/restore` | 恢复节点 |
| DELETE | `/trash/{nodeId}` | 彻底删除 |
| DELETE | `/trash` | 清空当前用户回收站 |

### 7.5 分享管理与公开访问

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/shares` | JWT | 创建分享 |
| GET | `/shares` | JWT | 我的分享列表 |
| GET | `/shares/{shareId}` | JWT | 分享详情 |
| DELETE | `/shares/{shareId}` | JWT | 取消分享 |
| GET | `/public/shares/{shareCode}` | 否 | 分享公开信息 |
| POST | `/public/shares/{shareCode}/verify` | 否 | 验证访问口令 |
| GET | `/public/shares/{shareCode}/nodes` | 可选 Share Token | 浏览分享内容 |
| GET | `/public/shares/{shareCode}/files/{fileId}/content` | 可选 Share Token | 下载分享文件 |

### 7.6 管理员

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/users` | 分页查询用户 |
| PATCH | `/admin/users/{userId}/status` | 启用或禁用用户 |
| PATCH | `/admin/users/{userId}/quota` | 调整配额 |

## 8. 重点接口示例

### 8.1 上传文件

```http
POST /api/v1/files/upload
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data

parentId=123
conflictPolicy=REJECT
file=@report.pdf
```

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 901,
    "parentId": 123,
    "type": "FILE",
    "name": "report.pdf",
    "sizeBytes": 583102,
    "contentType": "application/pdf",
    "createdAt": "2026-07-30T10:30:00Z",
    "updatedAt": "2026-07-30T10:30:00Z"
  }
}
```

### 8.2 创建分享

```http
POST /api/v1/shares
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "nodeIds": [901],
  "title": "项目资料",
  "password": "4832",
  "expiresAt": "2026-08-06T10:30:00Z",
  "downloadLimit": 10
}
```

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 55,
    "shareCode": "yN8k3JfQ",
    "shareUrl": "https://cloudbox.example/s/yN8k3JfQ",
    "title": "项目资料",
    "hasPassword": true,
    "expiresAt": "2026-08-06T10:30:00Z",
    "downloadLimit": 10,
    "downloadCount": 0,
    "status": "ACTIVE"
  }
}
```

### 8.3 访问受密码保护的分享

1. `GET /public/shares/yN8k3JfQ` 得知 `passwordRequired=true`。
2. `POST /public/shares/yN8k3JfQ/verify`，请求体为 `{"password":"4832"}`。
3. 保存响应中的 `shareToken`。
4. 浏览和下载时携带 `X-Share-Token: <shareToken>`。

## 9. 开发迭代计划

### 第 0 阶段：工程准备，1～2 天

- 创建 Spring Boot 工程和模块目录
- 接入 MySQL、MyBatis-Plus、Flyway、Redis、springdoc
- 实现统一响应、全局异常和参数校验
- 编写 Docker Compose，启动 MySQL 与 Redis
- 交付：健康检查和 Swagger UI 可访问

### 第 1 阶段：认证与用户，3～4 天

- 注册、登录、JWT、Refresh Token、退出
- 当前用户资料和修改密码
- Spring Security 路由权限
- 交付：认证接口集成测试通过

### 第 2 阶段：目录和文件主链路，7～10 天

- 节点表和目录树规则
- 目录列表、新建、详情、重命名、移动
- 本地 `StorageService`
- 流式上传、下载、内联预览
- 配额校验和文件名冲突处理
- 交付：用户能完整管理自己的文件

### 第 3 阶段：回收站与分享，5～7 天

- 软删除、恢复、彻底删除、清空回收站
- 创建分享、取消分享、过期判断
- 分享口令票据和下载次数限制
- 交付：匿名用户可通过分享链接安全下载文件

### 第 4 阶段：管理与质量，4～6 天

- 管理员用户查询、状态和配额管理
- 操作日志、登录和分享口令限流
- Testcontainers 集成测试
- Docker 镜像、README、部署说明和演示数据
- 交付：项目可一键运行，并覆盖关键异常路径

总周期约 4～6 周。先完成每阶段的验收条件，再进入下一阶段。

## 10. 测试清单

至少覆盖以下场景：

- 用户 A 不能读取、移动或删除用户 B 的节点。
- 目录不能移动到自身或自己的后代目录。
- 同目录不能存在同名活动节点。
- 上传超额时不产生数据库记录和残留物理文件。
- 数据库写入失败时已保存的物理文件得到补偿删除。
- Range 下载返回 `206 Partial Content` 和正确的 `Content-Range`。
- 删除目录后，其后代不会从普通列表或分享接口泄露。
- 回收站恢复遇到重名时按冲突策略处理。
- 分享到期、取消、口令错误和达到下载上限时均被拒绝。
- 并发下载不会突破分享下载次数限制。
- 禁用用户的旧 Access Token 不能继续访问受保护接口。

## 11. V1.1 挑战功能

完成 V1 后按以下顺序选做，不建议同时开发：

1. **分片上传与断点续传**：增加 `upload_sessions` 和分片合并接口。
2. **秒传与物理去重**：将物理对象抽为 `file_objects`，根据 SHA-256 和引用计数复用。
3. **MinIO 存储实现**：保持业务接口不变，仅增加 `StorageService` 实现。
4. **图片缩略图和视频元数据**：后台线程池异步生成派生文件。
5. **病毒扫描**：上传后交给 ClamAV 扫描，未通过前禁止分享。

## 12. 简历描述参考

在功能真实完成并经过测试后，可以写成：

> 基于 Java 21、Spring Boot、MySQL、Redis 实现私有文件管理与分享系统，完成目录树、流式上传下载、存储配额、回收站和受密码保护的限时分享；通过存储适配层隔离本地磁盘与对象存储，并使用条件更新保证并发下载场景下的次数限制。

不要写没有测试数据支撑的“高并发”“海量文件”或“分布式”描述。
