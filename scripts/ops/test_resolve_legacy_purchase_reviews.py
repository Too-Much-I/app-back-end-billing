import copy
import datetime as dt
import unittest
from types import SimpleNamespace
import resolve_legacy_purchase_reviews as tool


class ReviewChecks(unittest.TestCase):
    def setUp(self):
        self.p = dict(_id="purchase", environment="PRODUCTION", storeEnvironment="SANDBOX",
                      store="APP_STORE", appId="app", transactionKey="a" * 64,
                      lookupKeyVersion="v1", ownerRefId="owner", accountId="account",
                      purchasedAt=dt.datetime(2026, 10, 18, 9),
                      productSnapshot={"storeProductId": "premium1d"},
                      purchaseState="VERIFIED", accessState="ALLOWED", processingStatus="READY")
        self.e = dict(eventKey="b" * 64, receptionDigest="c" * 64,
                      purchaseFingerprint=tool.fingerprint(self.p), sourceObservationKey="d" * 64,
                      dashboardEvidenceDigest="e" * 64)
        self.r = dict(environment="PRODUCTION", providerEventKey=self.e["eventKey"],
                      receptionDigest=self.e["receptionDigest"], reason="OWNER_UNRESOLVED",
                      state="REVIEW_REQUIRED", createdAt=dt.datetime(2026, 10, 18, 9),
                      accountId="account", lifecycleId="life", version=1, counted=True)
        self.l = dict(purchaseId="purchase", type="PURCHASE_VERIFIED", sourceObservationKey="d" * 64)
        self.a = dict(_id="account", environment="PRODUCTION", userId="user", state="ACTIVE")
        self.life = dict(_id="life", environment="PRODUCTION", userId="user", state="ACTIVE",
                         version=1, pendingEvidence=2)
        self.actor = "f" * 64

    def check(self):
        return tool.validate_rows(self.e, self.r, self.p, self.l, self.a, self.life, self.actor)

    def test_verified_match_is_eligible_without_fabricating_candidate(self):
        before = copy.deepcopy((self.r, self.p, self.life))
        self.assertTrue(self.check())
        self.assertEqual(before, (self.r, self.p, self.life))
        self.assertNotIn("reviewCandidate", self.r)

    def test_all_purchase_fingerprint_fields_are_checked(self):
        for key in ("ownerRefId", "transactionKey", "accountId", "lookupKeyVersion", "storeEnvironment", "appId", "store"):
            with self.subTest(key=key):
                old = self.p[key]
                self.p[key] = "different"
                with self.assertRaises(ValueError): self.check()
                self.p[key] = old

    def test_purchase_time_and_product_checked(self):
        self.p["purchasedAt"] += dt.timedelta(seconds=1)
        with self.assertRaises(ValueError): self.check()
        self.p["purchasedAt"] -= dt.timedelta(seconds=1)
        self.p["productSnapshot"]["storeProductId"] = "other"
        with self.assertRaises(ValueError): self.check()

    def test_digest_conflict_rejected(self):
        self.r["receptionDigest"] = "0" * 64
        with self.assertRaises(ValueError): self.check()

    def test_cutoff_excludes_new_cases(self):
        self.r["createdAt"] = tool.CUTOFF
        with self.assertRaises(ValueError): self.check()

    def test_reversal_and_normalized_case_rejected(self):
        self.r["reason"] = "EVENT_REQUIRES_LOOKUP"
        with self.assertRaises(ValueError): self.check()
        self.r["reason"] = "OWNER_UNRESOLVED"
        self.r["normalizedObservation"] = {}
        with self.assertRaises(ValueError): self.check()

    def test_withdrawn_or_wrong_lifecycle_rejected(self):
        self.life["state"] = "WITHDRAWN"
        with self.assertRaises(ValueError): self.check()
        self.life["state"] = "ACTIVE"
        self.life["_id"] = "other"
        with self.assertRaises(ValueError): self.check()

    def test_missing_verification_or_revoked_purchase_rejected(self):
        self.l["type"] = "REFUND"
        with self.assertRaises(ValueError): self.check()
        self.l["type"] = "PURCHASE_VERIFIED"
        self.p["accessState"] = "REVOKED"
        with self.assertRaises(ValueError): self.check()

    def test_no_counter_underflow(self):
        self.life["pendingEvidence"] = 0
        with self.assertRaises(ValueError): self.check()

    def test_idempotent_only_for_same_audited_resolution(self):
        self.r.update(reviewState="RESOLVED", counted=False, purchaseId="purchase",
                      resolvedObservationKey="d" * 64, actorDigest=self.actor,
                      resolvedAt=dt.datetime(2026, 10, 19))
        self.assertFalse(self.check())
        self.r["actorDigest"] = "0" * 64
        with self.assertRaises(ValueError): self.check()

    def test_manifest_exact_ten_unique_hash_only_entries(self):
        entries = [dict(self.e, eventKey=f"{n:064x}") for n in range(10)]
        m = dict(purpose="legacy-review-2026-10-19", actorDigest=self.actor, entries=entries)
        tool.validate_manifest(m)
        entries[1] = entries[0]
        with self.assertRaises(ValueError): tool.validate_manifest(m)
        entries.pop()
        with self.assertRaises(ValueError): tool.validate_manifest(m)

    def batch(self):
        class Cursor(list):
            def limit(self, n): return Cursor(self[:n])

        class Collection:
            def __init__(self, rows):
                self.rows, self.writes = copy.deepcopy(rows), 0
            def find(self, query, **kwargs):
                return Cursor(copy.deepcopy([r for r in self.rows if all(r.get(k) == v for k, v in query.items())]))
            def find_one(self, query, **kwargs):
                rows = self.find(query)
                return rows[0] if rows else None
            def update_one(self, query, update, **kwargs):
                for r in self.rows:
                    if all(r.get(k) == v for k, v in query.items()):
                        r.update(update.get("$set", {}))
                        for k, v in update.get("$inc", {}).items(): r[k] += v
                        self.writes += 1
                        return SimpleNamespace(modified_count=1)
                return SimpleNamespace(modified_count=0)

        reasons = [k for k, count in tool.REASONS.items() for _ in range(count)]
        entries = [dict(self.e, eventKey=f"{n:064x}") for n in range(10)]
        reviews = [dict(self.r, _id=str(n), providerEventKey=e["eventKey"], reason=reasons[n]) for n, e in enumerate(entries)]
        db = SimpleNamespace(name=tool.DB_NAME,
            payment_recovery_controls=Collection([{"barrier": "ALL_BLOCKED"}]),
            payment_purchase_cases=Collection(reviews), payment_ledger=Collection([self.l]),
            purchases=Collection([self.p]), payment_accounts=Collection([self.a]),
            payment_account_lifecycles=Collection([dict(self.life, pendingEvidence=10)]))
        manifest = dict(purpose="legacy-review-2026-10-19", actorDigest=self.actor, entries=entries)
        return db, manifest

    def test_batch_dry_run_has_zero_writes(self):
        db, m = self.batch()
        self.assertEqual(tool.run(db, None, m, long_type=int), {"matched": 10, "wouldResolve": 10})
        self.assertTrue(all(c.writes == 0 for c in vars(db).values() if hasattr(c, "writes")))

    def test_batch_apply_and_rerun_do_not_double_decrement(self):
        db, m = self.batch()
        original_purchase = copy.deepcopy(db.purchases.rows)
        self.assertEqual(tool.run(db, None, m, True, int)["resolved"], 10)
        self.assertEqual(db.payment_account_lifecycles.rows[0]["pendingEvidence"], 0)
        self.assertEqual(tool.run(db, None, m, True, int)["resolved"], 0)
        self.assertEqual(db.payment_account_lifecycles.writes, 1)
        self.assertEqual(db.purchases.rows, original_purchase)
        self.assertEqual(db.payment_ledger.writes, 0)
        self.assertEqual(len(db.payment_purchase_cases.rows), 10)

    def test_last_case_failure_prevents_all_writes(self):
        db, m = self.batch()
        db.payment_purchase_cases.rows[-1]["receptionDigest"] = "0" * 64
        with self.assertRaises(ValueError): tool.run(db, None, m, True, int)
        self.assertEqual(db.payment_purchase_cases.writes, 0)

    def test_open_barrier_rejects_apply(self):
        db, m = self.batch()
        db.payment_recovery_controls.rows[0]["barrier"] = "OPEN"
        with self.assertRaises(ValueError): tool.run(db, None, m, True, int)
        self.assertEqual(db.payment_purchase_cases.writes, 0)


if __name__ == "__main__":
    unittest.main()
