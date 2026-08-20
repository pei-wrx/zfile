# 已存在文件秒传功能实施计划

**目标：** 前端先计算文件 SHA-256，后端预检命中时直接复用已有存储对象创建节点，避免上传文件；未命中时保留现有 multipart 上传流程。

**架构：** 新增 `POST /files/upload/check` 预检接口。预检请求携带父目录、文件名、大小、SHA-256 和冲突策略；命中时在后端事务内完成存储对象复制、文件节点插入和用户用量更新，并返回创建的节点。未命中返回 `instantUploaded=false`，前端随后调用现有上传接口。普通上传会校验可选的客户端 SHA-256 与后端实际计算值一致，客户端哈希不作为内容可信依据。

**技术栈：** Spring Boot、MyBatis-Plus、Spring Validation、Vue 3、TypeScript、Web Crypto API、JUnit 5、Maven。

## 影响文件

- 后端新增上传预检请求/响应 DTO，并扩展上传请求的可选 SHA-256 字段。
- 后端扩展 `FileService`、`FileServiceImpl` 和 `FileController`；复用现有存储复制、名称冲突、配额和事务模式。
- 前端新增 Web Crypto SHA-256 工具，扩展上传 API 类型和请求，调整上传循环先预检再决定是否发送文件。
- 补充后端服务测试和 OpenAPI 文档；前端使用现有 `npm run build` 校验 TypeScript。

## 验证

- 预检命中：不调用 `storeTemp`，创建节点并增加用户用量。
- 预检未命中：返回未命中，不创建节点、不复制存储对象。
- 普通上传的客户端哈希错误：拒绝并清理临时文件。
- 后端 `mvn test`、前端原目录与仓库副本 `npm run build`、OpenAPI YAML 解析、`git diff --check`。
