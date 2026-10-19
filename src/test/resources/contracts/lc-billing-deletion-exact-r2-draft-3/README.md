# Deletion draft-3 clarification inputs

Draft, not frozen or executed against product code. [Specification](../../../../../docs/contracts/TMI-217-DELETION-EXACT-R2-DRAFT-3.md).

Six `retryCases` are pure expected-schedule examples. `candidate` is already parsed; they do not test the HTTP header parser. `expectedDue` is the computed bound; `review=true` means **do not persist an automatic nextAttemptAt or send at this time**. A NEEDS_REVIEW state is not automatically resumed even if the bound later passes. Existing floor values must also be honored by claims/retries, not only response processing.

`positive503.rawUtf8` is exact JSON response text, to encode directly as UTF-8. Eight correlationId cases distinguish opaque strings from an unwarranted UUID-only constraint. Additional length checks at 1/128/129 ASCII characters are required by the specification. No actual sender/HTTP/transaction validation has run for this package.

Original draft-1/draft-2 files and expected hashes are preserved. Static arithmetic/format/hash checks are packaging checks, not independent implementation or runtime PASS evidence. No real identifiers or secrets are included.
