# Z-File

Z-File 是一个前后端分离的文件管理与分享系统，仓库采用单仓库结构：

```text
z-file/
├── src/          # Spring Boot 后端源码
├── frontend/     # Vue 3 + TypeScript + Vite 前端
├── storage/      # 本地文件存储目录（运行时生成，不提交文件）
└── pom.xml
```

## 环境要求

- Java 21+
- Maven（或项目内的 Maven Wrapper）
- Node.js 18+
- npm 9+

## 启动后端
启动前需先配置本地运行环境

在仓库根目录执行：

```bash
mvn spring-boot:run
```

后端默认监听 `http://localhost:8090`。

## 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认监听 `http://localhost:5173`。开发服务器会将 `/api` 请求代理到后端 `http://localhost:8090`，代理目标可在 `frontend/vite.config.ts` 中调整。

## 构建验证

```bash
cd frontend
npm run build
```

```bash
mvn test
```

前端依赖锁定在 `frontend/package-lock.json`
