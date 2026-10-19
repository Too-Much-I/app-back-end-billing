# Deletion exact R2 draft-2 — partial raw fixtures and boundary specification

Status: **DRAFT_NOT_EXECUTED**. Not a frozen package, implementation oracle or test pass report.

- `bytes.json`: 7 vectors. `rawUtf8` is the exact string to UTF-8 encode without parsing/re-serializing; `rawHex` decodes to exact bytes. B04 contains invalid UTF-8; B05 is valid UTF-8 JSON containing a lone-surrogate escape. B06 preserves the `1.0` numeric token; B07 preserves duplicate keys.
- B01–B03 test canonical serialization only, not HTTP/business validity. B03 exercises Unicode/quote/backslash/control escaping; do not infer that every such opaque Session value is admitted by the runtime decoder. Expected canonical bytes are the UTF-8 encoding of `expectedCanonicalUtf8`; SHA256 is over those bytes.
- `boundaries.json`: 31 precise timing/authentication/response/state specifications. These are still scenario inputs, not executable network or transaction tests. R/C cases require actual sender/DB implementations. They must not be reported as executed merely because JSON loads.
- [Specification](../../../../../docs/contracts/TMI-217-DELETION-EXACT-R2-DRAFT-2.md).

## ID mapping (always cite revision + ID)

| Previous source | Meaning | New reference |
| --- | --- | --- |
| historical TMI217 verification plan W10 | repeated deletion with another event | draft-1 S04; draft-2 C02 concurrency |
| historical TMI217 verification plan W11 | same transmission vs new request | draft-1 S02–S05; draft-2 C01 |
| draft-1 W10 | body above16384 bytes | remains draft-1 W10 (not repeated-deletion case) |
| draft-1 W11 | invalid UTF8/lone surrogate | draft-2 B04/B05 separately |
| draft-1 D03 | wrong purpose | draft-2 H01 trusted signature / H02 untrusted signature |
| draft-1 D07/D08 | review/TTL | draft-2 C03/C04/C05 |
| draft-1 D10 | stale response | draft-2 R12/R13 |

The original draft-1 cases/hash remain unchanged. Still needed before freeze: full scalar raw-byte boundaries, signed JWT vectors for the new purpose/port/query matrix, independently executed canonical/strict decoder validation, actual concurrent transaction and worker tests. Hash/byte packaging checks are not substitutes. There are no real keys, users or provider transactions here.
