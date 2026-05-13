# Field Book 协作改造 — 产品需求文档

> 版本: v1.0 | 日期: 2026-05-13 | 状态: 草稿

## 0. 文档地图

本文档是需求基线。详细技术分析与进度追踪见以下文档，PRD 不做重复叙述。

| 主题 | 参考文档 | 关系 |
|------|----------|------|
| BreedBase 改造失败原因与 brapi-light 技术方案 | `doc/brapi-deploy-plan.md` | PRD 总结；详细分析见该文档 |
| 当前进度、卡点、文件结构 | `doc/handoff-status.md` | PRD 定验收标准；handoff 追踪进展 |
| 后端 API 验证命令 | `doc/verification-checklist.md` | PRD 定需求；checklist 定验证步骤 |
| 端到端手动测试用例 | `doc/field-test-checklist.md` | PRD 特性 → checklist 对应测试 |
| 网络环境与构建约束 | `doc/network-readiness-report.md` | NFR-06 引用 |
| BrAPI 协议陷阱与调试纪律 | `CLAUDE.md` | 实现时遵守的约束 |

## 1. 产品概述

### 1.1 一句话描述

让 Field Book Android 应用支持多人实时协作采集 — 多台设备数据自动汇聚到同一个中心后端，冲突可检测可解决。

### 1.2 问题陈述

Field Book 是成熟的开源 Android 田间表型采集应用（PhenoApps，11 年历史）。但它的 BrAPI 后端选型 — BreedBase — 是一个为完整育种机构设计的庞然大物（Perl/Catalyst + PostgreSQL/Chado + R + Slurm + webpack，2GB 镜像，启动 20 分钟）。

**我们最初尝试基于 BreedBase 改造来支持多人协作，但失败了。** 核心原因是 BreedBase 的 118 个 BrAPI 端点通过多层 Perl 继承链实现，增加乐观锁和增量同步需要对 BrAPI.pm 做侵入性修改，而每轮改动都被其复杂的依赖关系抵消。最终得出结论：在 BreedBase 架构上做协作改造不可行。

因此自建 brapi-light — 在一个极简 Python 基础上从头设计协作逻辑，不受 BreedBase 约束。

### 1.3 关键指标

| 维度 | 改造前 (BreedBase) | 改造后 (brapi-light) |
|------|-------------------|---------------------|
| 镜像大小 | 2 GB | 73 MB |
| 启动时间 | 10-20 分钟 | < 2 秒 |
| 实际实现的端点 | 118 个（Field Book 只用 ~12 个） | 16 个（仅 Field Book 需要的） |
| 多人协作 | 不支持 | 乐观锁 + 增量同步 + 字段级冲突 |
| 自动同步 | 无 | WorkManager 后台定期同步 |
| 部署复杂度 | 200+ 系统包 + npm build | 单容器，Docker Compose 一行启动 |

### 1.4 范围

**在范围内：**
- `brapi-light/` — BrAPI v2 后端，实现 Field Book 实际使用的 16 个端点
- `fieldbook-android/` 同步层改造 — WorkManager 自动同步、字段级冲突检测与解决、同步通知
- `docker/` — 单容器部署配置
- 种子数据初始化（空库首次启动自动创建演示数据）

**不在范围内：**
- 修改上游核心采集 UI（CollectActivity、FieldBookActivity 等）
- 浏览器管理后台（当前 admin/db 仅用于调试）
- 完整的 BrAPI v2 规范实现（仅实现最小可用子集）
- 生产级用户认证（OIDC 绕过仅适局域网）
- 上游 Field Book 功能（GNSS 导航、条码扫描等）

## 2. 用户角色

### 2.1 田间研究员

每天在田间用 Android 设备采集数据 4-6 小时。网络不稳定，需要离线采集、恢复网络后自动同步，不丢数据。

**痛点：** 上游版本需要记住手动点同步按钮，忘记点导致数据只存在一台设备上。

### 2.2 团队负责人

管理 3-5 人的采集团队。需要确保所有人的数据汇聚到统一数据源，发现和处理采集冲突。

**痛点：** 团队成员对同一地块的观测可能不一致（记录值不同、采集人等），需要知道谁改了哪个字段、选择保留谁的版本。

