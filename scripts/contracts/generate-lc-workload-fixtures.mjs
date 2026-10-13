// Offline test artifacts only. Private keys exist only in memory and are never written.
import { generateKeyPairSync, sign, createHash } from 'node:crypto';
import { writeFileSync, mkdirSync } from 'node:fs';
const dir = 'src/test/resources/contracts/lc-billing-workload-auth-r1';
mkdirSync(dir, { recursive: true });
const keys = {};
for (const id of ['lc', 'identity', 'user', 'next']) {
  const pair = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const kid = id === 'next' ? 'fake-next' : 'fake-shared-kid';
  const jwk = { ...pair.publicKey.export({ format: 'jwk' }), kid, alg: 'RS256', use: 'sig', key_ops: ['verify'] };
  keys[id] = { ...pair, kid, jwk };
}
const now = 1791849600; // 2026-10-13T00:00:00Z
const iss = id => `urn:tosunsaeng:workload:${id === 'lc' ? 'learning-core' : id}:test`;
const claims = id => ({ iss: iss(id), sub: id === 'lc' ? 'learning-core-service' : `${id}-service`, aud: id === 'lc' ? 'tosunsaeng-billing:reservations:test' : 'tosunsaeng-billing:withdrawal-events:test', iat: now, exp: now + 60, jti: '11111111-1111-4111-8111-111111111111' });
const enc = v => Buffer.from(v).toString('base64url');
function token(raw, key='lc', header) {
  const input = enc(header ?? JSON.stringify({ alg:'RS256',typ:'JWT',kid:keys[key].kid })) + '.' + enc(raw);
  return input + '.' + sign('RSA-SHA256', Buffer.from(input), keys[key].privateKey).toString('base64url');
}
const cases=[];
function add(id, change={}, valid=true, options={}) {
  const c={...claims(options.service ?? 'lc'),...change};
  for(const field of options.omit ?? []) delete c[field];
  const raw=options.raw ?? JSON.stringify(c);
  const jwt=options.token ?? token(raw,options.key ?? options.service ?? 'lc',options.header);
  cases.push({id,group:options.group ?? 'AUTH',verifier:options.verifier ?? options.service ?? 'lc',keySet:options.keySet ?? 'current',now:options.now ?? '2026-10-13T00:00:00Z',token:jwt,valid,...(valid?{subject:c.sub,audience:c.aud}:{})});
  return jwt;
}
const normal=add('lc-normal');
const identity=add('identity-normal',{},true,{service:'identity'});
add('attempt-purpose',{aud:'tosunsaeng-billing:attempt-group-events:test'});
add('wrong-sub-authenticated',{sub:'wrong-service'}); // Invalidation belongs to authorization, not codec.
add('wrong-aud-authenticated',{aud:'wrong-purpose'});
for(const ttl of [0,1,60,61]) add(`ttl-${ttl}`,{exp:now+ttl},ttl>=1&&ttl<=60);
for(const offset of [30,31]) add(`iat-${offset}`,{iat:now+offset,exp:now+offset+60},offset===30);
for(const offset of [-1,0,30,31,60]) add(`nbf-${offset}`,{nbf:now+offset},offset>=0&&offset<=30);
add('expiry-before-boundary',{},true,{now:'2026-10-13T00:01:29.999999999Z'});
add('expiry-at-boundary',{},false,{now:'2026-10-13T00:01:30Z'});
add('fractional-future-iat',{iat:now+31,exp:now+91},false,{now:'2026-10-13T00:00:00.999999999Z'});
for(const field of ['iat','exp','nbf']) for(const [label,value] of [['string',`"${now}"`],['float',`${now}.0`],['exponent',`${now}e0`],['negative','-1'],['overflow','9223372036854775808'],['null','null']]) {
  const c={...claims('lc'),nbf:now};
  add(`${field}-${label}`,{},false,{raw:JSON.stringify(c).replace(`"${field}":${now+(field==='exp'?60:0)}`,`"${field}":${value}`)});
}
for(const field of ['iss','sub','aud','iat','exp','jti']) add(`missing-${field}`,{},false,{omit:[field]});
add('array-aud',{aud:['tosunsaeng-billing:reservations:test']},false);
add('uppercase-jti',{jti:'AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA'},false);
add('non-v4-jti',{jti:'11111111-1111-1111-8111-111111111111'},false);
add('duplicate-claim',{},false,{raw:JSON.stringify(claims('lc')).replace('{','{"iat":1,')});
add('trailing-json',{},false,{raw:JSON.stringify(claims('lc'))+' {}'});
add('invalid-utf8',{},false,{raw:Buffer.from([0xff])});
add('oversize',{},false,{raw:JSON.stringify({...claims('lc'),extra:'x'.repeat(17000)})});
add('additional-claims',{scope:'admin',roles:['identity-service'],userId:'22222222-2222-4222-8222-222222222222',unknown:{values:[1,null,true]}});
add('duplicate-extra-field',{},false,{raw:JSON.stringify(claims('lc')).replace('{','{"extra":{"x":1,"x":2},')});
for(const [id,h] of [['extra-header',{alg:'RS256',typ:'JWT',kid:keys.lc.kid,jku:'https://example.invalid'}],['wrong-alg',{alg:'RS512',typ:'JWT',kid:keys.lc.kid}],['wrong-typ',{alg:'RS256',typ:'jwt',kid:keys.lc.kid}],['unknown-kid',{alg:'RS256',typ:'JWT',kid:'fake-unknown'}],['missing-kid',{alg:'RS256',typ:'JWT'}]]) add(id,{},false,{header:JSON.stringify(h)});
add('duplicate-header',{},false,{header:'{"alg":"RS256","alg":"RS256","typ":"JWT","kid":"fake-shared-kid"}'});
add('wrong-environment',{iss:iss('lc').replace(':test',':staging')},false);
add('wrong-issuer',{iss:'urn:untrusted'},false);
add('same-kid-wrong-key',{},false,{key:'identity'});
add('issuer-lc-identity-signature',{},false,{key:'identity'});
add('identity-token-lc-verifier',{},false,{token:identity});
add('lc-token-identity-verifier',{},false,{token:normal,verifier:'identity'});
const parts=normal.split('.');const sig=Buffer.from(parts[2],'base64url');sig[0]^=1;
const bad=parts.slice(0,2).join('.')+'.'+sig.toString('base64url');
add('tampered-signature',{},false,{token:bad});
for(const [id,jwt] of [['padding',normal+'='],['empty-segment',parts[0]+'..'+parts[2]],['extra-dot',normal+'.x'],['token-space',normal+' ']]) add(id,{},false,{token:jwt});
add('rotation-next-overlap',{},true,{key:'next',keySet:'overlap'});
add('rotation-old-removed',{},false,{keySet:'next'});
add('rotation-next-only',{},true,{key:'next',keySet:'next'});
const http=[];
function request(id,opts={},expected=401) { http.push({id,group:opts.group??'AUTH-10',identityEnabled:true,lcEnabled:true,target:'lc',method:'POST',path:'/internal/v1/reservations/status',localPort:8444,queryString:null,authorizationHeaderName:'Authorization',authorization:['Bearer '+normal],...opts,expectedStatus:expected,execution:'PENDING_B_LC_HTTP'}); }
for(const [id,header] of [['exact','Bearer '+normal],['lowercase','bearer '+normal],['uppercase','BEARER '+normal],['double-space','Bearer  '+normal],['tab','Bearer\t'+normal],['leading-space',' Bearer '+normal],['trailing-space','Bearer '+normal+' '],['empty','Bearer '],['comma','Bearer '+normal+', Bearer '+normal]]) request(`bearer-${id}`,{authorization:[header],authorizationHeaderName:id==='exact'?'aUtHoRiZaTiOn':'Authorization'},id==='exact'?null:401);
request('header-absent',{authorization:[]});request('header-duplicate',{authorization:['Bearer '+normal,'Bearer '+normal]});
for(const query of ['x=1','x=','userId=22222222-2222-4222-8222-222222222222','x=1&x=2','']) request('query-'+(query||'empty'),{group:'QUERY',queryString:query});
request('identity-to-lc',{group:'AUTH-08',authorization:['Bearer '+identity]},403);
request('lc-to-identity',{group:'AUTH-08',target:'identity',path:'/internal/v1/payments/accounts/withdrawal/events'},403);
request('identity-off-token-to-lc',{group:'AUTH-08',identityEnabled:false,authorization:['Bearer '+identity]},401);
request('lc-off-token-to-identity',{group:'AUTH-08',lcEnabled:false,target:'identity',path:'/internal/v1/payments/accounts/withdrawal/events'},401);
request('identity-target-off',{group:'AUTH-08',identityEnabled:false,target:'identity',path:'/internal/v1/payments/accounts/withdrawal/events'},404);
request('lc-target-off',{group:'AUTH-08',lcEnabled:false,authorization:['invalid'],queryString:'x=1'},404);
const identityParts=identity.split('.'); const identitySig=Buffer.from(identityParts[2],'base64url'); identitySig[0]^=1;
const badIdentity=identityParts.slice(0,2).join('.')+'.'+identitySig.toString('base64url');
request('cross-issuer-tampered',{group:'AUTH-08',authorization:['Bearer '+badIdentity]},401);
request('wrong-local-port',{group:'INGRESS',localPort:8082},404);
request('wrong-method',{group:'INGRESS',method:'GET'},403);
request('unknown-internal-path',{group:'INGRESS',path:'/internal/v1/unknown'},403);
request('both-off-no-connector',{group:'INGRESS',identityEnabled:false,lcEnabled:false,transport:'connection-refused'},null);
const jwks={lc:{keys:[keys.lc.jwk]},identity:{keys:[keys.identity.jwk]},user:{keys:[keys.user.jwk]},overlap:{keys:[keys.lc.jwk,keys.next.jwk]},next:{keys:[keys.next.jwk]}};
const files={'public-jwks.json':jwks,'token-cases.json':{revision:'lc-billing-workload-auth-r1-fixtures-1',cases},'http-cases.json':{revision:'lc-billing-workload-auth-r1-fixtures-1',notes:'null expectedStatus means normal request passes authentication or connector has no HTTP response; inspect transport/expectedStage. No LC runtime execution claimed.',cases:http.map(c=>({...c,expectedStage:c.id==='both-off-no-connector'?'NO_HTTP_RESPONSE':c.expectedStatus===null?'BUSINESS_AFTER_AUTH':'INGRESS_OR_AUTH_REJECTION'}))}};
let hashes='';
for(const [file,data] of Object.entries(files)) {const bytes=JSON.stringify(data,null,2)+'\n';writeFileSync(dir+'/'+file,bytes);hashes+=createHash('sha256').update(bytes).digest('hex')+'  '+file+'\n';}
writeFileSync(dir+'/SHA256SUMS',hashes);
console.log(`Generated ${cases.length} signed token cases and ${http.length} HTTP specification cases; private keys discarded.`);
