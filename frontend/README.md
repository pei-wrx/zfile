# CloudBox 前端

> 基于 Vue 3 + TypeScript + Vite + Pinia + Element Plus 的私有文件管理与分享系统前端。

## 技术栈

- **Vue 3** + **TypeScript** — 渐进式框架 + 类型安全
- **Vite** — 构建工具，支持 HMR 和代码分割
- **Vue Router 4** — 路由管理与导航守卫
- **Pinia** — 状态管理
- **Element Plus** — UI 组件库（按需自动导入）
- **Axios** — HTTP 请求，封装 JWT 自动刷新

## 已完成功能

| 模块 | 功能 |
|------|------|
| 认证 | 登录、注册、JWT 自动刷新（并发保护）、退出登录 |
| 我的文件 | 目录树浏览、面包屑导航、新建目录、文件上传（拖拽/多选/进度条）、文件下载、预览（图片/PDF/文本） |
| 文件操作 | 重命名、移动（文件夹浏览器）、复制、批量删除、详情查看 |
| 回收站 | 查看、恢复、彻底删除、清空回收站 |
| 分享管理 | 创建分享（密码/有效期/下载次数）、复制分享链接、取消分享 |
| 公共分享 | 访问无密码/有密码分享、浏览分享内容、下载文件 |
| 个人中心 | 查看/修改资料、修改密码、存储用量仪表盘 |
| 管理员 | 用户列表、启用/禁用用户、调整存储配额 |

## 项目结构

```text
.
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
└── src
    ├── api
    │   ├── index.ts          # API 接口模块（对齐 OpenAPI 全部接口）
    │   └── request.ts        # Axios 封装 + JWT/ShareToken 管理 + 并发刷新保护
    ├── components
    │   ├── FileIcon.vue      # 文件图标组件（按类型智能着色）
    │   └── EmptyState.vue    # 空状态组件
    ├── layouts
    │   └── MainLayout.vue     # 侧边栏 + 顶部搜索栏 + 用户信息
    ├── router
    │   └── index.ts           # 路由配置与守卫（登录/管理员权限）
    ├── stores
    │   └── auth.ts            # 认证状态管理（Pinia）
    ├── styles
    │   └── global.css         # 全局样式与设计系统（颜色/阴影/圆角）
    ├── types
    │   └── index.ts           # TypeScript 类型定义（严格对齐 OpenAPI Schema）
    ├── utils
    │   └── format.ts          # 文件大小/时间/图标/状态格式化工具
    ├── views
    │   ├── auth
    │   │   ├── LoginView.vue
    │   │   └── RegisterView.vue
    │   ├── files
    │   │   └── FilesView.vue
    │   ├── share
    │   │   ├── MySharesView.vue
    │   │   └── PublicShareView.vue
    │   ├── trash
    │   │   └── TrashView.vue
    │   ├── admin
    │   │   └── AdminUsersView.vue
    │   ├── profile
    │   │   └── ProfileView.vue
    │   └── error
    │       └── NotFoundView.vue
    ├── App.vue
    ├── env.d.ts
    └── main.ts
```

## 运行方式

```bash
# 安装依赖
npm install

# 开发环境
npm run dev

# 生产构建
npm run build

# 预览构建产物
npm run preview
```

开发服务器默认端口为 **5173**，代理配置为 `http://localhost:8080`。可在 `vite.config.ts` 中修改后端地址。

## API 对接

接口严格对齐项目根目录中的 `cloudbox-openapi.yaml`，基础地址为 `/api/v1`。Vite 开发服务器已将 `/api` 代理到后端服务。

## 设计特色

- 现代化渐变色品牌视觉（登录页动态光斑背景）
- 卡片式布局、圆角、柔和阴影设计系统
- 响应式布局，适配不同屏幕
- 侧边栏可折叠，存储用量实时展示
- 文件图标按类型智能着色
- 路由切换过渡动画
