---
name: issue-completion-workflow
description: Issue 完成后的结项报告流程。USE THIS SKILL whenever an issue has been implemented and needs a completion report, or when the user says "完成这个issue", "关闭issue", "issue做完了", "写完成报告".
allowed-tools: Bash(gh:*) Bash(git:*) Bash(python:*) Read Write Edit
---

# Issue Completion Workflow

确保每个关闭的 Issue 都有结构化的完成报告：根因、修改内容、验证方式。

## When to use

- PR 合并后，关联的 Issue 需要关闭（或已自动关闭）
- 用户明确要求关闭 Issue 或写完成报告
- 实现任务完全完成（测试通过、代码已推送/合并）

## Steps

### 1. 确认代码已合并（或已推送）

```bash
gh pr view --json state,mergeCommit
```

如果通过 PR 合并且 PR body 含 `fixes #N`，GitHub 可能已自动关闭 Issue。**仍需写报告** — 见步骤 4。

### 2. 获取 Issue 上下文

```bash
gh issue view <ISSUE_NUMBER> --json number,title,body,url
```

### 3. 生成完成报告

```bash
python .claude/skills/issue-completion-workflow/scripts/generate_report.py --issue <ISSUE_NUMBER> --repo nwafufhy/Field-Book
```

脚本输出 markdown 报告到 stdout。保存到文件，检查，按需编辑。

### 4. 贴报告并关闭 Issue（如仍 open）

```bash
gh issue comment <ISSUE_NUMBER> --body-file report.md
```

如果 Issue 仍 open，关闭它：

```bash
gh issue close <ISSUE_NUMBER>
```

## Report Template

如手动写报告（而非用脚本生成），按此结构：

```markdown
## 完成报告

### 根因
- （问题发生的根本原因）

### 修复内容
- **文件/模块**: 具体改动说明
- 附关键行号或函数名

### 验证方式
- （如何验证修复有效：测试、curl、logcat 等）

### 留下的坑 / 已知问题
- （边界情况、临时方案、未覆盖的测试等）
```

## Pitfalls to avoid

- **不要默默关闭 Issue**。除非用户明确要求跳过，否则必须先贴报告再关闭。
- **不要猜测文件路径**。用 `git diff` 或 Read 验证后再写进报告。
- **报告用中文**，与 Issue 语言保持一致。
- **测试未通过不要标记完成**。
- **即使 PR 已通过 `fixes #N` 自动关闭 Issue，仍需贴报告**。自动关闭只链接 PR，完成报告补充了人类可读的上下文。
