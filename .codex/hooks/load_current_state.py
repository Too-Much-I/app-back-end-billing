#!/usr/bin/env python3
"""Inject Billing CURRENT_STATE.md at session startup or resume."""

from __future__ import annotations

import json
import sys
from pathlib import Path


def main() -> None:
    try:
        event = json.load(sys.stdin)
    except (json.JSONDecodeError, OSError):
        event = None

    if not isinstance(event, dict):
        print(json.dumps({"systemMessage": "Billing CURRENT_STATE hook 입력을 읽지 못했습니다."}, ensure_ascii=False))
        return

    state_path = Path(__file__).resolve().parents[2] / "docs" / "codex" / "CURRENT_STATE.md"
    try:
        state = state_path.read_text(encoding="utf-8")
    except OSError:
        print(json.dumps({"systemMessage": "Billing CURRENT_STATE.md를 읽지 못했습니다."}, ensure_ascii=False))
        return

    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": "다음은 Billing Service의 현재 저장소 상태입니다.\n\n" + state,
        }
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
