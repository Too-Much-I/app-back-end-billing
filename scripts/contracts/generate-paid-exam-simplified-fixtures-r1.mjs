// Fake offline vectors only. Never overwrite a published fixture revision.
import {readFileSync,writeFileSync,mkdirSync,existsSync} from 'node:fs';
import {createHash} from 'node:crypto';
const revision='lc-billing-paid-exam-simplified-exact-r1-fixtures-1';
const dir='src/test/resources/contracts/'+revision;
if(existsSync(dir))throw Error('Revision exists; use a new revision');
const contractPath='docs/contracts/LC-BILLING-PAID-EXAM-SIMPLIFIED-EXACT-R1.md';
const contract=readFileSync(contractPath);
if(!contract.includes('lc-billing-paid-exam-simplified-exact-r1-draft-3'))throw Error('Wrong contract');
mkdirSync(dir,{recursive:true});const files=[];
const hash=x=>createHash('sha256').update(x).digest('hex');
const save=(n,v)=>{writeFileSync(dir+'/'+n,typeof v==='string'||Buffer.isBuffer(v)?v:JSON.stringify(v,null,2)+'\n');files.push(n)};
save('contract-snapshot.md',contract);
const errors=[];
const body={code:'MEMBER_EVIDENCE_PENDING',message:'The operation is waiting for member evidence and request resumption.',retryable:true,correlationId:'fixture-correlation-a'};
function err(id,patch,expected,raw){const v={endpoint:'reserve',http:503,retryAfter:'1',rawBodyBase64:Buffer.from(raw??JSON.stringify(body)).toString('base64'),...patch};errors.push({id,input:v,expected});}
err('E01-reserve-member',{},'WAITING_MEMBER');err('E01-status-member',{endpoint:'status'},'WAITING_MEMBER');
err('E01-correlation-changed',{},'WAITING_MEMBER',JSON.stringify({...body,correlationId:'fixture-b'}));
for(const [id,raw] of [
 ['missing',JSON.stringify({code:body.code,retryable:true,correlationId:'x'})],
 ['unknown',JSON.stringify({...body,extra:1})],['string-bool',JSON.stringify({...body,retryable:'true'})],
 ['null-message',JSON.stringify({...body,message:null})],['array','[]'],['duplicate',JSON.stringify(body).replace('"retryable":true','"retryable":true,"retryable":true')],
 ['trailing',JSON.stringify(body)+' {}'],['surrogate',JSON.stringify({...body,message:'\ud800'})]])err('E06-'+id,{},'CONTRACT_ERROR',raw);
