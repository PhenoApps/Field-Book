# brapi-light-phase1 Agent Gradle 网络问题分析

> 分析日期：2026-05-12  
> 会话 ID：`0914422c-d1f5-42e9-955a-6afdc200bb59`  
> 转录文件：`~/.claude/projects/D--proj-Field-Book/0914422c-...jsonl` (2.8MB, 1454 行)

---

## 结论摘要

Agent 遇到 Gradle 网络问题的**根因**是：**Android Gradle Plugin (AGP) 8.9.3 在内部硬编码了 `storage.googleapis.com/r8-releases` 作为 R8 编译器的下载源，该域名被 GFW 封锁，与 Docker Hub 被封锁是同一原因。**

这不是普通的 Maven 仓库问题——无法通过配置 `repositories` 或 `mavenLocal()` 解决。

---

## 故障时序

```
Agent 执行 gradle app:assembleDebug
  -> Gradle 开始解析 classpath 依赖
    -> AGP 初始化时硬编码添加 storage.googleapis.com/r8-releases 仓库
      -> 尝试下载 r8-8.11.18.jar
        -> TCP 连接超时 (~10分钟)
          -> BUILD FAILED
```

三次构建尝试全部相同结果，每次耗时 9~11 分钟后超时。

---

## 已尝试的修复方案（全部失败）

| # | 方案 | 失败原因 |
|---|------|----------|
| 1 | 手动放置 R8 jar 到 Gradle cache | AGP 忽略本地缓存，直接访问远程 |
| 2 | 创建本地 Maven 仓库 (`~/.m2/`) + `mavenLocal()` | AGP 内部仓库优先级高于 `mavenLocal()` |
| 3 | 文件仓库 + `init.gradle` 全局脚本 | `init.gradle` 钩子无法拦截 AGP 内部添加的仓库 |
| 4 | 直接添加 classpath 依赖项 | R8 由 AGP 内部管理，不走正常 classpath 解析 |
| 5 | `init.gradle` 中的 `afterEvaluate` 移除仓库 | 执行时机太晚——classpath 解析在此之前完成 |
| 6 | `init.gradle` 中的 `projectsLoaded` 重定向 URL | AGP 尚未添加仓库时该钩子已触发，添加仓库时该钩子已结束 |

**共同根因：** AGP 在插件初始化期间通过 `buildscript` 的 classpath 解析机制硬编码了 Google Cloud Storage URL，Gradle 的生命周期钩子无法在正确的时机拦截它。

---

## 关键发现

```
dl.google.com        ✅ 可达 (google() Maven 仓库)
repo1.maven.org      ✅ 可达 (mavenCentral())
storage.googleapis.com ❌ 不可达 (GFW 封锁)
```

- `com.android.tools:r8:8.11.18` **在 `dl.google.com/dl/android/maven2/` 上存在**且可达
- 但 AGP 将其自己的 `storage.googleapis.com/r8-releases/raw/` 仓库以更高优先级附加，导致 Gradle 首先尝试被封锁的 URL

---

## 最终部分解决方案

Agent 最后通过以下组合绕过了问题：

1. 在本地启动 Python HTTP 服务器（端口 9999）提供 r8 jar 文件
2. 修改 `%USERPROFILE%\.gradle\init.gradle` 将 `storage.googleapis.com` URL 重定向到 `localhost:9999`
3. R8 下载问题得到解决（L1420 确认）

但在 R8 问题解决后立即遇到了新问题：
- `local.properties` 缺少 Android SDK 路径 (`sdk.dir`)
- Windows 中文路径/文件名编码问题导致 `compileDebugJavaWithJavac` 失败

会话在这些新问题上结束了，构建未最终成功。

---

## 建议的正确解决方案

### 方案一：代理/VPN（推荐，一劳永逸）

为 `storage.googleapis.com` 配置可访问的网络环境，与 Docker Hub 问题一同解决。

### 方案二：Gradle init 脚本重定向到 google()

理论上可以将 AGP 的 R8 仓库重定向到 `dl.google.com`，但需要找到触发时机正确的 Gradle 钩子。可尝试：

1. Gradle `settingsEvaluated` 钩子
2. 在 `buildscript.configurations.classpath.resolutionStrategy` 中做组件规则替换
3. 使用 Gradle 的 `dependencySubstitution` 或 `artifactResolution` API

具体方案需要进一步试验，因为 AGP 对 R8 解析的封装非常深层。

### 方案三：Android Studio 内构建

如果安装了 Android Studio，它内置了 R8 并且会自动配置 `local.properties`。在有网络限制的环境下，Android Studio 的 SDK Manager 可以先下载好所有必要组件再做离线构建。

### 方案四：离线依赖缓存

在有网络的环境执行一次完整构建，然后将整个 `~/.gradle/caches/` 目录打包迁移到目标机器。
