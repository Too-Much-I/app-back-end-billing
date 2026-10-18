const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const source = fs.readFileSync(__dirname + '/billing-test-cleanup-maintenance.js', 'utf8');
const Long = value => ({_bsontype:'Long', toString:()=>String(value), toJSON:()=>Number(value)});
function fixture(change = {}) {
  const c = {_id:'test', environment:'SANDBOX', consumer:'billing', epoch:'epoch',
    version:Long(2), schemaVersion:Long(1), barrier:'OPEN'};
  const manifest = {validator:{rule:'r2'}, indexes:[{name:'unique',keys:{environment:1,userId:1},unique:true}]};
  const controlValidator = {rule:'control4'};
  const options = {};
  const indexes = [];
  const writes = [];
  let blocked = false;
  const controls = {countDocuments:async()=>1,findOne:async()=>({...c}),async updateOne(match, update) {
    writes.push({match,update});
    for (const [k,v] of Object.entries(match)) assert.equal(JSON.stringify(c[k]),JSON.stringify(v));
    Object.assign(c,update.$set);c.version=Long(Number(c.version.toString())+1);
    blocked=c.barrier==='ALL_BLOCKED';return {modifiedCount:1};
  }};
  const jobs = {countDocuments:async()=>change.rows || 0,getIndexes:async()=>indexes,
    async createIndex(key,opt){writes.push('index');indexes.push({key,...opt});}};
  const db = {getName:()=>change.db || 'to-teacher-billing-test',
    getCollection:n=>n==='payment_recovery_controls'?controls:jobs,
    getCollectionInfos:async({name})=>[{options:name==='payment_recovery_controls'
      ?{validator:controlValidator,validationLevel:'strict',validationAction:'error'}:options}],
    async runCommand(command) {if(change.failDDL) throw Error('DDL');Object.assign(options,
      {validator:command.validator,validationLevel:command.validationLevel,validationAction:command.validationAction});return {ok:1};}};
  if(change.baseline)c.baseline={};
  if(change.badVersion)c.schemaVersion=Long(3);
  const context=vm.createContext({EJSON:{stringify:JSON.stringify},Long,print:()=>{},
    process:{env:{BILLING_WRITERS_STOPPED:change.notStopped?'false':'true'}}});
  vm.runInContext(source,context);
  return {c,writes,run:apply=>context.installCleanup(db,manifest,controlValidator,apply),isBlocked:()=>blocked};
}
test('dry run never writes',async()=>{const f=fixture();await f.run(false);assert.equal(f.writes.length,0);});
test('install preserves epoch and upgrades only after DDL',async()=>{const f=fixture();await f.run(true);
  assert.equal(f.c.epoch,'epoch');assert.equal(f.c.barrier,'OPEN');assert.equal(f.c.schemaVersion.toString(),'4');assert.equal(f.c.version.toString(),'4');});
for(const change of [{db:'production'},{rows:1},{baseline:true},{badVersion:true},{notStopped:true}])
  test('reject unsafe precondition '+JSON.stringify(change),async()=>{const f=fixture(change);await assert.rejects(()=>f.run(true));assert.equal(f.writes.length,0);});
test('DDL failure leaves blocked',async()=>{const f=fixture({failDDL:true});await assert.rejects(()=>f.run(true));assert.ok(f.isBlocked());assert.equal(f.c.schemaVersion.toString(),'1');});
