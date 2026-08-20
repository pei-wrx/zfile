# 文件物理对象引用计数 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** 将重复文件的物理存储改为共享 `file_objects` 引用计数模型，并安全迁移现有 `file_nodes` 数据。

**Architecture:** 新增 `FileObject` 实体、Mapper 和服务，统一管理对象查询、引用增加/释放和物理对象生命周期。`FileNode` 增加 `fileObjectId` 并继续保留旧存储字段作为迁移兼容影子字段；上传预检、普通上传、节点复制、分享读取和回收站永久删除全部通过对象服务访问物理文件。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、Flyway、MySQL、JUnit 5、Mockito。

---

### Task 1: 添加对象模型和迁移

**Files:**
- Create: `src/main/java/com/pei/zfile/file/entity/FileObject.java`
- Create: `src/main/java/com/pei/zfile/file/mapper/FileObjectMapper.java`
- Create: `src/main/resources/db/migration/V2__add_file_objects.sql`
- Modify: `src/main/java/com/pei/zfile/file/entity/FileNode.java`

- [ ] 添加 `FileObject` 字段 `id/checksum/sizeBytes/contentType/storageKey/referenceCount/createdAt/updatedAt`，映射表 `file_objects`。
- [ ] 为 `FileObjectMapper` 添加 `@Mapper`，并增加 `insertIgnore`、条件引用增加、条件引用释放的 MySQL SQL 方法。
- [ ] 编写 V2：创建对象表；非空 checksum 按 checksum 合并，空 checksum 按 storage key 独立迁移；给 `file_nodes` 增加 `file_object_id` 和索引；删除旧 `storage_key` 唯一索引；回填所有历史文件节点。
- [ ] 在 `FileNode` 增加 `fileObjectId`，保留旧 `storageKey/checksum` 字段作为影子字段，确保迁移期间旧数据可读。

### Task 2: 先写对象引用生命周期测试

**Files:**
- Create: `src/test/java/com/pei/zfile/file/service/FileObjectServiceTest.java`
- Modify: `src/test/java/com/pei/zfile/file/service/FileAndNodeServiceTest.java`
- Modify: `src/test/java/com/pei/zfile/trash/service/TrashServiceImplTest.java`

- [ ] 测试已有 checksum 对象被预检命中时只增加引用，不调用 `StorageService.copy`。
- [ ] 测试普通上传命中已有对象时删除临时文件并复用对象。
- [ ] 测试节点复制复用 `fileObjectId` 并增加引用。
- [ ] 测试对象引用释放：引用数从 2 降到 1 时不返回待删除对象，从 1 降到 0 时返回对象。
- [ ] 先运行定向测试，确认新增行为在实现前失败。

### Task 3: 实现对象服务

**Files:**
- Create: `src/main/java/com/pei/zfile/file/service/FileObjectService.java`
- Create: `src/main/java/com/pei/zfile/file/service/Impl/FileObjectServiceImpl.java`

- [ ] 实现 `getRequired(Long)`、`findByChecksumAndSize(String, Long)`、`retain(Long)`、`createOrRetain(StoreResult, String)` 和 `releaseReferences(Collection<Long>)`。
- [ ] `createOrRetain` 在已有对象时删除临时文件并增加引用；新对象时提交临时文件、插入对象并设置引用数为 1；插入竞争时删除候选物理文件并转为已有对象引用。
- [ ] `releaseReferences` 按对象 ID 聚合计数，用条件 SQL 防止负数，删除引用数归零的对象行并返回其 storage key。
- [ ] 定向对象服务测试通过。

### Task 4: 改造上传和文件读取

**Files:**
- Modify: `src/main/java/com/pei/zfile/file/service/Impl/FileServiceImpl.java`
- Modify: `src/main/java/com/pei/zfile/file/service/Impl/NodeServiceImpl.java`
- Modify: `src/main/java/com/pei/zfile/share/service/Impl/ShareServiceImpl.java`

- [ ] `FileServiceImpl` 注入对象服务；预检命中调用 `retain` 后创建引用节点；普通上传调用 `createOrRetain`，不复制物理文件。
- [ ] 文件下载通过 `fileObjectId` 查询对象 storage key；节点详情通过对象查询 checksum。
- [ ] `NodeServiceImpl.deepCopyNode` 复用对象 ID并增加引用，移除物理复制和回滚物理文件列表。
- [ ] `ShareServiceImpl` 通过对象服务加载分享文件流，保留下载计数行为。
- [ ] 更新上传、复制、分享测试并运行定向测试。

### Task 5: 改造回收站和物理清理

**Files:**
- Modify: `src/main/java/com/pei/zfile/trash/service/Impl/TrashServiceImpl.java`
- Create: `src/main/java/com/pei/zfile/file/service/FileObjectMigrationCleanup.java`

- [ ] 永久删除和清空回收站按文件节点的 `fileObjectId` 聚合调用 `releaseReferences`，只对返回的归零对象注册 after-commit 物理删除。
- [ ] 移入回收站和恢复不改变对象引用。
- [ ] 启动清理组件只扫描 `fileObjectId` 非空的历史节点，删除不在规范对象集合中的旧 storage key；对象表为空时跳过。
- [ ] 增加回收站引用计数测试并运行完整后端测试。

### Task 6: 迁移与全量验证

**Files:**
- Modify: `frontend/cloudbox-openapi.yaml` only if API schema needs object-related fields.
- Modify: `docs/superpowers/specs/2026-08-20-file-object-reference-counting-design.md` if verification reveals a contradiction.

- [ ] 使用 Python YAML 解析验证 OpenAPI 未损坏。
- [ ] 运行 `mvn clean test`，确认所有测试通过。
- [ ] 在 `frontend` 和 `D:\vueproject\z-file-vue` 分别运行 `npm run build`。
- [ ] 运行 `git diff --check`，检查迁移 SQL 和工作区差异。
- [ ] 只暂存本功能文件，提交描述使用 `文件物理对象引用计数去重`，推送 `origin main`。