### 2.3 运维/管理员

在局域网服务器上部署后端。环境可能受限（Docker Hub 不可达）。

**痛点：** BreedBase 部署需要解决 200+ 包依赖、npm 构建、数据库初始化等大量问题。

## 3. 特性领域

每个特性含：优先级 (P0-P3)、Gherkin 验收条件、关联测试文件。

### 3.1 后端端点 (F-BE)

#### F-BE-01 只读核心端点 · P0

提供 programs、trials、studies、seasons、locations、people 的分页只读查询。

```gherkin
Scenario: 客户端获取程序列表
  Given 服务端已载入种子数据
  When 客户端请求 GET /brapi/v2/programs
  Then 响应包含名为 "Wheat Breeding 2026" 的程序
  And 分页元数据随响应返回
```

参考: `tests/test_programs.py`, `tests/test_trials.py`, `tests/test_studies.py`, `tests/test_seasons.py`

#### F-BE-02 观测变量（性状） · P0

返回含正确 `trait` 和 `scale` JSON 格式的变量。**这是 BrAPI 协议陷阱重灾区** — Field Book 内置的 brapi-java-client SDK 对字段命名有严格要求。

```gherkin
Scenario: 变量包含完整 trait 和 scale 数据
  Given 服务端存在变量 "Plant Height"
  When 客户端请求 GET /brapi/v2/variables
  Then trait 字段为 {traitDbId, traitName, traitDescription}（不可为 null）
  And scale.validValues 使用 minimumValue/maximumValue（非 min/max）
  And 分类变量的 categories 为 [{value: "..."}] 对象数组格式
  And observationVariableDbId 不被服务端改写
```

参考: `tests/test_variables.py`, `CLAUDE.md` BrAPI 协议陷阱表

#### F-BE-03 观测值 CRUD + 乐观锁 · P0

POST 创建、PUT 更新。每次写入递增 `rev` 字段（乐观锁），冲突时返回冲突状态不覆盖数据。

```gherkin
Scenario: 新建观测值 rev=1
  Given 存在 study、observationUnit、variable
  When POST /brapi/v2/observations 创建新观测值
  Then response 中 rev=1 且 lastSyncedTime 不为空

Scenario: 正确 rev 更新成功
  Given 观测值 o1 当前 rev=1, value="old"
  When PUT /brapi/v2/observations {"o1": {"value": "new", "rev": 1}}
  Then response 中 rev=2, value="new"

Scenario: 过期 rev 触发冲突
  Given 观测值 o1 当前 rev=2
  When PUT /brapi/v2/observations {"o1": {"value": "stale", "rev": 1}}
  Then metadata.status 中含 "conflicts"
  And result.data 为空列表
  And 服务端 value 未被修改
```

参考: `tests/test_observations.py`, `tests/test_sync.py`, `brapi_light/services/phenotyping.py`

#### F-BE-04 增量同步端点 · P0

`/brapi/v2/sync/changes?since=<ISO-timestamp>` 仅返回该时间戳之后修改的观测值。

```gherkin
Scenario: 同步仅返回新数据
  Given 两条观测值，修改时间分别为 2024-01-01 和 2024-06-15
  When GET /brapi/v2/sync/changes?since=2024-03-01T00:00:00Z
  Then 仅返回一条 value="new" 的观测值

Scenario: 无变更时返回空列表
  When GET /brapi/v2/sync/changes?since=2099-01-01T00:00:00Z
  Then result.data 为空列表
```

参考: `tests/test_sync.py`

#### F-BE-05 图片元数据与内容上传 · P2

```gherkin
Scenario: 上传图片元数据
  Given 存在 observationUnit
  When POST /brapi/v2/images 上传图片元数据
  Then 响应含 imageDbId

Scenario: 上传图片二进制内容
  Given 存在有效 imageDbId
  When PUT /brapi/v2/images/{imageDbId}/imagecontent
  Then 响应 200，内容持久化到数据库
```

参考: `tests/test_images.py`

### 3.2 同步引擎 (F-SYNC)

#### F-SYNC-01 手动同步（上传 + 下载） · P0

