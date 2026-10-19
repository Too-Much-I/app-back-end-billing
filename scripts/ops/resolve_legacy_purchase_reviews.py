"""Explicit, one-off review closure; never a webhook replay or purchase writer.

Default is read-only. Apply requires isolated writers and ALL_BLOCKED, plus the
SHA256 of the reviewed manifest. URI is entered without echo. No raw provider
payload or owner/transaction identifiers belong in the manifest or output.
"""
import argparse
import datetime as dt
import getpass
import hashlib
import json
import re

DB_NAME = "to-teacher-billing-prod"
ENV = "PRODUCTION"
CUTOFF = dt.datetime(2026, 10, 18, 10, 28)
REASONS = {"OWNER_UNRESOLVED": 5, "QUANTITY_REQUIRES_LOOKUP": 2,
           "QUANTITY_UNSUPPORTED": 3}


def require(value):
    if not value:
        raise ValueError("LEGACY_REVIEW_PRECONDITION_FAILED")


def sha(value):
    return hashlib.sha256(value.encode()).hexdigest()


def fingerprint(purchase):
    # Includes all independently verified matching fields, without exporting IDs.
    fields = {k: purchase.get(k) for k in (
        "store", "appId", "transactionKey", "lookupKeyVersion", "ownerRefId",
        "accountId")}
    fields["storeEnvironment"] = purchase.get("storeEnvironment", purchase.get("environment"))
    fields["purchasedAt"] = purchase["purchasedAt"].isoformat(timespec="milliseconds")
    fields["storeProductId"] = purchase["productSnapshot"]["storeProductId"]
    return sha(json.dumps(fields, sort_keys=True, separators=(",", ":")))


def validate_manifest(m):
    require(set(m) == {"purpose", "actorDigest", "entries"})
    require(m["purpose"] == "legacy-review-2026-10-19")
    require(isinstance(m["entries"], list) and len(m["entries"]) == 10)
    require(re.fullmatch("[a-f0-9]{64}", m["actorDigest"]))
    seen = set()
    for e in m["entries"]:
        require(set(e) == {"eventKey", "receptionDigest", "purchaseFingerprint",
                           "sourceObservationKey", "dashboardEvidenceDigest"})
        require(all(isinstance(v, str) and re.fullmatch("[a-f0-9]{64}", v) for v in e.values()))
        require(e["eventKey"] not in seen)
        seen.add(e["eventKey"])


def validate_rows(e, r, p, ledger, account, life, actor):
    require(r and p and ledger and account and life)
    require(r.get("environment") == p.get("environment") == ENV)
    require(r.get("providerEventKey") == e["eventKey"])
    require(r.get("receptionDigest") == e["receptionDigest"])
    require(r.get("reason") in REASONS and r.get("normalizedObservation") is None)
    require(r.get("state") == "REVIEW_REQUIRED" and r.get("createdAt") < CUTOFF)
    require(p.get("purchaseState") == "VERIFIED" and p.get("accessState") == "ALLOWED"
            and p.get("processingStatus") == "READY")
    require(fingerprint(p) == e["purchaseFingerprint"])
    require(ledger.get("purchaseId") == p["_id"] and ledger.get("type") == "PURCHASE_VERIFIED"
            and ledger.get("sourceObservationKey") == e["sourceObservationKey"])
    require(account.get("environment") == life.get("environment") == ENV)
    require(account.get("state") == life.get("state") == "ACTIVE")
    require(r.get("accountId") == p.get("accountId") == account["_id"])
    require(account.get("userId") == life.get("userId") and r.get("lifecycleId") == life["_id"])
    for row in (r, life):
        require(type(row.get("version")).__name__ in ("int", "Int64")
                and 0 < row["version"] < 2**63 - 1)
    require(type(life.get("pendingEvidence")).__name__ in ("int", "Int64")
            and life["pendingEvidence"] >= 0)
    require(isinstance(r.get("counted"), bool))
    if r.get("reviewState") == "RESOLVED":
        require(r.get("purchaseId") == p["_id"] and not r["counted"]
                and r.get("resolvedObservationKey") == e["sourceObservationKey"]
                and r.get("actorDigest") == actor and r.get("resolvedAt") is not None)
        return False
    require(r.get("reviewState") in (None, "OPEN") and r.get("resolvedObservationKey") is None)
    require(not r["counted"] or life["pendingEvidence"] > 0)
    return True


