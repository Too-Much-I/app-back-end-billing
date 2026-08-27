#!/usr/bin/env python3
"""Inject turn-scoped Billing worklog requirements."""

from __future__ import annotations

import json
import sys


def valid_turn_id(value: object) -> str | None:
    if not isinstance(value, str) or not value or len(value) > 512:
        return None
    if not value.isprintable() or "-->" in value:
        return None
    return value


def main() -> None:
    try:
        event = json.load(sys.stdin)
    except (json.JSONDecodeError, OSError):
        event = None

    turn_id = valid_turn_id(event.get("turn_id")) if isinstance(event, dict) else None
    marker = f"<!-- codex-turn:{turn_id} -->" if turn_id else "현재 turn marker"
    instruction = (
        "작업 종료 전에 docs/codex/WORKLOG.md 끝에 새 항목을 append하고 "
        "docs/codex/CURRENT_STATE.md를 갱신하세요. 과거 WORKLOG는 수정하지 말고, "
        f"새 항목에 {marker} 를 포함하세요. Secret, Token, 결제 원문과 개인정보는 기록하지 마세요."
    )
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "UserPromptSubmit",
            "additionalContext": instruction,
        }
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
