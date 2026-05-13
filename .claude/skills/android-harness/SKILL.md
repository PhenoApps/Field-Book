---
name: android-harness
description: Field Book Android 项目的 MCP 工具使用指南。当需要读取 logcat 日志、操作模拟器、截图、UI 交互、安装 APK 或调试崩溃时使用。涵盖 android-emulator (40 工具) 和 android-dev MCP 服务器。
---

# Field Book Android Harness — MCP 工具使用指南

## 可用工具速查

本项目的 `.mcp.json` 配置了两套 MCP 服务器，所有工具以 `mcp__<server>__<tool>` 形式调用。

### android-emulator（主力，40 工具）

| 类别 | 工具 | 用途 |
|------|------|------|
| 设备 | `device_info` | 获取设备型号、分辨率 |
| 应用 | `list_packages`, `launch_app`, `install_apk`, `clear_app_data`, `force_stop` | 包管理 |
| 日志 | `get_logs` | 获取设备日志（支持 filter/level/lines） |
| 截图 | `screenshot` | 全屏截图（返回 base64 PNG） |
| UI | `get_current_activity`, `get_all_text`, `get_ui_tree`, `get_clickable_elements` | 界面结构 |
| 操作 | `tap`, `tap_text`, `tap_element`, `swipe`, `scroll`, `type_text`, `set_text`, `press_key` | UI 交互 |
| 状态 | `is_keyboard_visible`, `get_focused_element`, `get_screen_size` | 设备状态 |

### android-dev（补充 — 日志和深度分析）

| 类别 | 工具 | 用途 |
|------|------|------|
| 日志 | `log_logcat` | **主要日志工具**，支持 `app_package`/`log_level`/`lines` 精确过滤 |
| 崩溃 | `log_crash_dump`, `log_crash_dump_for_app` | 获取 crash buffer 中的崩溃堆栈 |
| 诊断 | `bugreport_capture`, `log_anr_traces` | bugreport 和 ANR 深度分析 |
| 设备 | `device_list`, `device_connect`, `device_disconnect` | 设备连接管理 |
| 录屏 | `screen_record_start`, `screen_record_stop` | 录制操作过程 |
| 安装 | `app_install`, `app_launch` | 安装 APK 并启动应用 |

## 信息收集原则：低成本优先

**核心规则：能用文本就不用 UI 树，能用 UI 树就不用截图。**

| 层级 | 方法 | Token 成本 | 何时使用 |
|------|------|-----------|---------|
| T1 | `log_logcat` / `get_all_text` | 极低 | 日志分析、崩溃排查、界面文本验证 |
| T2 | `get_clickable_elements` / `get_ui_tree` | 低 | 控件定位、UI 结构分析 |
| T3 | `screenshot` | 高 | 视觉确认、布局验证（最后手段） |

示例——查找同步按钮：
1. T1 `get_all_text` → 看到 "Sync" "SYNC" "同步" → 知道按钮存在
2. T2 `get_clickable_elements` → 找到坐标 (540, 2100)
3. T3 `screenshot` → 只在需要视觉确认时才用

## Field Book App 结构

### 包名

```
com.fieldbook.tracker.debug    # 调试版
com.fieldbook.tracker          # 正式版
```

### 关键 Activity

| Activity | 文件名 | 功能 |
|----------|--------|------|
| `CollectActivity` | `activities/CollectActivity.java` | 主采集界面 |
| `TraitActivity` | `activities/brapi/BrapiTraitActivity.java` | 性状管理 |
| `BrapiActivity` | `activities/brapi/BrapiActivity.java` | BrAPI 配置 |
| `ConfigActivity` | `activities/ConfigActivity.java` | 设置 |
| `DataGridActivity` | `activities/DataGridActivity.kt` | 数据表格 |
| `FieldEditorActivity` | `activities/FieldEditorActivity.kt` | 田地编辑 |
| `BrapiStudyImportActivity` | `activities/brapi/io/BrapiStudyImportActivity.kt` | 从 BrAPI 导入试验 |
| `BrapiAuthActivity` | `activities/brapi/BrapiAuthActivity.java` | BrAPI 认证 |

### 项目构建

```bash
cd D:/proj/Field-Book/fieldbook-android
./gradlew app:assembleDebug          # 编译 Debug APK
./gradlew app:test                   # 单元测试 (Robolectric)
./gradlew app:connectedAndroidTest   # 仪器测试（需设备）
./gradlew app:lint                   # Lint
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`

## 日志获取（logcat）方法速查

**首选 `android-dev__log_logcat`**，支持 `app_package` 直接按包名过滤，无需手动 grep。