def run(db, session, manifest, apply=False, long_type=None):
    if long_type is None:
        from bson.int64 import Int64
        long_type = Int64
    validate_manifest(manifest)
    require(db.name == DB_NAME)
    kw = {"session": session}
    if apply:
        controls = list(db.payment_recovery_controls.find({}, **kw))
        require(controls and all(c.get("barrier") == "ALL_BLOCKED" for c in controls))
    changes, reasons = [], {}
    for e in manifest["entries"]:
        rows = list(db.payment_purchase_cases.find({"environment": ENV, "providerEventKey": e["eventKey"]}, **kw).limit(2))
        require(len(rows) == 1)
        r = rows[0]
        ledgers = list(db.payment_ledger.find({"sourceObservationKey": e["sourceObservationKey"], "type": "PURCHASE_VERIFIED"}, **kw).limit(2))
        require(len(ledgers) == 1)
        ledger = ledgers[0]
        p = db.purchases.find_one({"_id": ledger["purchaseId"]}, **kw)
        require(p is not None)
        account = db.payment_accounts.find_one({"_id": p["accountId"]}, **kw)
        require(account is not None)
        life = db.payment_account_lifecycles.find_one({"environment": ENV, "userId": account["userId"]}, **kw)
        needed = validate_rows(e, r, p, ledger, account, life, manifest["actorDigest"])
        reasons[r["reason"]] = reasons.get(r["reason"], 0) + 1
        if needed:
            changes.append((e, r, p, life))
    require(reasons == REASONS)
    if not apply:
        return {"matched": 10, "wouldResolve": len(changes)}
    # One transaction: a failure rolls back all cases and pending counters.
    deltas = {}
    for e, r, p, life in changes:
        if r["counted"]:
            prev = deltas.setdefault(life["_id"], [life, 0])
            prev[1] += 1
        result = db.payment_purchase_cases.update_one(
            {"_id": r["_id"], "version": r["version"], "receptionDigest": e["receptionDigest"]},
            {"$set": {"reviewState": "RESOLVED", "resolvedAt": dt.datetime.now(dt.timezone.utc),
                      "purchaseId": p["_id"], "resolvedObservationKey": e["sourceObservationKey"],
                      "actorDigest": manifest["actorDigest"], "counted": False},
             "$inc": {"version": long_type(1)}}, **kw)
        require(result.modified_count == 1)
    for life, count in deltas.values():
        require(life["pendingEvidence"] >= count)
        result = db.payment_account_lifecycles.update_one(
            {"_id": life["_id"], "version": life["version"], "pendingEvidence": life["pendingEvidence"]},
            {"$inc": {"version": long_type(1), "pendingEvidence": long_type(-count)}}, **kw)
        require(result.modified_count == 1)
    return {"matched": 10, "resolved": len(changes)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--writers-stopped", action="store_true")
    parser.add_argument("--approved-manifest-sha256")
    args = parser.parse_args()
    with open(args.manifest, "rb") as f:
        raw = f.read(32769)
    require(len(raw) <= 32768)
    manifest = json.loads(raw)
    validate_manifest(manifest)
    if args.apply:
        require(args.writers_stopped and hashlib.sha256(raw).hexdigest() == args.approved_manifest_sha256)
    from pymongo import MongoClient
    from pymongo.read_concern import ReadConcern
    from pymongo.write_concern import WriteConcern
    with MongoClient(getpass.getpass("Mongo URI (hidden): "), serverSelectionTimeoutMS=15000) as client:
        with client.start_session() as session:
            result = session.with_transaction(lambda s: run(client[DB_NAME], s, manifest, args.apply),
                read_concern=ReadConcern("snapshot"), write_concern=WriteConcern("majority"))
    print(json.dumps(result))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # Driver errors can contain addresses/data. Operator diagnostics stay private.
        print("LEGACY_REVIEW_STOPPED_NO_SUCCESS_CONFIRMATION")
        raise SystemExit(1)
