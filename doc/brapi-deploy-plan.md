# Field Book BrAPI 云端部署方案

## 目标

为 Field Book 搭建 BrAPI v2 兼容的后端服务，实现**多人实时协作采集**：
- 多台 Android 设备通过局域网连接同一 BrAPI 服务器
- 采集的观测数据自动同步到中心数据库
- 多人同时采集时能检测和处理数据冲突

## 当前状态

| 阶段 | 状态 | 说明 |
|------|------|------|
| BreedBase Docker 部署 | ✅ 完成 | `http://192.168.50.155:38000` |
| Field Book 连接 BrAPI | ✅ 完成 | 可导入试验/性状 |
| 手动上传同步 | ✅ 可用 | 需手动点击 Upload |
| 手动下载同步 | ✅ 可用 | 需手动点击 Download |
| 自动实时同步 | ❌ 缺失 | Field Book 无此能力 |

## 遇到的主要问题

### 1. 仓库地址错误

Issue #8 中记录的是 `https://github.com/solgenomics/BreedBase.git`，但该仓库几乎为空（仅 README）。真正的 Docker 部署仓库是 `https://github.com/solgenomics/breedbase_dockerfile`。

### 2. 端口冲突

- 宿主机 8080 被 `iot4agripv-adminer-1` 容器占用
- 宿主机 5432 被 `iot4agripv-db-1`（postgres:18）占用
- **解决**：BreedBase 映射到 38000 端口，PostgreSQL 使用 Docker 内部网络不暴露宿主机端口

### 3. 启动极慢（npm install 卡 20+ 分钟）

每次容器重启，`/etc/init.d/sgn start` 会触发 `npm run build`（含 `npm install .` + webpack），耗时 10-20 分钟。
- **临时方案**：kill npm 进程，Starman 可以直接启动，BrAPI API 端点不需要前端 JS
- **永久方案**：用 `docker commit` 保存快照，下次从快照启动

### 4. pgcrypto 函数在错误 schema

`crypt()` 和 `gen_salt()` 被装在 `sgn` schema 而非 `public`，导致密码验证时找不到函数。
- **解决**：`ALTER DATABASE breedbase SET search_path TO public, sgn;`

### 5. 数据库未初始化（空库）

`POSTGRES_DB=breedbase` 让 PostgreSQL 镜像自动创建空库，entrypoint 检测到库已存在就跳过了 fixture 加载。
- **解决**：手动 `psql -f cxgn_fixture.sql` 加载测试数据，然后跑 `run_all_patches.pl`

### 6. BrAPI Token 认证格式

BreedBase 的 BrAPI token 端点不支持 JSON body（`[{"username":"..."}]` 格式），需用 OAuth2 表单格式 `grant_type=password&username=...&password=...`。
- 但 Field Book 使用的是 OIDC flow，不走 token 端点

### 7. OIDC 授权硬编码 Google OAuth

`/brapi/authenticate/oauth` 端点硬编码跳转到 Google OAuth：
```perl
$c->res->redirect("https://accounts.google.com/o/oauth2/auth?...");
```
局域网 IP 无法做 Google OAuth 回调。
- **解决**：在 Root.pm 添加 `/localauth/authorize` 端点，自动以 `janedoe` 登录并返回 token

### 8. search_path 配置不生效于已有连接

`ALTER DATABASE SET search_path` 只对新连接生效，Starman workers 需重启。

### 9. BrAPI 认证强制要求

默认所有 BrAPI 请求都要认证（返回 401）。
- **解决**：在 `sgn_local.conf` 添加：
```conf
brapi_require_login 0
brapi_default_user janedoe
```

### 10. 修改 BrAPI.pm 导致文件损坏

使用 sed 行号编辑修改 BrAPI.pm 时，插入位置偏移导致 Pod 文档被插入代码中间，文件损坏。
- **教训**：不要用 sed 编辑 Perl 源码，应使用完整的文件替换或独立控制器
- **恢复**：`docker create` + `docker cp` 从原始镜像提取干净文件

## BreedBase 臃肿分析