```gherkin
Scenario: 手动上传新观测值
  Given 本地有 3 条未上传的观测值
  When 用户点击 Sync → Upload
  Then 进度条逐步推进，显示 "3 uploaded"
  And 服务端 GET /observations 包含这 3 条

Scenario: 手动下载远程变更
  Given 服务端有 5 条本地没有的观测值
  When 用户点击 Sync → Download
  Then 新数据写入本地数据库，显示下载摘要
```

参考: `BrapiSyncViewModel.kt`, `field-test-checklist.md` 第 2 节

#### F-SYNC-02 上传后本地状态更新 · P1

**当前活跃卡点。** 上传成功（HTTP 200）但本地记录未更新 `observationDbId`/`rev`/`lastSyncedTime`，导致下次同步重复上传。

```gherkin
Scenario: 上传后同步状态更新
  Given 本地有 1 条新观测值（无 observationDbId）
  When 上传成功（response 含 observationDbId 和 rev）
  Then 本地记录获得 observationDbId
  And 本地记录获得 rev
  And 下次同步时该观测值不出现在"新"计数中
```

参考: `doc/handoff-status.md` 卡点 #3, `BrapiSyncViewModel.kt`

#### F-SYNC-03 WorkManager 后台自动同步 · P1

```gherkin
Scenario: 自动同步上传新数据
  Given 有 2 条未上传观测值，网络正常，BRAPI_SYNC_ENABLED=true
  When 经过一个同步周期（默认 1 分钟）
  Then 通知栏显示同步完成摘要

Scenario: 离线时不执行同步
  Given 设备处于飞行模式
  When 经过 2 个同步周期
  Then 无同步通知，服务端无新数据

Scenario: 网络恢复后自动同步
  Given 离线时采集了 1 条观测值
  When 网络恢复，经过一个同步周期
  Then 数据自动上传到服务端
```

参考: `SyncWorker.kt`, `SyncScheduler.kt`, `SyncNotifications.kt`

### 3.3 冲突检测与解决 (F-CONF)

#### F-CONF-01 字段级差异检测 · P2

下载时逐字段比较（value、collector、rep），构建 FieldDiff 对象列表。修改不同字段自动合并，修改相同字段标记冲突。

```gherkin
Scenario: 修改不同字段自动合并
  Given 用户 A 离线将 value 改为 "150"
  And 用户 B 离线将同条记录的 collector 改为 "Zhang San"
  When 用户 A 上传后，用户 B 下载
  Then 无冲突提示，最终记录为 value=150, collector=Zhang San

Scenario: 修改相同字段产生冲突
  Given 用户 A 上传 value="AAA"
  And 用户 B 本地 value="BBB"
  When 用户 B 下载
  Then 出现冲突提示，显示服务端值 vs 本地值
```

参考: `test_collaboration.py`, `BrapiSyncViewModel.kt`

#### F-CONF-02 冲突解决策略 UI · P2

用户可选择四种策略：保留本地、保留服务端、保留最新、逐一手动选择。

```gherkin
Scenario: 选择"保留服务端"解决所有冲突
  Given 下载产生 3 个冲突
  When 用户选择 "Server" 策略并确认
  Then 所有冲突值更新为服务端版本

Scenario: 手动逐条选择
  Given 下载产生 2 个冲突
  When 用户选 "Manual" → 冲突 1 选服务端，冲突 2 选本地 → 确认
  Then 冲突 1 用服务端值，冲突 2 用本地值
```

参考: `BrapiSyncUi.kt`, `BrapiSyncViewModel.kt`

### 3.4 部署与运维 (F-DEPLOY)

#### F-DEPLOY-01 种子数据初始化 · P0

首次启动自动创建演示数据，已有数据不重复插入。

```gherkin
Scenario: 空库首次启动创建种子数据
  Given 数据库为空
  When 服务启动（lifespan 事件）
  Then 至少包含 1 个 program、1 个 trial、1 个 study、3 个 variable
  And GET /brapi/v2/programs 返回非空列表

Scenario: 重启不重复创建
  Given 数据库已有数据
  When 服务重启
  Then 种子数据跳过，无重复条目
```

