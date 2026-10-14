// Offline fake fixtures. RSA private keys live in memory only. Never overwrite a revision.
import {generateKeyPairSync,sign,createHash} from 'node:crypto';
import {mkdirSync,existsSync,writeFileSync,readFileSync} from 'node:fs';
const dir='src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-2';
if(existsSync(dir)) throw new Error('Revision exists: do not regenerate accepted bytes');
mkdirSync(dir,{recursive:true});
const revision='lc-billing-paid-exam-r2-fixtures-2';
const uid=n=>`${String(n).padStart(8,'0')}-1111-4111-8111-111111111111`;
const now='2026-10-14T00:00:00.000Z', epoch=Date.parse(now)/1000;
const hash=b=>createHash('sha256').update(b).digest('hex');
const canonical=v=>{
 if(v===null)return 'null';
 if(typeof v==='string'){
  let out='"';
  for(const ch of v){
   const cp=ch.codePointAt(0);
   if(cp>=0xd800 && cp<=0xdfff)throw Error('Unpaired surrogate');
   if(cp<32)out+=String.fromCharCode(92)+'u'+cp.toString(16).padStart(4,'0');
   else if(cp===34 || cp===92)out+=String.fromCharCode(92)+ch;
   else out+=ch;
  }
  return out+'"';
 }
 if(typeof v==='number'){if(!Number.isSafeInteger(v))throw Error('integer');return String(v);}
 if(Array.isArray(v))return '['+v.map(canonical).join(',')+']';
 return '{'+Object.entries(v).map(([k,x])=>canonical(k)+':'+canonical(x)).join(',')+'}';
};
const files=[];
function save(name,value){writeFileSync(`${dir}/${name}`,typeof value==='string'?value:JSON.stringify(value,null,2)+'\n');files.push(name);}
const event={eventId:uid(1),eventType:'AttemptGroupAccessRevoked',schemaVersion:1,producer:'billing',consumerScopeId:'fixture-test',occurredAt:now,userId:uid(2),attemptGroupId:uid(3),reason:'PURCHASE_REFUNDED',confirmedSessionId:null};
const vectors=[];
function vector(id,obj,raw=JSON.stringify(obj),kind='event'){
 const input=Buffer.from(raw);const output=Buffer.from(canonical(obj));
 if(JSON.stringify(JSON.parse(output.toString('utf8')))!==JSON.stringify(obj))throw Error('Canonical round-trip changed value');
 vectors.push({id,kind,inputBase64:input.toString('base64'),expectedCanonicalBase64:output.toString('base64'),expectedSha256:hash(output)});
}
vector('V04-null',event);
vector('V04-protected',{...event,confirmedSessionId:'session-old'});
vector('V04-reordered',event,JSON.stringify(Object.fromEntries(Object.entries(event).reverse()),null,2));
// String codec vectors test serialization, not validity as workload event identifiers.
for(const [id,value] of Object.entries({unicode:'한글/😀',escapes:'"\\\n\t\b\f\r\u0000\u001f',composed:'é',decomposed:'e\u0301'}))vector('V04-codec-'+id,value,JSON.stringify(value),'string-codec');
vector('V04-surrogate-pair','😀','"\\ud83d\\ude00"','string-codec');
vector('V05-exact-limit',event,JSON.stringify(event)+' '.repeat(16384-Buffer.byteLength(JSON.stringify(event))));