| 维度 | BreedBase | Field Book 实际需要 |
|------|-----------|-------------------|
| 镜像大小 | 2GB+ | ~200MB |
| 启动时间 | 10-20 分钟 | <2 秒 |
| BrAPI 端点 | 118 个 | ~12 个 |
| 技术栈 | Perl/Catalyst + R + Slurm + BLAST | 任意轻量框架 |
| 数据库 | PostgreSQL + Chado schema | SQLite 足够小团队 |
| 前端 | Mason + jQuery + Bootstrap + webpack | 不需要 |
| 依赖 | 200+ 系统包 + 1000+ npm 包 | ~10 个 Python 包 |

核心问题：**BreedBase 是为完整育种机构设计的，不是为田间采集后端设计的。**

## 计画方案：brAPI-light

### 架构

```
Field Book (Android) ←→ BrAPI v2 REST API ←→ brAPI-light (FastAPI + SQLite)
                           192.168.50.155:38000
```

### 技术选型

| 组件 | 选择 | 理由 |
|------|------|------|
| Web 框架 | Python FastAPI | 异步、自动 OpenAPI 文档、Pydantic 验证 |
| 数据库 | SQLite | 零配置、单文件、Docker 友好、够小团队用 |
| 数据模型 | 基于 BrAPI-FastAPI 的 Pydantic 桩 | 已有现成的 BrAPI v2 数据模型 |
| 容器化 | Docker Compose | 与现有环境一致 |

### 需要实现的 BrAPI 端点（最小集）

```
GET  /brapi/v2/serverinfo          — 服务发现
GET  /brapi/v2/programs            — 项目列表
GET  /brapi/v2/trials              — 试验列表
GET  /brapi/v2/studies             — 研究导入
GET  /brapi/v2/observationunits    — 观测单元导入
GET  /brapi/v2/variables           — 性状变量
GET  /brapi/v2/observations        — 下载观测数据
POST /brapi/v2/observations        — 上传新观测数据
PUT  /brapi/v2/observations        — 更新已编辑观测数据
GET  /brapi/v2/people              — 人员信息
GET  /brapi/v2/seasons             — 季节
GET  /brapi/v2/locations           — 位置
POST /brapi/v2/images              — 上传图片元数据
PUT  /brapi/v2/images/{id}/imagecontent — 上传图片内容
```

### Field Book 改造方向

1. **WorkManager 定期自动同步**（最重要）：每 5 分钟自动触发 upload + download
2. **同步状态通知**：通知栏显示最后一次同步时间和冲突数
3. **增量冲突检测**：上传前检查服务器端是否已被他人修改

### Docker Compose 目标

```yaml
services:
  brapi-light:
    build: .
    ports:
      - "38000:8000"
    volumes:
      - ./data:/data
    environment:
      - DATABASE_URL=sqlite:///data/brapi.db
```

## 快照管理

```bash
# 当前可用的 BreedBase 快照
breedbase-local:snapshot     # 初版
breedbase-local:snapshot-v2  # search_path 修复
breedbase-local:final        # 完整可用（含 localauth + 无认证）

# 恢复方式
docker stop breedbase_web && docker rm breedbase_web
docker run -d --name breedbase_web \
  --network breedbase_dockerfile_default \
  -p 38000:8080 breedbase-local:final
```

## 当前可用环境

| 服务 | 地址 | 说明 |
|------|------|------|
| BreedBase Web | `http://192.168.50.155:38000` | BreedBase 完整界面 |
| BrAPI serverinfo | `http://192.168.50.155:38000/brapi/v2/serverinfo` | 无需认证 |
| OIDC 授权 | `http://192.168.50.155:38000/localauth/authorize` | 自动登录 |
| 测试账号 | `janedoe` / `admin123` | |

## 参考资料

- [BrAPI-FastAPI](https://github.com/agostof/BrAPI-FastAPI) — Python BrAPI v2 桩代码
- [breedbase_dockerfile](https://github.com/solgenomics/breedbase_dockerfile) — BreedBase Docker 部署
- [Field Book GitHub](https://github.com/PhenoApps/Field-Book) — Field Book 源码
- [BrAPI v2 规范](https://brapi.org) — BrAPI API 文档
