---
name: translate-strings
description: Use when translating Field Book Android strings.xml resources to Chinese, filling missing translations (99% coverage, 8 remaining non-translatable items), or maintaining i18n term consistency across Field Book modules. Triggers on requests to "翻译strings", "汉化", "补全翻译", or work on Issue #1.
---

# Android Strings 汉化工作流

## 概述

将 Field Book Android 的 `strings.xml` 从英文翻译为中文。已完成两轮翻译，覆盖率 99%（1561/1569），8 条剩余项为 string-array/plurals/空字符串/translatable=false。

**核心原则：术语一致 > 格式保全 > 表达自然**

参考语言：pt-BR（97% 完成度）可在不确定原文语境时提供对照。

## 工作流

### 第 1 步：确定翻译范围

运行提取脚本查看待翻译字符串：

```bash
python .claude/skills/translate-strings/scripts/extract_untranslated.py --by-section
```

选项：
- `--section <name>` — 只提取某个模块（如 `BRAPI`、`ABOUT`）
- `--priority` — 按 Issue #1 优先级排序输出
- `--missing-only` — 仅输出未翻译的名柄（默认）

### 第 2 步：查阅术语表

翻译前必须阅读 `glossary.md` 确认术语译法。高频术语（trait → 性状、plot → 小区、entry → 条目）不可随意更改。

### 第 3 步：逐条翻译

将未翻译的 `<string>` 翻译后追加到 `values-zh-rCN/strings.xml`：

- 保持 XML 注释（`<!-- SECTION_NAME -->`）与英文源一致
- 保持 string name 的顺序与英文源一致
- 已存在的中文翻译不动
- 参考 pt-BR（`values-pt-rBR/strings.xml`）理解英文原文语境

### 第 4 步：校验

```bash
python .claude/skills/translate-strings/scripts/validate_xml.py
```

确认：XML 结构完整、无重复 name、格式化占位符数量匹配。

### 第 5 步：提交

使用规范化 commit：
```
chore(i18n): 补全<模块名>中文翻译 (#1)
```

## 翻译规则（HARD GATE）

<HARD-GATE>
- **占位符保护** — `%1$s`、`%2$s`、`%s`、`%d` 必须原样保留，不可修改或删除
- **转义字符保护** — `\'`、`\n`、`\n\n` 等不可删除或修改
- **HTML 标签保护** — `<b>`、`</b>`、`<i>`、`</i>`、`<br/>` 等不可删除
- **CDATA 段保护** — `<![CDATA[...]]>` 内的 XML 结构不可破坏
- **plurals 结构保护** — `<plurals>` 下的 `<item quantity="one|other|...">` 数量必须与英文源一致
- **BrAPI 术语不译** — BrAPI 作为协议名称保留不翻译
- **术语强制一致** — 同一英文词在所有 string 中必须译为同一中文
</HARD-GATE>

## 高频术语速查

| English | 中文 | 说明 |
|---------|------|------|
| trait | 性状 | 可观测/测量的表型特征 |
| plot | 小区 | 田间的实验单元 |
| observation unit | 观测单元 | 数据采集的最小单位 |
| entry | 条目 | 种质在试验中的编号 |
| rep | 重复 | 同一试验的重复块 |
| study / field | 试验 / 田块 | 根据语境选择 |
| germplasm | 种质 | 遗传资源 |
| observation level | 观测层级 | 如 plot、plant |
| format | 格式 | 性状的数据类型格式 |
| BrAPI | BrAPI | 保留不译 |
| sync | 同步 | 数据同步 |
| OIDC | OIDC | 保留不译 |
| GNSS | GNSS | 保留不译（全球导航卫星系统） |
| TTS | TTS | 保留不译（文字转语音） |
| Wear OS | Wear OS | 保留不译 |

完整术语表见 `glossary.md`。

## 常见错误

| 错误 | 正确 |
|------|------|
| %1$s → %1$s 被漏掉 | %1$s 必须原样保留 |
| `\'` 被翻译为 `'` | `\'` 是 XML 转义，保留 |
| `\n\n` 被删除 | `\n` 是换行符，保留 |
| trait → 特征 | trait → 性状 |
| field → 字段（采集语境） | field → 地块 / 田块 |
| plot → 绘图 | plot → 小区 |

## 检查清单

提交翻译前逐项确认：

- [ ] 占位符 `%s`/`%d`/`%1$s` 等全部原样保留
- [ ] `\'`、`\n`、`\n\n` 未删除或修改
- [ ] `<b>`/`</b>`/`<i>`/`</i>` 等 HTML 标签完整
- [ ] 术语与 glossary.md 一致
- [ ] 未覆盖已有中文翻译
- [ ] XML 结构完整（开闭标签匹配）
- [ ] `<plurals>` 的 item 数量与英文源匹配
- [ ] `translatable="false"` 的 string 未翻译

## 大规模翻译最佳实践（经验总结）

### 分批策略

按 Issue #1 优先级分 4 批并行，每批使用 `general-purpose` + `haiku` 模型 subagent：

| 批次 | 模型 | 适用范围 |
|------|------|----------|
| P1-P2 | haiku | 高频模块 (BrAPI, Collect) — 模式化字符串 |
| P3-P4 | haiku | 通用 UI, Preferences — 量大但术语固定 |

**关键经验**：

1. **subagent 无法写文件** — haiku 模型的 subagent 没有 Write/Bash 写权限。解决办法：让 agent 在回复中直接输出 XML，主 session 负责写文件和合并。

2. **`--section` 与 `--csv` 不兼容** — `extract_untranslated.py --csv` 会忽略 `--section` 参数（代码第 171-178 行提前 return）。必须使用 `--by-section` 模式才能按 section 筛选。

3. **Worktree 路径陷阱** — git worktree 从上游 main 创建后不含本地翻译历史。合并脚本应使用**相对路径**或在主仓库中操作。

4. **`%ss` 误报警告** — 英文 `Location updates is %ss` 中的 `%ss` 是 `%s` 占位符 + 字面 `s`（表示秒），validator 误报为缺失占位符。忽略即可。

5. **string-array / plurals 需特殊处理** — `community_servers_*` 是 string-array，`years_ago`/`fields_delete_confirmation` 是 plurals，不能作为普通 `<string>` 合并。建议最后手动处理或使用专门脚本。

6. **Agent 输出 HTML 转义** — Agent 在 markdown 代码块中输出 `&lt;` `&gt;`，写入真实 XML 文件时需用实际 `<` `>` 字符。

7. **dedup 合并策略** — 4 个 agent 并行输出到独立 `batchN_*.xml`，然后用 `merge.py` 统一合并到 `values-zh-rCN/strings.xml`，自动跳过已存在的 name。

### 并行翻译流水线

```bash
# 1. 创建临时目录
mkdir -p temp_i18n

# 2. 并行启动 4 个 subagent（各自输出到独立 temp 文件）
# 3. 主 session 收集翻译，写入 batchN_*.xml
# 4. 合并（自动去重）
python temp_i18n/merge.py

# 5. 校验
python .claude/skills/translate-strings/scripts/validate_xml.py

# 6. 清理并提交
rm -rf temp_i18n
git add fieldbook-android/app/src/main/res/values-zh-rCN/strings.xml
git commit -m "chore(i18n): 补全XX条中文翻译 (#1)"
```