for(const letter of ['n','t','b','f','r']){
 const value=String.fromCharCode(92)+letter;
 vector('V04-literal-backslash-'+letter,value,JSON.stringify(value),'string-codec');
}
for(const length of [1,2,3,4]){
 const value=String.fromCharCode(92).repeat(length)+'n'+String.fromCharCode(10)+String.fromCharCode(92).repeat(length)+'t'+String.fromCharCode(9);
 vector('V04-mixed-backslashes-'+length,value,JSON.stringify(value),'string-codec');
}
save('canonical-cases.json',vectors);
const invalid=[];
function bad(id,raw,status,code='INVALID_REQUEST'){invalid.push({id,inputBase64:Buffer.from(raw).toString('base64'),expectedStatus:status,expectedCode:code});}
bad('V05-oversize',JSON.stringify(event)+' '.repeat(16385-Buffer.byteLength(JSON.stringify(event))),413);
bad('V05-duplicate',JSON.stringify(event).replace('"schemaVersion":1','"schemaVersion":1,"schemaVersion":1'),400);
bad('V05-trailing',JSON.stringify(event)+' {}',400);
bad('V05-old-version',JSON.stringify({...event,accessVersion:1}),400);
bad('V05-unknown-plus-schema',JSON.stringify({...event,schemaVersion:2,extra:true}),400);
bad('V05-schema',JSON.stringify({...event,schemaVersion:2}),422,'UNSUPPORTED_CONTRACT');
bad('V05-float-schema',JSON.stringify(event).replace('"schemaVersion":1','"schemaVersion":1.0'),400);
bad('V05-lone-surrogate',JSON.stringify({...event,confirmedSessionId:'\ud800'}),400);
bad('V05-invalid-utf8',Buffer.concat([Buffer.from('{"'),Buffer.from([0xff]),Buffer.from('":1}')]),400);
bad('V05-null-missing',JSON.stringify(Object.fromEntries(Object.entries(event).filter(([k])=>k!=='confirmedSessionId'))),400);
bad('V05-date-no-millis',JSON.stringify({...event,occurredAt:'2026-10-14T00:00:00Z'}),400);
bad('V05-invalid-date',JSON.stringify({...event,occurredAt:'2026-02-30T00:00:00.000Z'}),400);
bad('V05-old-reason',JSON.stringify({...event,reason:'PAYMENT_REFUNDED'}),422,'UNSUPPORTED_CONTRACT');
save('invalid-event-cases.json',invalid);
const pair=generateKeyPairSync('rsa',{modulusLength:2048});
const next=generateKeyPairSync('rsa',{modulusLength:2048});
const jwk=(p,kid)=>({...p.publicKey.export({format:'jwk'}),kid,alg:'RS256',use:'sig',key_ops:['verify']});
save('public-jwks.json',{current:{keys:[jwk(pair,'fake-current')]},overlap:{keys:[jwk(pair,'fake-current'),jwk(next,'fake-next')]},next:{keys:[jwk(next,'fake-next')]}});
save('public-current.pem',pair.publicKey.export({type:'spki',format:'pem'}));
save('public-next.pem',next.publicKey.export({type:'spki',format:'pem'}));
const claims={iss:'urn:tosunsaeng:workload:billing:test',sub:'billing-service',aud:'tosunsaeng-learning-core:entitlement-access-events:test',iat:epoch,exp:epoch+60,jti:uid(4)};
const tokens=[];
function token(id,change={},expected=204,opt={}){
 const payload=opt.raw??JSON.stringify({...claims,...change});
 const header=opt.header??{alg:'RS256',typ:'JWT',kid:opt.next?'fake-next':'fake-current'};
 const input=Buffer.from(JSON.stringify(header)).toString('base64url')+'.'+Buffer.from(payload).toString('base64url');
 let jwt=input+'.'+sign('RSA-SHA256',Buffer.from(input),(opt.next?next:pair).privateKey).toString('base64url');
 if(opt.tamper){const a=jwt.split('.');const b=Buffer.from(a[2],'base64url');b[0]^=1;jwt=a[0]+'.'+a[1]+'.'+b.toString('base64url');}
 tokens.push({id,now:opt.now??now,keySet:opt.keySet??'current',token:jwt,expectedAuthStatus:expected});return jwt;
}
const normal=token('V06-normal');
token('V06-wrong-sub',{sub:'identity-service'},403);
token('V06-wrong-aud',{aud:'wrong-purpose'},403);
token('V06-wrong-issuer',{iss:'urn:tosunsaeng:workload:billing:production'},401);
token('V06-ttl61',{exp:epoch+61},401);token('V06-ttl0',{exp:epoch},401);
token('V06-exp-boundary',{},401,{now:'2026-10-14T00:01:30.000Z'});
token('V06-exp-before',{},204,{now:'2026-10-14T00:01:29.999Z'});
token('V06-future30',{iat:epoch+30,exp:epoch+90});token('V06-future31',{iat:epoch+31,exp:epoch+91},401);
token('V06-nbf-valid',{nbf:epoch});token('V06-nbf-invalid',{nbf:epoch-1},401);
token('V06-extra-claim',{test:{value:'한글'}});token('V06-unknown-kid',{},401,{header:{alg:'RS256',typ:'JWT',kid:'unknown'}});
token('V06-extra-header',{},401,{header:{alg:'RS256',typ:'JWT',kid:'fake-current',jku:'https://invalid.example/jwks'}});
token('V06-tampered',{},401,{tamper:true});
token('V06-array-aud',{aud:[claims.aud]},401);
token('V06-fraction-iat',{},401,{raw:JSON.stringify(claims).replace(String(epoch),String(epoch)+'.0')});
token('V06-duplicate-claim',{},401,{raw:JSON.stringify(claims).replace('"iat":','"sub":"billing-service","iat":')});
token('V06-next-unknown',{},401,{next:true});token('V06-rotation-overlap',{},204,{next:true,keySet:'overlap'});
token('V06-retired-key',{},401,{keySet:'next'});

