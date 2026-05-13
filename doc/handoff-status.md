# Field Book 协作改造 — 交接文档

> 最后更新: 2026-05-13

## 一、当前进度

### brapi-light 后端 ✅ 基本完成

| 端点 | 状态 | 备注 |
|------|:---:|------|
| GET /serverinfo | ✅ | 含 serverDescription |
| GET /programs | ✅ | 只读 |
| GET /trials | ✅ | 支持 programDbId 过滤 |
| GET /studies | ✅ | 列表 + 详情 |
| GET /observationunits | ✅ | 含 position 含 observationLevel |
| GET /observationlevels | ✅ | 硬编码 plot/plant/subplot |
| GET /variables | ✅ | 含 trait/scale JSON |
| GET /observations | ✅ | 分页 |
| POST /observations | ⚠️ | 基础工作，正规 BrAPI trait 上传待验证 |
| PUT /observations | ✅ | rev 乐观锁 |
| GET /sync/changes | ✅ | since 增量过滤 |
| GET /people, /seasons, /locations | ✅ | 只读 |
| POST /images | ✅ | 元数据 |
| PUT /images/{id}/imagecontent | ✅ | 二进制 BLOB |
| GET /germplasm | ✅ | 硬编码 5 个品种 |
| OIDC (/.well-known, /token) | ✅ | 绕过认证 |

**测试**: 53 passed, 0 lint errors

### Android APK ✅ 可编译运行

| 功能 | 状态 | 备注 |
|------|:---:|------|
| APK 编译 | ✅ | Android Studio 中 Run 'app' |
| BrAPI 连接 | ✅ | 配好 OIDC 后可连上 |
| Study 导入 | ✅ | 从云端导入试验 |
| Trait 导入 | ✅ | 后端 trait null 已修复 (2026-05-13) |
| 手动同步 — Upload | ⚠️ | 正规 BrAPI trait 数据未验证 |
| 手动同步 — Download | ⚠️ | 未测试 |
| WorkManager 后台同步 | ⚠️ | 间隔 1 分钟，代码已集成 |
| SyncWorker | ⚠️ | 自动上传逻辑已完成 |
| 字段级冲突 UI | ⚠️ | 代码已实现，未联调验证 |
| SyncScheduler | ⚠️ | 开机自启，默认启用 |

---

## 二、当前卡点

### 问题 1: Trait 导入报 "Unknown error" ✅ 已修复

**现象**: 模拟器中 Import → BrAPI → Traits 报 `act_brapi_list_api_exception`

**根因**: `/brapi/v2/variables` 返回的 JSON 中有两个问题：
1. `trait` 字段为 null → `PhenotypingMapper.toTraitObject()` 访问 `trait.traitDescription` 时 NPE 崩溃
2. `scale.validValues` 使用 `min`/`max` 而非 BrAPI 模型期望的 `minimumValue`/`maximumValue`
3. `categories` 是字符串数组而非 `[{value: "..."}]` 对象数组

**修复** (2026-05-13):
1. 更新 brapi.db 三个变量的 trait 数据为 `{traitDbId, traitName, traitDescription}`
2. 修正 scale.validValues 字段名和 categories 格式
3. 在 `main.py` lifespan 中添加启动种子函数 `_seed_demo_data()`，自动创建带完整 trait/scale 的示例数据
4. 种子数据仅在 study 表为空时执行，已有数据不重复插入

### 问题 2: POST /observations 500 错误（正规 trait 数据未验证）

**现象**: 已修复本地 trait NULL FK 问题。正规 BrAPI trait 数据的上传统未测试。

### 问题 3: 上传后本地状态未更新

**现象**: 上传成功（200）但 app 解析响应后 `new=0, edited=0`，导致重复上传。

---

## 三、文件结构

### 本次新增/修改的核心文件

```
brapi-light/
├── brapi_light/
│   ├── main.py                    # FastAPI 入口 + 500 错误日志中间件
│   ├── config.py                  # 环境变量配置
│   ├── database/                  # 异步 SQLAlchemy + aiosqlite
│   ├── models/core.py             # Program, Trial, Study, Season, Location, Person
│   ├── models/phenotyping.py      # ObservationUnit, Variable, Observation(rev), Image
│   ├── schemas/                   # Pydantic v2 模型 + camelCase ↔ snake_case 转换
│   ├── routers/auth.py            # OIDC 绕过 (.well-known, /token)
│   ├── routers/server_info.py     # 服务发现
│   ├── routers/core.py            # 6 个只读核心端点
│   ├── routers/phenotyping.py     # 14 个端点 (观测 CRUD + levels + germplasm + sync/changes)
│   ├── services/core.py           # 通用分页查询
│   └── services/phenotyping.py    # 乐观锁 + 增量查询
├── tests/                         # 53 个测试
└── Dockerfile + .dockerignore

fieldbook-android/app/src/main/java/.../
├── activities/brapi/io/sync/
│   ├── BrapiSyncViewModel.kt      # +userCreatedTraitObservations 合并
│   ├── BrapiSyncUiState.kt        # +FieldDiff 字段级冲突
│   ├── BrapiSyncUi.kt             # +字段级冲突展示
│   ├── SyncWorker.kt              # CoroutineWorker 后台自动同步
│   ├── SyncScheduler.kt           # PeriodicWorkRequest (1min, NetworkConnected)
│   └── SyncNotifications.kt       # 通知渠道 + 同步完成通知
├── application/FieldBook.java     # +通知渠道创建 + 同步调度
└── preferences/PreferenceKeys.kt  # +BRAPI_SYNC_ENABLED 等 4 个 key

~/.gradle/init.gradle.kts          # 全局镜像脚本 (腾讯云)
gradle/wrapper/gradle-wrapper.properties  # distributionUrl → 腾讯云 -all
```

---

## 四、快速启动

```bash
# 1. 启动后端
cd D:\proj\Field-Book\brapi-light
uv run uvicorn brapi_light.main:app --host 0.0.0.0 --port 8000

# 2. 种子测试数据
sqlite3 brapi.db "INSERT INTO program VALUES ('p1','Wheat Breeding 2026',null);"
sqlite3 brapi.db "INSERT INTO trial VALUES ('t1','Drought Tolerance',null,'p1');"
sqlite3 brapi.db "INSERT INTO study (study_db_id,study_name,common_crop_name,location_name,active,program_db_id,trial_db_id) VALUES ('s1','Field A','Wheat','Test',1,'p1','t1');"
# Units, Variables 见前面步骤

# 3. 验证
curl http://localhost:8000/brapi/v2/serverinfo

# 4. 模拟器 App: Settings → BrAPI → http://10.0.2.2:8000 (V2) → Login
```

---

## 五、未来开发计划

| 优先级 | 任务 | 预估 |
|:---:|------|:---:|
| P0 | 修复 Trait 导入 (variables 响应格式) | 1-2h |
| P0 | 验证正规 BrAPI trait 上传/下载 | 1-2h |
| P1 | 修复上传后本地状态不更新 (响应格式 matching) | 2-3h |
| P1 | 验证 WorkManager 自动同步 | 1h |
| P2 | 字段级冲突 UI 联调 | 2-3h |
| P2 | 多模拟器并发测试 (2 台设备访问同一 server) | 2h |
| P3 | Docker 部署验证 | 1h |
| P3 | 测试清单逐项通过 (doc/field-test-checklist.md) | 3h |
