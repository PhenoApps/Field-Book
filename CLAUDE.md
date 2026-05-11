@.claude/skills/andrej-karpathy-skills/CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## 仓库概述

Field Book 伞形仓库 — 多人作物表型协作采集系统。包含两个子项目：

| 子项目 | 目录 | 技术栈 | 说明 |
|--------|------|--------|------|
| **fieldbook-android** | `fieldbook-android/` | Kotlin/Java, Gradle, Android SDK | 田间表型数据采集 Android App |
| **brapi-light** | `brapi-light/` | Python 3.11+, FastAPI, SQLite | 轻量 BrAPI v2 后端服务 |

目标：用 brapi-light 替换笨重的 BreedBase（2GB 镜像 / 20 分钟启动 → 200MB / 2 秒），实现多人实时协作采集。

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
uv sync                              # 安装依赖
uv run pytest                        # 运行测试
uv run ruff check .                  # Lint
uv run uvicorn brapi_light.main:app --reload  # 启动开发服务器
```

### Docker

```bash
cd docker
docker compose up -d                 # 启动 brapi-light 服务
```

## 架构要点

### brapi-light 设计约束

- 目标替换 BreedBase 的 BrAPI 层，不是全功能育种数据库
- 只需实现 13 个 BrAPI 端点（见 `doc/brapi-deploy-plan.md`）
- SQLite 足够小团队（3-10 人）使用，无需 PostgreSQL
- 通过 Docker Compose 部署，端口 38000
- 数据模型参考 [BrAPI-FastAPI](https://github.com/agostof/BrAPI-FastAPI) Pydantic 桩

### fieldbook-android 改造方向

- WorkManager 定期自动同步（核心需求）
- 增量冲突检测
- 同步状态通知

## 注意事项

- Android 项目根目录是 `fieldbook-android/`，Gradle 命令需要在此目录下执行
- brapi-light 使用 uv 作为包管理器和虚拟环境工具
- BreedBase 快照保存在 Docker 镜像中：`breedbase-local:final`
- 测试账号：`janedoe` / 环境详情见 `doc/brapi-deploy-plan.md`