token('V06-exp-minus-1ns',{},204,{now:'2026-10-14T00:01:29.999999999Z'});
token('V06-exp-exact-ns',{},401,{now:'2026-10-14T00:01:30.000000000Z'});
token('V06-exp-plus-1ns',{},401,{now:'2026-10-14T00:01:30.000000001Z'});
for(const [suffix,time,expected] of [
 ['minus-1ns','2026-10-13T23:59:59.999999999Z',401],
 ['exact','2026-10-14T00:00:00.000000000Z',204],
 ['plus-1ns','2026-10-14T00:00:00.000000001Z',204]]){
 token('V06-iat-skew-'+suffix,{iat:epoch+30,exp:epoch+90},expected,{now:time});
 token('V06-nbf-skew-'+suffix,{nbf:epoch+30},expected,{now:time});
}
save('clock-cases.json',[
 {id:'expiry-minus-1ns',now:'2026-10-14T00:01:29.999999999Z',expectedEpochNanos:String(BigInt(epoch+90)*1000000000n-1n)},
 {id:'expiry-exact',now:'2026-10-14T00:01:30.000000000Z',expectedEpochNanos:String(BigInt(epoch+90)*1000000000n)},
 {id:'expiry-plus-1ns',now:'2026-10-14T00:01:30.000000001Z',expectedEpochNanos:String(BigInt(epoch+90)*1000000000n+1n)},
 {id:'skew-minus-1ns',now:'2026-10-13T23:59:59.999999999Z',expectedEpochNanos:String(BigInt(epoch)*1000000000n-1n)},
 {id:'skew-plus-1ns',now:'2026-10-14T00:00:00.000000001Z',expectedEpochNanos:String(BigInt(epoch)*1000000000n+1n)}
]);
save('token-cases.json',tokens);
const route='/internal/v1/entitlements/access/events';
save('http-cases.json',[
 {id:'V06-http-on',enabled:true,port:'workload',method:'POST',path:route,authorization:'Bearer '+normal,query:null,expectedStatus:204},
 ...['bearer ','Bearer  ',' Bearer ','Bearer\t'].map((prefix,i)=>({id:'V06-bearer-'+i,enabled:true,port:'workload',method:'POST',path:route,authorization:prefix+normal,query:null,expectedStatus:401})),
 ...[null,''].map((q,i)=>({id:'V06-off-'+i,enabled:false,port:'workload',authorization:null,query:q,expectedStatus:404})),
 {id:'V06-query-empty',enabled:true,port:'workload',method:'POST',path:route,authorization:'Bearer '+normal,query:'',expectedStatus:401},
 {id:'V06-method',enabled:true,port:'workload',method:'GET',path:route,authorization:'Bearer '+normal,query:null,expectedStatus:403},
 {id:'V06-wrong-port',enabled:true,port:'public',expectedStatus:404},
 {id:'V06-no-listener',enabled:true,port:'unbound',expectedTransport:'CONNECTION_FAILURE'},
 {id:'V06-other-chain',enabled:true,port:'workload',path:'/internal/v1/identity/events',expectedDispatch:'NOT_OWNED_BY_REVOKE_CHAIN'}
]);
const req={userId:uid(2),sessionId:'session-new',mockExamId:'mock-a'};
const old={previousAttemptGroupId:uid(3),previousSessionId:'session-old',startReason:'RESTART_INCOMPLETE'};
const resp={operationId:uid(5),reservationId:uid(6),reservationKind:'INITIAL',reservationStatus:'RESERVED',attemptGroupId:uid(7),sessionId:'session-new',mockExamId:'mock-a',expiresAt:'2026-10-14T00:05:00.000Z',entitlementSource:'SUBSCRIPTION'};
save('wire-examples.json',[
 {id:'S01-initial',request:req,response:resp},
 {id:'S02-replacement',request:{...req,restartContext:old},response:{...resp,reservationKind:'REPLACEMENT',attemptGroupId:uid(3),entitlementSource:'EXAM_PASS'}},
 {id:'S02-switch',request:{...req,restartContext:old},response:{...resp,transition:{previousAttemptGroupId:uid(3),previousSessionId:'session-old'}}},
 ...['NEW_AFTER_SUBMISSION','NEW_AFTER_COMPLETION'].map((reason,i)=>({id:i?'S04-completed':'S03-submitted',request:{...req,mockExamId:'mock-b',restartContext:{...old,startReason:reason}},response:{...resp,mockExamId:'mock-b',transition:{previousAttemptGroupId:uid(3),previousSessionId:'session-old'}}})),
 {id:'S04-same-mock-fallback',request:{...req,restartContext:{...old,startReason:'NEW_AFTER_SUBMISSION'}},response:{...resp,transition:{previousAttemptGroupId:uid(3),previousSessionId:'session-old'}}}
]);
save('status-examples.json',[
 {id:'S13-revoked',request:{userId:uid(2),operationId:uid(5)},response:{...resp,reservationStatus:'CANCELED',terminalAt:now,confirmRejection:{code:'ENTITLEMENT_REVOKED'}}},
 {id:'S13-canceled',request:{userId:uid(2),operationId:uid(5)},response:{...resp,reservationStatus:'CANCELED',terminalAt:now}},
 {id:'S14-confirmed-replay',request:{userId:uid(2),operationId:uid(5)},response:{...resp,reservationStatus:'CONFIRMED',attemptGroupStatus:'OPEN',terminalAt:now}}
]);
// Concrete state scenarios: these are inputs/expectations, NOT runtime or transaction tests.
const scenarios=[];
function scenario(id,given,when,then){scenarios.push({id,given,when,then,execution:'NOT_EXECUTED_REQUIRES_IMPLEMENTATION'});}
scenario('S01-legacy',{legacySession:'legacy-1',billingLink:null},['new request','paid INITIAL confirm'],{oldConsumed:false,restoredUnits:0,newGroup:uid(7)});
scenario('S02-incomplete',{reason:'RESTART_INCOMPLETE',oldMock:'mock-a',activePaid:false,freeGroupUsable:true},['reserve'],{kind:'REPLACEMENT',mock:'mock-a',additionalConsumption:0});
scenario('S03-exclude',{reason:'NEW_AFTER_SUBMISSION',previous:'mock-a',eligible:[{id:'mock-a',count:0,sequence:1},{id:'mock-b',count:1,sequence:2}]},['select'],{mock:'mock-b',kind:'INITIAL'});
scenario('S03-empty-alternative',{previous:'mock-a',eligible:['mock-a']},['select after submission'],{mock:'mock-a',kind:'INITIAL'});
scenario('S03-catalog-error',{catalogFailure:true},['select after submission'],{fallbackAllowed:false});
scenario('S04-open',{reason:'NEW_AFTER_SUBMISSION',billingState:'OPEN',selectedMock:'mock-a'},['reserve'],{kind:'INITIAL',reuseOldConsumption:false});
scenario('S05-tracking-off',{tracking:false,marker:null,receiptExpired:true},['classify'],{inferIncomplete:false,requiresProof:true});
scenario('S06-prepare-first',{required:[1,2,3],accepted:[1,2]},['prepare operation','accept answer 3','replay same key'],{reason:'RESTART_INCOMPLETE',mockUnchanged:true});
scenario('S06-submit-first',{required:[1,2,3],accepted:[1,2]},['accept answer 3','prepare operation','catalog changes','replay same key'],{reason:'NEW_AFTER_SUBMISSION',mockUnchanged:true});
scenario('S07-replay',{savedSuccess:true,currentSessionChanged:true,sameDigest:true,ownerAllowed:true},['replay'],{reproduceSaved:true,staleConflict:false});
scenario('S07-owner-lost',{savedSuccess:true,ownerAllowed:false},['replay'],{allow:false});
scenario('S08-invalid-combination',{reason:'NEW_AFTER_COMPLETION',responseKind:'REPLACEMENT'},['validate response'],{category:'CONTRACT_ERROR'});
scenario('S09-restore',{source:'TRIAL_CLAIM',consumption:'fake-consumption',restored:false},['two concurrent paid confirms','late old completion'],{restoredUnits:1,oldGroupBlocked:true});
scenario('S09-no-right',{oldSessionOpen:true,newRightAvailable:false},['reserve new source'],{oldSessionTerminated:false,newGrant:0});
scenario('S09-no-rerestore',{source:'PAID_SWITCH_RESTORATION'},['switch to paid'],{restoredUnits:0});
scenario('S10-finalize-retry',{billingConfirmed:true,lcFinalized:false},['LC transaction fails','worker retries'],{duplicateGrant:0,ttlBeforeFinalization:false,guardUntilFinalization:true,publicSuccessAfterCommit:true});
scenario('S11-summary',{acceptedBeforeClose:true,summaryJobExists:false},['close source','create summary','retry summary'],{allowDerived:true,allowNewUserSubmission:false});
scenario('S12-partial',{required:[1,2,3],accepted:[1,2]},['close source','finish accepted jobs'],{completed:false});
scenario('S12-late-old',{oldGroup:uid(3),newGroup:uid(7),newGuard:true},['old completion event'],{newGuard:true});
scenario('S13-reject',{status:'CANCELED',terminalAt:now,confirmRejection:{code:'ENTITLEMENT_REVOKED'}},['cancel response omits reason','query status'],{publicCode:'ENTITLEMENT_REVOKED'});
scenario('S14-command-ttl',{commandExists:false,durableReceipt:{result:'CONFIRMED',digest:'fake-digest'}},['same caller owner digest replay'],{reproduce:'CONFIRMED',newConsumption:0});
scenario('S16-manifest',{manifest:['legacy-1'],session:'new-1',source:null},['read new session'],{grandfather:false});
scenario('V01-inbox-ttl',{inboxExpired:true,groupFact:event},['retry same id and canonical body'],{http:204});
scenario('V02-new-id',{groupFact:event},[{...event,eventId:uid(8)}],{http:409,code:'GROUP_REVOKE_CONFLICT'});
scenario('V02-event-conflict',{groupFact:event},[{...event,reason:'PURCHASE_REVOKED'}],{http:409,code:'EVENT_ID_CONFLICT'});
scenario('V03-money-correction',{groupFact:event},['extra refund monetary observation'],{newRevoke:false,protectedSession:null});
save('state-scenarios.json',scenarios);
const source='docs/contracts/LC-BILLING-PAID-EXAM-CONTRACT-R2.md';
save('manifest.json',{revision,contractRevision:'lc-billing-paid-exam-contract-r2-draft-2',contractSha256:hash(readFileSync(source)),fixedNow:now,environment:'test',scope:'OFFLINE_ONLY',runtimeTested:false,lcIndependentValidation:false,deferred:['S15','V07'],included:[...Array.from({length:14},(_,i)=>'S'+String(i+1).padStart(2,'0')),'S16',...Array.from({length:6},(_,i)=>'V'+String(i+1).padStart(2,'0'))],privateKeysWritten:false,previousRevision:'lc-billing-paid-exam-r2-fixtures-1',changes:['Scalar-wise canonical escaping with literal-backslash regressions','Exact nanosecond clock and expiry/iat/nbf boundaries']});
save('README.md',`# ${revision}\n\nFake offline fixture package for contract R2 draft-2. No live credentials or requests.\n\n- canonical-cases and invalid-event-cases: raw input base64, expected normalized bytes/hash or rejection. String-codec cases test escaping only, not domain identifiers.\n- token-cases: ephemeral RSA test signatures and fixed now. Private keys were never persisted. 204 means token accepted for this route; it is not an executed HTTP response.\n- http-cases: expected routing/auth outcomes, NOT executed against a server.\n- wire/status examples: concrete proposed DTO examples, NOT calls to the old runtime.\n- state-scenarios: concrete state inputs and expectations requiring future service/transaction tests; NOT proof of concurrency or business correctness.\n- S15/V07 deferred. Existing A fixtures unchanged. LC independent verification has not run.\n\nRun from repository root: python3 scripts/contracts/verify-paid-exam-fixtures-r2.py\nDo not regenerate accepted bytes. Generator rejects an existing output directory. New content needs a new revision and hashes.\n`);
writeFileSync(`${dir}/SHA256SUMS`,files.sort().map(f=>hash(readFileSync(`${dir}/${f}`))+'  '+f).join('\n')+'\n');
console.log(JSON.stringify({revision,files:files.length,canonical:vectors.length,invalid:invalid.length,tokens:tokens.length,stateScenarios:scenarios.length}));
