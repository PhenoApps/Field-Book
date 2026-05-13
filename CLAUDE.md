@.claude/skills/andrej-karpathy-skills/CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## 仓库概述

Field Book 伞形仓库 — 多人作物表型协作采集系统。包含两个子项目：

| 子项目 | 目录 | 技术栈 | 说明 |
|--------|------|--------|------|
| **fieldbook-android** | `fieldbook-android/` | Kotlin/Java, Gradle, Android SDK | 田间表型数据采集 Android App |
| **brapi-light** | `brapi-light/` | Python 3.11+, FastAPI, SQLite | 轻量 BrAPI v2 后端服务 |

核心目标：实现多人实时协作采集。最初尝试基于 BreedBase 改造，发现其架构不支持协作场景，因此自建 brapi-light。

## 开发流程：PRD → Issue → 实现

**每次开始新任务前，先定位需求来源，不要从代码反推意图。**

```
1. 查 PRD（doc/prd.md）→ 找到对应的特性 ID（F-BE-01 等）和 Gherkin 验收条件
2. 查 GitHub Issues → 找到对应的 Issue 编号，确认没有人在做
3. 创建 worktree → git worktree add 隔离开发
4. 实现 → TDD 循环（RED → GREEN → REFACTOR）
5. 提交 → 使用特性 ID 标记（如 "fix(F-BE-03): xxx"）
6. PR → 引用 "Fixes #N"，合并后 Issue 自动关闭
```

### 需求追溯链

```
PRD 特性 ID (F-SYNC-02) ← 引用 → GitHub Issue (#10) ← 关联 → Commit/PR
                          ← 验收条件 → 测试用例 (test_sync.py)
```

### 当前 Milestone

