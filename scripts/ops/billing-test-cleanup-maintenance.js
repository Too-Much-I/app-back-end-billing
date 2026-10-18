// One-off mongosh maintenance. All application writers must be stopped externally.
// The bundle supplies exact repository manifests; no credentials or user data are printed.
async function installCleanup(db, manifest, controlValidator, apply) {
  let step = 0;
  const check = (ok) => { step++; if (!ok) throw new Error('CLEANUP_MAINTENANCE_PRECONDITION_' + step); };
  const eq = (a, b) => EJSON.stringify(a, {relaxed:true}) === EJSON.stringify(b, {relaxed:true});
  check(db.getName() === 'to-teacher-billing-test');
  const controls = db.getCollection('payment_recovery_controls');
  check(await controls.countDocuments({}) === 1);
  const c = await controls.findOne({environment:'SANDBOX', consumer:'billing'});
  check(c && c.schemaVersion.toString() === '1' && c.barrier === 'OPEN');
  check(Object.keys(c).sort().join(',') === '_id,barrier,consumer,environment,epoch,schemaVersion,version');
  check(c.version._bsontype === 'Long' && c.schemaVersion._bsontype === 'Long');
  check(BigInt(c.version.toString()) < 9223372036854775804n);
  const controlOptions = (await db.getCollectionInfos({name:'payment_recovery_controls'}))[0].options;
  check(eq(controlOptions.validator, controlValidator));
  check(controlOptions.validationLevel === 'strict' && controlOptions.validationAction === 'error');
  const name = 'payment_account_cleanup_jobs', jobs = db.getCollection(name);
  check(await jobs.countDocuments({}) === 0); // No proof/lease/job evidence is translated.
  const options = (await db.getCollectionInfos({name}))[0].options;
  check(!options.collation || options.collation.locale === 'simple');
  check(!options.validator || Object.keys(options.validator).length === 0 || eq(options.validator, manifest.validator));
  const indexes = await jobs.getIndexes();
  check(indexes.every(i => i.expireAfterSeconds === undefined));
  for (const expected of manifest.indexes) {
    const actual = indexes.find(i => i.name === expected.name);
    if (!actual) { check(!indexes.some(i => eq(i.key, expected.keys))); continue; }
    check(eq(actual.key, expected.keys) && !!actual.unique === expected.unique);
    check(!actual.hidden && !actual.sparse && !actual.partialFilterExpression);
    check(!actual.collation || actual.collation.locale === 'simple');
  }
  if (!apply) { print('CLEANUP_MAINTENANCE_DRY_RUN_OK'); return; }
  check(process.env.BILLING_WRITERS_STOPPED === 'true');
  const match = {_id:c._id, environment:c.environment, consumer:c.consumer,
    epoch:c.epoch, schemaVersion:c.schemaVersion, version:c.version, barrier:'OPEN'};
  check((await controls.updateOne(match, {$set:{barrier:'ALL_BLOCKED'}, $inc:{version:Long(1)}})).modifiedCount === 1);
  // Any subsequent failure leaves ALL_BLOCKED, never opens a partially installed schema.
  for (const expected of manifest.indexes) {
    if (!indexes.some(i => i.name === expected.name))
      await jobs.createIndex(expected.keys, {name:expected.name, unique:expected.unique});
  }
  check((await db.runCommand({collMod:name, validator:manifest.validator,
    validationLevel:'strict', validationAction:'error'})).ok === 1);
  const installed = (await db.getCollectionInfos({name}))[0].options;
  check(eq(installed.validator, manifest.validator) && installed.validationLevel === 'strict'
    && installed.validationAction === 'error' && await jobs.countDocuments({}) === 0);
  for (const expected of manifest.indexes) {
    const actual = (await jobs.getIndexes()).find(i => i.name === expected.name);
    check(actual && eq(actual.key, expected.keys) && !!actual.unique === expected.unique);
  }
  const blockedVersion = Long((BigInt(c.version.toString()) + 1n).toString());
  check((await controls.updateOne({...match, version:blockedVersion, barrier:'ALL_BLOCKED'},
    {$set:{schemaVersion:Long(4), barrier:'OPEN'}, $inc:{version:Long(1)}})).modifiedCount === 1);
  // Restore the prior OPEN state, not a restore/catch-up approval. Epoch is unchanged;
  // all old processes were stopped and there are no cleanup or recovery job fields.
  print('CLEANUP_MAINTENANCE_INSTALLED_R2_CONTROL4');
}