参考: `brapi_light/main.py` `_seed_demo_data()`

#### F-DEPLOY-02 Docker 构建 · P3

```gherkin
Scenario: Docker 容器启动即服务
  Given 镜像已构建
  When 容器启动
  Then GET /brapi/v2/serverinfo 返回 200，serverName="brapi-light"

Scenario: 重启后数据持久化
  Given 已通过 API 创建观测值
  When 容器停止再重启
  Then 之前创建的观测值仍可查询
```

参考: `docker/docker-compose.yml`, `verification-checklist.md` 第 1 节

### 3.5 多人协同场景 (F-COLLAB)

#### F-COLLAB-01 两人创建不同观测值 · P2

```gherkin
Scenario: 两个用户分地块采集
  Given study 含 Plot 1 和 Plot 2
  When 用户 A 在 Plot 1 上创建观测值 "150"
  And 用户 B 在 Plot 2 上创建观测值 "160"
  Then GET /observations 返回两条，value 分别为 "150" 和 "160"
```

参考: `tests/test_collaboration.py` `test_two_users_create_different_observations`

#### F-COLLAB-02 完整同步周期 · P2

```gherkin
Scenario: 完整协作流程
  Given 两个用户、一个 study、两个 variable
  When 用户 A POST 两条观测值
  And 用户 B POST 一条观测值
  And 用户 A PUT 编辑一条（rev 递增）
  And 用户 B PUT 同一记录使用过期的 rev
  Then 用户 B 收到冲突响应
  And 用户 B GET 获取到用户 A 的最新版本
  And 用户 B PUT 使用正确的 rev 更新成功
```

参考: `tests/test_collaboration.py` `test_full_sync_cycle`

#### F-COLLAB-03 三人协同采集 · P2

```gherkin
Scenario: 3 台设备协同采集同一研究
  Given 3 台设备连接同一 BrAPI 服务器，导入同一 study
  When 每台设备采集 10 个不同地块的观测值并上传
  And 最后一台设备下载同步
  Then 30 条观测值全部可见，无数据丢失
```

参考: `field-test-checklist.md` 第 6 节

## 4. 非功能性需求

| ID | 要求 | 指标 | 验证方式 |
|----|------|------|----------|
| NFR-01 | 后端启动时间 | < 2 秒 | `time docker run` |
| NFR-02 | Docker 镜像大小 | < 200 MB | `docker images` |
| NFR-03 | 并发设备 | ≥ 3 台同时读写 | `test_collaboration.py` + 手动集群测试 |
| NFR-04 | 离线操作 | 无网络时采集数据不丢失 | `field-test-checklist.md` 第 5 节 |
| NFR-05 | 自动同步延迟 | ≤ 可配置间隔（默认 1 分钟） | `SyncScheduler.kt` + 手动验证 |
| NFR-06 | 镜像构建可行性 | Docker Hub 不可达时可通过阿里云镜像加速器构建 | `doc/network-readiness-report.md` |
| NFR-07 | 数据库管理 | SQLite 单文件，零运维 | 设计保证 |

## 5. 发布标准

以下全部满足视为 v1.0 可发布：

1. **P0 验收条件 100% 通过** — F-BE-01 ~ F-BE-04, F-SYNC-01, F-DEPLOY-01
2. **测试基线** — 后端 53 测试全部通过，0 lint 错误
3. **验证清单通过** — `doc/verification-checklist.md` 第 1-2 节
4. **现场测试通过** — `doc/field-test-checklist.md` 第 1-6 节
5. **集群测试通过** — 3 台设备并发采集 (F-COLLAB-03)

## 6. 待解决问题

1. **上游合并策略** — PhenoApps/Field-Book 持续更新，本项目修改的 `BrapiSyncViewModel.kt` 等文件可能与上游冲突。需要制定 rebase 规程。
2. **生产级认证** — 当前 OIDC 绕过方案仅适合局域网。如需公网部署，需要对接真正的 OIDC provider。
3. **协作规模上限** — brapi-light 当前以 3-10 人团队为目标。超过此规模时 SQLite 写入锁可能成为瓶颈，需评估迁移至 PostgreSQL 的必要性。