`v1.0.0` — 7 个 open Issue (#9-#15)，发布标准见 `doc/prd.md` 第 5 章。

**不要直接凭感觉开始写代码。** 先回答这个问题："我在实现 PRD 的哪个特性？关联哪个 Issue？"

## 构建环境（重要）

### 优先级：Android Studio > 命令行

**直接用 Android Studio 打开 `fieldbook-android/` 目录构建**，不要用命令行 Gradle 折腾。AS 自动处理 SDK、依赖下载、Gradle wrapper。只有 AS 也失败时才考虑命令行。

### 国内网络镜像

已配置：
- `~/.gradle/init.gradle.kts` — 全局镜像脚本，将 google()/mavenCentral() 重定向到腾讯云
- `gradle/wrapper/gradle-wrapper.properties` — distributionUrl 使用腾讯云镜像 + `-all` 分发包（含源码，避免 IDE 额外下载 src.zip）

如果 Sync 还是慢，检查 `services.gradle.org` 是否被 `init.gradle.kts` 拦截；`-all` 分发包一次性解决了 wrapper + src 两个下载。

## 调试纪律：logcat 优先

**规则：先用工具获取真实数据，再凭推断写代码。**

### brapi-light 后端

```bash
# 前台启动，直接看请求和 traceback
uv run uvicorn brapi_light.main:app --host 0.0.0.0 --port 8000
```

### fieldbook-android 前端

```
adb logcat -d | grep -iE "BrAPI|500|error|Exception"
```

或使用 MCP 工具 `mcp__android-dev__log_logcat` / `mcp__android-emulator__get_logs`。

**禁止行为**：看到 500 错误后凭猜测修改服务端代码。必须先读到响应的 traceback 或请求体再做修改。

## BrAPI 协议陷阱

Field Book 内置了 `brapi-java-client` SDK，它对 JSON 响应格式有严格要求，比 REST 规范更挑剔。

| 陷阱 | 表现 | 对策 |
|------|------|------|
| `observationUnitPosition.observationLevel` 缺失 | NPE 崩溃 | 必须返回 `{observationLevel: {levelName, levelOrder}}` |
| `trait` 字段为 null | Trait 导入失败 | 必须返回 `{traitDbId, traitName}` |
| `season` 是嵌套对象不是字符串 | 500 / 解析失败 | POST 时提取 `seasonName` 或 `year` 转字符串 |
| `externalReferences` 传输 `fieldBookDbId` | 上传后客户端无法匹配响应 | 请求中的 externalReferences 必须原样返回 |
| `observationVariableDbId` 被服务端改写 | 客户端过滤响应时跳过该条记录 | 不要改变客户端发来的 ID 值 |
| 响应字段缺失（如 `serverDescription`） | 客户端 NPE 崩溃 | 查 BrAPI spec 确保所有必填字段存在 |

## TDD 适用范围

**TDD 在可控环境（brapi-light 后端）有效，在不可控环境（Android 构建/网络问题未解决时）是花架子。**

流程纪律：
1. 先保证目标平台能构建、能跑起来
2. 再开始写测试
3. 测试失败原因必须是"功能缺失"而不是"环境没通"

53 个 brapi-light 测试在模型变更时多次有效拦截了回归 bug。Android 端测试因构建环境不通未能执行。

## MCP 工具（Android 联调）

项目已配置 `android-emulator` 和 `android-dev` 两套 MCP 服务器，通过 `.claude/skills/android-harness/SKILL.md` 的 skill 调用。

关键工具：
- `get_all_text` / `get_clickable_elements` — 低成本看 UI 状态
- `logcat` — 读 app 运行日志
- `tap` / `press_key` — 操作模拟器
- `screenshot` — 仅在需要视觉确认时用（token 成本高）

## 测试纪律：BDD + TDD 双循环

### 外循环 — BDD 行为规格（动手前必做）

在编写任何实现代码之前：
1. 使用 Gherkin 的 Given-When-Then 格式，写出空测试文件（仅含行为注释和测试函数名）
2. Gherkin 注释写法参考项目中已有测试文件；brapi-light 尚无测试文件时，参考 `.claude/skills/test-driven-development/SKILL.md` 中的示例
3. 使用 AskUserQuestion 向用户确认行为规格是否正确
4. 用户确认后，才进入内循环实现

禁止在完成步骤 1-3 之前编写任何实现代码或测试实现代码。

### 内循环 — TDD 红-绿-重构

用户确认 BDD 规格后，严格按以下循环实现。详见 `.claude/skills/test-driven-development/SKILL.md`。

**铁律：没有先看到失败的测试，绝不写生产代码。**

```
RED → 写一个失败的测试，只测一个行为
  ↓   确认测试失败，且失败是因为功能缺失（不是语法错误）
GREEN → 写最少代码让测试通过
  ↓   确认测试通过，其他测试不受影响
REFACTOR → 清理代码，保持测试绿色
  ↓
下一个测试，重复直到 BDD 规格全部满足
```

### 禁止事项

- 禁止跳过 BDD 规格直接写实现代码
- 禁止在红灯亮前写生产代码 — 先写了就删掉重来
- 禁止跳过红灯验证
- 禁止 "太简单不用测"
- 禁止 "写完再补测试"

## 构建与开发命令

### fieldbook-android (Android App)

```bash
cd fieldbook-android
./gradlew app:assembleDebug          # Debug APK
./gradlew app:test                   # 单元测试 (Robolectric + JUnit)
./gradlew app:connectedAndroidTest   # 仪器测试 (需要设备)
./gradlew app:lint                   # Lint
```

### brapi-light (Python Backend)

```bash
cd brapi-light
uv sync --extra dev                   # 安装依赖（含测试）
uv run pytest                         # 运行测试
uv run ruff check .                   # Lint
uv run uvicorn brapi_light.main:app --host 0.0.0.0 --port 8000  # 开发服务器
```

### Docker

```bash
cd docker
docker compose up -d                  # 启动 brapi-light 服务 (端口 38000)
```

## 架构要点

### brapi-light 设计约束

- 目标替换 BreedBase 的 BrAPI 层，不是全功能育种数据库
- 实现了 16 个端点（含 observationlevels、germplasm、sync/changes、OIDC）
- SQLite 足够小团队（3-10 人）使用，无需 PostgreSQL
- 通过 Docker Compose 部署，端口 38000
- 数据模型参考 [BrAPI-FastAPI](https://github.com/agostof/BrAPI-FastAPI) Pydantic 桩
- 已实现：乐观锁 (rev)、增量同步 (/sync/changes)、OIDC 绕过

### fieldbook-android 改造方向

- WorkManager 定期自动同步（已集成，间隔 1 分钟，默认启用）
- 字段级冲突检测（代码已完成，待联调验证）
- 同步状态通知（已实现 SyncNotifications）

## 汉化 (i18n)

**现状**: 中文翻译覆盖率 20.3%（323/1594），1271 条待翻译。详见 [Issue #1](https://github.com/nwafufhy/Field-Book/issues/1)。

**汉化工作流** — 使用 `.claude/skills/translate-strings/SKILL.md` skill：

```bash
# 1. 提取待翻译字符串（按模块分组）
python .claude/skills/translate-strings/scripts/extract_untranslated.py --by-section

# 2. 翻译（遵循 glossary.md 术语表 + SKILL.md 翻译规则）

# 3. 校验占位符/XML 完整性
python .claude/skills/translate-strings/scripts/validate_xml.py
```

**参考资源**:
- Skill: `.claude/skills/translate-strings/SKILL.md` — 汉化工作流与翻译规则
- 术语表: `.claude/skills/translate-strings/glossary.md` — 农业/育种/UI 术语对照
- Issue #1: 模块优先级、术语约定、Crowdin 配置

**平台**:
- Crowdin 已配置 (`fieldbook-android/crowdin.yml`)
- Android Studio Translations Editor 可辅助编辑
- 参考高完成度语言: es-MX, it-IT, pt-BR, ru-RU (~1561 条)

## 当前状态

- **需求基线**: `doc/prd.md` — 5 个特性领域、13 个功能、Gherkin 验收条件
- **任务追踪**: [GitHub Issues](https://github.com/nwafufhy/Field-Book/issues) — v1.0.0 Milestone，7 个 open Issue (#9-#15)
- brapi-light: 53 测试通过，0 lint 错误，Docker 镜像 ~73MB
- Android: APK 可编译运行，BrAPI 连接成功，Study/Trait 导入成功
- 活跃卡点: #10 上传后本地状态不更新 (F-SYNC-02)

## 注意事项

- Android 项目根目录是 `fieldbook-android/`，Gradle 命令需要在此目录下执行
- brapi-light 使用 uv 作为包管理器和虚拟环境工具
- BreedBase 快照保存在 Docker 镜像中：`breedbase-local:final`
- 测试账号：`janedoe` / 环境详情见 `doc/brapi-deploy-plan.md`
- 国内网络需 `~/.gradle/init.gradle.kts` 镜像脚本，不要删除
- 调试 POST 500 错误时用前台模式启动 uvicorn，直接看 traceback
