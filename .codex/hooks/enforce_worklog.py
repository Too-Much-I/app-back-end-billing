#!/usr/bin/env python3
"""Block turn completion until the Billing worklog contains the turn marker."""

from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path


def valid_turn_id(value: object) -> str | None:
    if not isinstance(value, str) or not value or len(value) > 512:
        return None
    if not value.isprintable() or "-->" in value:
        return None
    return value


def git_output(root: Path, *arguments: str) -> str:
    try:
        result = subprocess.run(
            ["git", *arguments], cwd=root, check=False, capture_output=True,
            text=True, encoding="utf-8", errors="replace", timeout=5,
        )
    except (OSError, subprocess.SubprocessError):
        return "(조회 실패)"
    return result.stdout.strip()[:12000] if result.returncode == 0 else "(조회 실패)"


def main() -> None:
    try:
        event = json.load(sys.stdin)
    except (json.JSONDecodeError, OSError):
        event = None

    active = isinstance(event, dict) and event.get("stop_hook_active") is True
    turn_id = valid_turn_id(event.get("turn_id")) if isinstance(event, dict) else None
    if turn_id is None:
        payload = {"continue": True} if active else {
            "decision": "block",
            "reason": "유효한 turn_id가 없습니다. Billing WORKLOG와 CURRENT_STATE를 갱신하세요.",
        }
        print(json.dumps(payload, ensure_ascii=False))
        return

    marker = f"<!-- codex-turn:{turn_id} -->"
    root = Path(__file__).resolve().parents[2]
    worklog = root / "docs" / "codex" / "WORKLOG.md"
    try:
        if marker in worklog.read_text(encoding="utf-8", errors="replace"):
            print(json.dumps({"continue": True}))
            return
    except OSError:
        pass

    if not active:
        print(json.dumps({
            "decision": "block",
            "reason": f"Billing WORKLOG 끝에 현재 작업 항목과 {marker} 를 추가하고 CURRENT_STATE를 갱신하세요.",
        }, ensure_ascii=False))
        return

    record = (
        "\n## Codex Stop Hook 안전 fallback\n\n"
        f"{marker}\n\n"
        f"- 현재 브랜치: `{git_output(root, 'branch', '--show-current') or '(없음)'}`\n"
        f"- 상태:\n\n```text\n{git_output(root, 'status', '--short') or '(변경 없음)'}\n```\n"
    )
    try:
        worklog.parent.mkdir(parents=True, exist_ok=True)
        with worklog.open("a", encoding="utf-8") as output:
            output.write(record)
            output.flush()
            os.fsync(output.fileno())
    except OSError:
        pass
    print(json.dumps({"continue": True}))


if __name__ == "__main__":
    main()
