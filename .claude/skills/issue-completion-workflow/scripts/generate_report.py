#!/usr/bin/env python3
"""生成 Field Book Issue 完成报告，基于 git 历史和变更文件。"""

import argparse
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def run(cmd: list[str]) -> str:
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")
    if result.returncode != 0:
        raise RuntimeError(f"Error running {' '.join(cmd)}:\n{result.stderr}")
    return result.stdout.strip()


def get_issue_title(issue: str, repo: str | None) -> str:
    args = ["gh", "issue", "view", issue, "--json", "title", "-q", ".title"]
    if repo:
        args.extend(["--repo", repo])
    return run(args)


def get_commits_since(base: str = "origin/main") -> list[str]:
    out = run(["git", "log", f"{base}..HEAD", "--oneline", "--no-merges"])
    if not out:
        return []
    return out.splitlines()


def get_changed_files(base: str = "origin/main") -> list[str]:
    out = run(["git", "diff", f"{base}..HEAD", "--name-only"])
    if not out:
        return []
    return out.splitlines()


def main() -> None:
    parser = argparse.ArgumentParser(description="生成 Issue 完成报告")
    parser.add_argument("--issue", required=True, help="Issue number (e.g. 10)")
    parser.add_argument("--repo", default="nwafufhy/Field-Book", help="Owner/repo")
    parser.add_argument("--base", default="origin/main", help="比较基准")
    parser.add_argument("--output", default="report.md", help="输出文件路径")
    args = parser.parse_args()

    try:
        title = get_issue_title(args.issue, args.repo)
    except Exception:
        title = "未知标题"

    commits = get_commits_since(args.base)
    files = get_changed_files(args.base)

    commit_lines = "\n".join(f"- {c}" for c in commits) if commits else "- （无单独提交记录）"
    file_lines = "\n".join(f"- `{f}`" for f in files) if files else "- （无文件变更）"

    report = f"""## 完成报告

**Issue**: #{args.issue} {title}
**完成时间**: {datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")}

### 根因
- （待补充：问题的根本原因是什么）

### 修复内容
**提交记录**:
{commit_lines}

**变更文件**:
{file_lines}

### 验证方式
- （待补充：如何验证修复有效）

### 留下的坑 / 已知问题
- （待补充：边界情况、临时方案、未覆盖测试等）
"""

    output_path = Path(args.output)
    output_path.write_text(report, encoding="utf-8")
    print(f"报告已写入 {output_path.resolve()}")
    print("\n--- 预览 ---\n")
    print(report)


if __name__ == "__main__":
    main()
