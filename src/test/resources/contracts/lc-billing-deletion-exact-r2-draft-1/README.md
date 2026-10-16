# Deletion exact R2 draft-1 fixture proposal

This is a review fixture catalog, **not an executed contract test suite or a frozen package**.

`cases.json` contains one fake request, 12 wire transformations, 18 business-state scenarios and 14 delivery/retention scenarios (44 cases). `change` and `scenario` are human-readable construction instructions, not an executable mutation language. Implementations must build the requested raw JSON/DB states independently; no scenario is marked PASS.

Specification: [exact draft](../../../../../docs/contracts/TMI-217-DELETION-EXACT-R2-DRAFT-1.md).

Existing authentication/scalar/canonical fixtures are unchanged. This catalog does not replace their boundary tests or supply new signed JWT vectors. A frozen package needs shared acceptance, concrete byte-level vectors/digests, independent validators, and implementation/transaction tests. Static JSON parsing/unique IDs/hash checks prove only packaging integrity.

No real user, credential, payment or provider data is included. No automatic runtime tests consume this draft yet. Exact schema/retention/retry proposals remain subject to LC review. SHA-256 values, when reported, are file integrity checks and not signatures or shared acceptance.
