// mongosh --nodb --quiet --file scripts/inspection/source-inventory.js
// Inject SOURCE_INVENTORY_URI, SOURCE_INVENTORY_DATABASE, SOURCE_INVENTORY_ENV,
// SOURCE_INVENTORY_ROLE=billing|lc from an approved environment. Never pass a URI in argv.
// Counts only. No writes, DDL, identifiers or raw errors in output.
(async function () {
  const role = process.env.SOURCE_INVENTORY_ROLE;
  const environment = process.env.SOURCE_INVENTORY_ENV;
  const uri = process.env.SOURCE_INVENTORY_URI;
  const database = process.env.SOURCE_INVENTORY_DATABASE;
  if (!['billing', 'lc'].includes(role) || !['test', 'staging', 'production'].includes(environment)
      || !uri || !database) {
    print(JSON.stringify({status: 'CONFIG_REQUIRED'})); quit(2); return;
  }
  try {
    const connection = new Mongo(uri);
    const target = connection.getDB(database);
    const names = new Set(target.getCollectionNames());
    const fields = role === 'billing' ? {
      reservations: ['entitlementSource', 'subjectRefId', 'operationId', 'attemptGroupId', 'proposedSessionId'],
      attempt_sessions: ['entitlementSource', 'operationId', 'attemptGroupId'],
      attempt_groups: ['entitlementSource', 'trialClaimId', 'consumptionLedgerEventId'],
      idempotency_commands: ['responseSnapshot.entitlementSource'],
      reservation_allocations: ['reservationId', 'grantId']
    } : {
      exam_creation_operations: ['entitlementSource', 'reservationId', 'attemptGroupId', 'sessionId'],
      exam_sessions: ['entitlementSource', 'billingReservationId', 'attemptGroupId']
    };
    const results = {};
    for (const [name, paths] of Object.entries(fields)) {
      if (!names.has(name)) { results[name] = {collectionExists: false}; continue; }
      const sums = {_id: null, total: {$sum: 1}};
      for (let i = 0; i < paths.length; i++) {
        sums['missing' + i] = {$sum: {$cond: [{$in: [{$ifNull: ['$' + paths[i], null]}, [null, '']]}, 1, 0]}};
      }
      const rows = target.getCollection(name).aggregate([{$group: sums}], {maxTimeMS: 10000}).toArray();
      const row = rows[0] || {total: 0};
      results[name] = {collectionExists: true, total: row.total, missingOrNullOrEmpty: {}};
      paths.forEach((path, i) => {results[name].missingOrNullOrEmpty[path] = row['missing' + i] || 0;});
    }
    print(JSON.stringify({status: 'COUNT_SCAN_COMPLETE', role, environment, at: new Date().toISOString(),
      scope: 'Presence counts only; not source proof, linkage validation, consistent snapshot or migration approval', results}));
  } catch (_) {
    print(JSON.stringify({status: 'SCAN_FAILED', role, environment,
      reason: 'Connection, permission or query failure; raw error suppressed'})); quit(1);
  }
})();