err('E06-invalid-utf8',{rawBodyBase64:Buffer.from([255]).toString('base64')},'CONTRACT_ERROR');
err('E06-wrong-http',{http:409},'CONTRACT_ERROR');err('E06-wrong-route',{endpoint:'cancel'},'CONTRACT_ERROR');
err('E06-wrong-retryable',{},'CONTRACT_ERROR',JSON.stringify({...body,retryable:false}));
// Retry-After transport enforcement is outside this category model; not invented by the oracle.
const terminal={code:'RESERVE_REJECTED_BEFORE_CREATION',message:'The reserve operation was rejected before reservation creation.',retryable:false,correlationId:'x'};
err('E06-terminal-status',{endpoint:'status',http:409,retryAfter:null},'PRE_RESERVE_TERMINAL',JSON.stringify(terminal));
err('E06-terminal-reserve',{http:409,retryAfter:null},'CONTRACT_ERROR',JSON.stringify(terminal));
err('E06-terminal-true',{endpoint:'status',http:409,retryAfter:null},'CONTRACT_ERROR',JSON.stringify({...terminal,retryable:true}));
err('E06-owner',{endpoint:'status',http:409,retryAfter:null},'OWNER_BLOCKED',JSON.stringify({...terminal,code:'OPERATION_OWNER_MISMATCH'}));
err('E06-notfound',{endpoint:'status',http:404,retryAfter:null},'OPERATION_NOT_FOUND',JSON.stringify({...terminal,code:'OPERATION_NOT_FOUND'}));
err('E06-empty404',{endpoint:'status',http:404,retryAfter:null},'CONTRACT_ERROR','');
err('E06-temporary',{endpoint:'status'},'TEMPORARY_FAILURE',JSON.stringify({...body,code:'BILLING_TEMPORARILY_UNAVAILABLE'}));
save('error-cases.json',errors);
const publicCases=[
 ['member',503,'BILLING_TEMPORARILY_UNAVAILABLE','시험 사용권을 일시적으로 확인할 수 없습니다.','1'],
 ['delete-during-creation',409,'EXAM_CREATION_PROCESSING','시험 준비 중입니다. 잠시 후 다시 시도해 주세요.','1'],
 ['creation-processing',409,'EXAM_CREATION_PROCESSING','시험 생성이 처리 중입니다. 같은 요청으로 다시 시도해 주세요.','1'],
 ['deleted-replay',404,'EXAM_4004','해당 모의고사 세션을 찾을 수 없습니다.',null]
].map(([kind,http,code,message,retryAfter])=>({id:'PUBLIC-'+kind,kind,http,retryAfter,rawBodyBase64:Buffer.from(JSON.stringify({isSuccess:false,code,message})).toString('base64')}));save('public-error-examples.json',publicCases);
const clocks=[];
for(const [label,start]of [['normal','2026-10-14T00:00:00.000Z'],['leap','2028-02-29T23:59:59.999Z'],['year','2026-12-31T23:59:59.999Z']]){
 const end=Date.parse(start)+120*86400000;
 for(const delta of [-1,0,1])clocks.push({id:`T01-${label}-${delta}`,firstAcceptedAt:start,now:new Date(end+delta).toISOString(),expectedGuaranteeUntil:new Date(end).toISOString(),expectedWithin:delta<0});
}
const terminalAt='2026-10-20T01:02:03.004Z',due=Date.parse(terminalAt)+120*86400000;
for(const [suffix,delta,unresolved,hold,eligible]of [['before',-1,false,false,false],['exact',0,false,false,true],['after',1,false,false,true],['unresolved',1,true,false,false],['hold',1,false,true,false]])clocks.push({id:'T03-'+suffix,terminalAt,now:new Date(due+delta).toISOString(),unresolved,hold,expectedPurgeEligible:eligible,scope:'DETAIL_RECEIPT_ONLY_NOT_MINIMUM_EVIDENCE'});
save('time-cases.json',clocks);
const base={phase:'PREPARED',intent:'WAITING_MEMBER',quarantine:'NONE',reason:null,ownerAllowed:true,authAllowed:true,leaseValid:true,versionMatches:true,ownsGuard:true,newSessionExists:false,commitMarker:false,reservationKnown:false,cancelSent:false,cancelUnknown:false,withinWindow:true,memberWaitPersisted:true,exhaustionLinked:false,mixedCause:false,remote:'MEMBER_PENDING'};
const resumes=[];function resume(id,patch,expected){resumes.push({id,input:{...base,...patch},expected,scope:'HTTP_RESUME_DECISION_MODEL_WORKER_NEVER_RESERVES'})}
resume('E02-a-ready',{},'HTTP_RESUME_ALLOWED');resume('E02-e-cleanup',{intent:'CLEANUP_PRECOMMIT'},'HTTP_RESUME_ALLOWED');
resume('E02-f-review-evidence',{intent:'CLEANUP_PRECOMMIT',quarantine:'NEEDS_REVIEW',reason:'retry_exhausted',exhaustionLinked:true},'HTTP_RESUME_ALLOWED');
resume('E02-f-review-reason-only',{quarantine:'NEEDS_REVIEW',reason:'retry_exhausted',memberWaitPersisted:false},'REVIEW_REQUIRED');
resume('E02-f-no-link',{quarantine:'NEEDS_REVIEW',reason:'retry_exhausted'},'REVIEW_REQUIRED');
resume('E02-f-mixed',{quarantine:'NEEDS_REVIEW',reason:'retry_exhausted',exhaustionLinked:true,mixedCause:true},'REVIEW_REQUIRED');
resume('E02-f-other-reason',{quarantine:'NEEDS_REVIEW',reason:'state_inconsistent',exhaustionLinked:true},'REVIEW_REQUIRED');
resume('E02-f-owner',{ownerAllowed:false},'OWNER_BLOCKED');resume('E02-f-auth',{authAllowed:false},'AUTH_BLOCKED');
resume('E02-f-blocked-owner',{quarantine:'BLOCKED_OWNER',exhaustionLinked:true},'OWNER_BLOCKED');resume('E02-f-blocked-auth',{quarantine:'BLOCKED_AUTH',exhaustionLinked:true},'AUTH_BLOCKED');
for(const field of ['leaseValid','versionMatches','ownsGuard'])resume('E02-d-'+field,{[field]:false},'RELOAD_REQUIRED');
for(const field of ['newSessionExists','commitMarker','reservationKnown','cancelSent','cancelUnknown'])resume('E02-e-'+field,{[field]:true},'REVIEW_REQUIRED');
for(const phase of ['RESERVED','SESSION_COMMITTED','CANCEL_PENDING','SUCCEEDED'])resume('E02-e-phase-'+phase,{phase},'EXISTING_STATE_RECOVERY');
resume('E02-h-expired',{withinWindow:false},'REVIEW_REQUIRED');resume('E02-b-no-evidence',{memberWaitPersisted:false},'REVIEW_REQUIRED');
for(const remote of ['OPERATION_NOT_FOUND','EMPTY404','RESERVED','CONFIRMED'])resume('E02-b-remote-'+remote,{remote},'STATUS_RECOVERY_REQUIRED');
save('member-resume-cases.json',resumes);
const request={continuationReason:'PHONE_REJOIN',continuationId:'00000000-0000-4000-8000-000000000001',expectedAttemptGroupId:'00000000-0000-4000-8000-000000000002',sessionId:'fixture-session-new',mockExamId:'fixture-mock'};
const paid={reservationKind:'INITIAL',entitlementSource:'SUBSCRIPTION',attemptGroupId:'00000000-0000-4000-8000-000000000003',sessionId:request.sessionId,mockExamId:request.mockExamId,continuationReason:request.continuationReason,continuationId:request.continuationId};
const phone=[];function ph(id,snapshot,valid=true,status=snapshot){phone.push({id,request,reserveSnapshot:snapshot,statusSnapshot:status,expectedValid:valid,scope:'SELECTED_IMMUTABLE_FIELDS_ONLY_NOT_FULL_DTO'})}
ph('P01-paid',paid);ph('P02-free',{...paid,reservationKind:'REPLACEMENT',entitlementSource:'EXAM_PASS',attemptGroupId:request.expectedAttemptGroupId});
for(const [name,patch]of Object.entries({oldgroup:{attemptGroupId:request.expectedAttemptGroupId},source:{entitlementSource:'EXAM_PASS'},mock:{mockExamId:'different'},session:{sessionId:'different'},echo:{continuationId:'different'},transition:{transition:{}},nulltransition:{transition:null}}))ph('P05-'+name,{...paid,...patch},false);
const missing={...paid};delete missing.continuationReason;ph('P05-missing-echo',missing,false);ph('P05-status-changed',paid,false,{...paid,mockExamId:'different'});save('phone-snapshot-cases.json',phone);
const stateRows=[...contract.toString().matchAll(/^\| ((?:E\d\d(?:-[a-h])?|T\d\d|P\d\d|C\d\d)) \|\s*(.+) \|$/gm)].map(m=>({id:m[1],requirement:m[2],execution:'NOT_EXECUTED_REQUIRES_IMPLEMENTATION'}));
if(stateRows.length!==28)throw Error('Unexpected coverage count '+stateRows.length);
save('state-scenarios.json',stateRows);
save('README.md',`# ${revision}\n\nFake offline fixtures only. No keys, tokens, user data, HTTP, databases or runtime execution.\n\ncontract-snapshot.md is the exact draft-3 input. Error/resume vectors are pure decision models; phone vectors project selected immutable fields, not full wire DTOs. Time vectors are UTC milliseconds. State scenarios (28, including E02 parent and eight children) are NOT executed.\n\nRun from repository root: python3 scripts/contracts/verify-paid-exam-simplified-fixtures-r1.py\nPass a fixture folder as optional argument to verify an unpacked package. Verify SHA256SUMS separately. LC must implement its own independent oracle; rerunning this verifier is not LC independent validation.\n\nExcluded: new deletion route/auth/old-owner application, final per-record retention/purge and real worker/lease/Transaction/races. No final deployment approval.\n`);
save('manifest.json',{revision,contractRevision:'lc-billing-paid-exam-simplified-exact-r1-draft-3',contractSha256:hash(contract),contractPath,counts:{errors:errors.length,publicExamples:publicCases.length,time:clocks.length,memberResume:resumes.length,phone:phone.length,stateScenarios:stateRows.length},runtimeTested:false,httpExecuted:false,lcIndependentValidation:false,deferred:['deletion-route-auth','historical-owner-deletion','final-record-retention-purge'],testData:'SYNTHETIC_ONLY'});
save('SHA256SUMS',files.map(n=>hash(readFileSync(dir+'/'+n))+'  '+n).join('\n')+'\n');
console.log(JSON.stringify({revision,files:files.length,contractSha256:hash(contract)}));