| 工具 | 适用场景 | 调用示例 |
|------|---------|---------|
| `mcp__android-dev__log_logcat` | **日常日志**，可按包名/级别/行数精确过滤 | `log_logcat(app_package="com.fieldbook.tracker.debug", log_level="ERROR", lines=100)` |
| `mcp__android-emulator__get_logs` | 按关键词/级别过滤，不支持包名过滤 | `get_logs(filter="BrAPI", level="E", lines=50)` |
| `mcp__android-dev__log_crash_dump` | 获取 crash buffer 中的所有崩溃堆栈 | 无参数，返回 FATAL EXCEPTION |
| `mcp__android-dev__log_crash_dump_for_app` | 按包名过滤崩溃堆栈 | `log_crash_dump_for_app(app_package="com.fieldbook.tracker.debug")` |
| `mcp__android-dev__log_anr_traces` | ANR 线程堆栈分析 | 拉取 `/data/anr/` 下的 trace 文件 |

**关键参数说明（`log_logcat`）：**
- `app_package`: 直接传包名字符串，如 `"com.fieldbook.tracker.debug"`，工具自动按 PID 过滤
- `log_level`: `"ERROR"` | `"WARNING"` | `"INFO"` | `"DEBUG"` | `"VERBOSE"`
- `lines`: 返回最近 N 行，默认 200，最大 5000
- **包名不存在会返回空**：先用 `list_packages` 确认实际包名再查日志

## 常用工作流

### 工作流 1：崩溃排查

```
1. mcp__android-dev__log_logcat(app_package="com.fieldbook.tracker.debug", log_level="ERROR")
2. 如果无结果 → 可能是 crash buffer，改用 log_crash_dump_for_app(app_package="com.fieldbook.tracker.debug")
3. 如果 crash buffer 无结果 → 可能是 ANR，改用 log_anr_traces()
4. 根据堆栈定位到具体源码文件 → Read 工具查看 → Edit 修复
5. 重新构建后再次 log_logcat 验证
```

### 工作流 2：构建→安装→测试闭环

```
1. cd D:/proj/Field-Book/fieldbook-android && ./gradlew app:assembleDebug
2. mcp__android-emulator__install_apk(path="app/build/outputs/apk/debug/app-debug.apk")
3. mcp__android-emulator__launch_app(package="com.fieldbook.tracker.debug")
4. mcp__android-emulator__get_current_activity()  ← 确认 App 已启动
5. 执行 UI 操作序列（见工作流 3）
6. mcp__android-dev__log_logcat(app_package="com.fieldbook.tracker.debug")  ← 验证无异常
```

### 工作流 3：UI 自动化操作

```
1. screenshot()                              ← 看清当前界面
2. get_clickable_elements()                  ← 找到目标控件坐标
3. tap(x, y)                                 ← 点击
   或 swipe(x1, y1, x2, y2)                  ← 滑动列表
   或 type_text(text="...")                  ← 输入文本 (不清空)
   或 set_text(text="...")                   ← 清空后输入
4. get_current_activity() / get_all_text()   ← 确认导航到预期界面
```

### 工作流 4：同步功能测试（Field Book 核心需求）

Field Book 的核心改造方向是 WorkManager 定期自动同步。测试步骤：

```
1. launch_app("com.fieldbook.tracker.debug")
2. 导航到 BrAPI 设置: tap_text("设置") or 通过 get_clickable_elements 定位
3. get_all_text() ← 检查当前 BrAPI URL 和状态
4. 触发同步：通过 UI 操作或启动 WorkManager 任务
5. mcp__android-dev__log_logcat(app_package="com.fieldbook.tracker.debug") 
   ← 验证同步流程日志，关注 "WorkManager|Sync|BrAPI" 关键字
6. get_all_text() ← 验证同步后的界面变化
```

## 注意事项

1. **模拟器必须先启动**：MCP 工具依赖 adb 连接，确保 `adb devices` 能看到设备
2. **首次 npx 启动慢**：MCP 服务首次启动需下载 npm 包 (~20-30 秒)，后续有缓存
3. **screenshot 返回 base64**：截图会作为 base64 图片返回，Claude 可直接"看"图
4. **TraitActivity 是当前测试界面**：性状查看/编辑是 BrAPI 同步的核心数据
5. **调试版包名后缀 .debug**：确保使用 `com.fieldbook.tracker.debug` 而非正式版包名

## 两套 MCP 的配合

| 场景 | 首选 | 备选 |
|------|------|------|
| 日常日志 | `android-dev: log_logcat(app_package=...)` ← 支持包名过滤 | `android-emulator: get_logs(filter=...)` ← 仅关键词过滤 |
| 崩溃分析 | `android-dev: log_crash_dump_for_app(app_package=...)` | `android-dev: log_crash_dump` (全设备) |
| ANR 分析 | `android-dev: log_anr_traces` | — |
| 设备信息 | `android-dev: device_list` | `android-emulator: device_info`（详细信息） |
| 录屏 | `android-dev: screen_record_start/stop` | — |
| UI 交互 | `android-emulator: tap/swipe/type_text` | — |
| 应用管理 | `android-emulator: launch_app/install_apk` | `android-dev: app_install/app_launch` |

## 相关资源

- 完整 MCP 方案对比：`D:\AI_harness\skills\android-dev-tools\SKILL.md`
- 项目 CLAUDE.md：`D:\proj\Field-Book\CLAUDE.md`
- Android 构建 Hook 参考：`D:\AI_harness\skills\han-bdd\plugins\specialized\android\`
