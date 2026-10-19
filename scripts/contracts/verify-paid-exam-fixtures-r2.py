"""Independent offline byte/token checks; does not implement service transactions or HTTP."""
import base64, datetime as dt, hashlib, json, re, subprocess, sys, tempfile
from pathlib import Path
from decimal import Decimal
D=Path(sys.argv[1]) if len(sys.argv)>1 else Path('src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-2')
def require(ok):
    if not ok: raise ValueError('fixture validation failed')
def b64(s): return base64.b64decode(s,validate=True)
def b64url(s):
    require(re.fullmatch(r'[A-Za-z0-9_-]+',s) is not None)
    return base64.urlsafe_b64decode(s+'='*((-len(s))%4))
def pairs(items):
    out={}
    for k,v in items:
        require(k not in out);out[k]=v
    return out
def scalar(v):
    if isinstance(v,str): require(not any(0xd800<=ord(c)<=0xdfff for c in v))
    elif isinstance(v,dict):
        for k,x in v.items(): scalar(k);scalar(x)
    elif isinstance(v,list):
        for x in v:scalar(x)
def parse(raw):
    v=json.loads(raw.decode('utf-8','strict'),object_pairs_hook=pairs,parse_float=Decimal,
                 parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
    scalar(v);return v
def load(f):return parse((D/f).read_bytes())
def canon(v):
    if v is None:return 'null'
    if type(v) is int:return str(v)
    if isinstance(v,str):
        out='"'
        for c in v:
            if ord(c)<32:out+='\\u%04x'%ord(c)
            elif c in ['"','\\']:out+='\\'+c
            else:out+=c
        return out+'"'
    if isinstance(v,dict):return '{'+','.join(canon(k)+':'+canon(x) for k,x in v.items())+'}'
    raise ValueError()
FIELDS=['eventId','eventType','schemaVersion','producer','consumerScopeId','occurredAt','userId','attemptGroupId','reason','confirmedSessionId']
UUID=r'[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}'
def event(raw):
    if len(raw)>16384:return 413,None
    try:
        v=parse(raw);require(isinstance(v,dict) and set(v)==set(FIELDS))
        require(type(v['schemaVersion']) is int)
        for k in ['eventId','attemptGroupId']:require(isinstance(v[k],str) and re.fullmatch(UUID,v[k]))
        require(isinstance(v['userId'],str) and re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}',v['userId']))
        for k in ['eventType','producer','consumerScopeId','reason','occurredAt']:require(isinstance(v[k],str))
        require(re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z',v['occurredAt']))
        dt.datetime.fromisoformat(v['occurredAt'].replace('Z','+00:00'))
        require(v['confirmedSessionId'] is None or isinstance(v['confirmedSessionId'],str) and 1<=len(v['confirmedSessionId'])<=128)
    except Exception:return 400,None
    if (v['schemaVersion']!=1 or v['producer']!='billing' or v['consumerScopeId']!='fixture-test'
        or v['eventType']!='AttemptGroupAccessRevoked' or v['reason'] not in ['PURCHASE_REFUNDED','PURCHASE_REVOKED']):return 422,None
    return 204,{k:v[k] for k in FIELDS}
def epoch(s):
    match=re.fullmatch(r'(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d{1,9}))?Z',s)
    require(match is not None)
    whole=dt.datetime.strptime(match[1],'%Y-%m-%dT%H:%M:%S')
    delta=whole-dt.datetime(1970,1,1)
    seconds=delta.days*86400+delta.seconds
    nanos=int((match[2] or '').ljust(9,'0'))
    return Decimal(seconds)+Decimal(nanos)/Decimal(1000000000)
keys=load('public-jwks.json')
def auth(c):
    try:
        require(len(c['token'].encode())<=16384)
        parts=c['token'].split('.');require(len(parts)==3)
        h=parse(b64url(parts[0]));v=parse(b64url(parts[1]));sig=b64url(parts[2])
        require(set(h)=={'alg','typ','kid'} and h['alg']=='RS256' and h['typ']=='JWT')
        jwks=keys[c['keySet']]['keys'];require(any(k['kid']==h['kid'] for k in jwks))
        pem={'fake-current':'public-current.pem','fake-next':'public-next.pem'}[h['kid']]
        with tempfile.TemporaryDirectory() as t:
            Path(t,'sig').write_bytes(sig);Path(t,'input').write_bytes((parts[0]+'.'+parts[1]).encode())
            result=subprocess.run(['openssl','dgst','-sha256','-verify',str(D/pem),'-signature',str(Path(t,'sig')),str(Path(t,'input'))],capture_output=True)
            require(result.returncode==0)
        require(all(k in v for k in ['iss','sub','aud','iat','exp','jti']))
        require(v['iss']=='urn:tosunsaeng:workload:billing:test')
        require(isinstance(v['sub'],str) and isinstance(v['aud'],str) and isinstance(v['jti'],str) and re.fullmatch(UUID,v['jti']))
        for k in ['iat','exp']+(['nbf'] if 'nbf' in v else []):require(type(v[k]) is int and 0<=v[k]<=2**63-1)
        now=epoch(c['now']);require(1<=v['exp']-v['iat']<=60 and v['iat']<=now+30 and now<v['exp']+30)
        if 'nbf' in v:require(v['iat']<=v['nbf']<v['exp'] and v['nbf']<=now+30)
    except Exception:return 401
    return 204 if v['sub']=='billing-service' and v['aud']=='tosunsaeng-learning-core:entitlement-access-events:test' else 403
# Match JWK public parameters to the DER SubjectPublicKeyInfo in PEM, independently.
def der(tag,data):
    n=len(data);length=bytes([n]) if n<128 else bytes([128+(n.bit_length()+7)//8])+n.to_bytes((n.bit_length()+7)//8,'big')
    return bytes([tag])+length+data
def integer(b):return der(2,(b'\0' if b[0]&128 else b'')+b)
for k in keys['overlap']['keys']:
    public=der(48,integer(b64url(k['n']))+integer(b64url(k['e'])))
    spki=der(48,bytes.fromhex('300d06092a864886f70d0101010500')+der(3,b'\0'+public))
    pem=(D/('public-current.pem' if k['kid']=='fake-current' else 'public-next.pem')).read_text()
    require(base64.b64decode(''.join(x for x in pem.splitlines() if not x.startswith('---')))==spki)
counts={}
for line in (D/'SHA256SUMS').read_text().splitlines():
    digest,name=line.split('  ');require(Path(name).name==name);require(hashlib.sha256((D/name).read_bytes()).hexdigest()==digest)
counts['hashFiles']=len((D/'SHA256SUMS').read_text().splitlines())
for c in load('canonical-cases.json'):
    raw=b64(c['inputBase64'])
    if c['kind']=='event':status,value=event(raw);require(status==204)
    else:value=parse(raw)
    out=canon(value).encode('utf-8');require(parse(out)==value);require(out==b64(c['expectedCanonicalBase64']));require(hashlib.sha256(out).hexdigest()==c['expectedSha256'])
counts['canonical']=len(load('canonical-cases.json'))
if (D/'clock-cases.json').exists():
    for c in load('clock-cases.json'):
        require(epoch(c['now'])*Decimal(1000000000)==Decimal(c['expectedEpochNanos']))
    counts['exactNanosecondClock']=len(load('clock-cases.json'))
for c in load('invalid-event-cases.json'):
    status,_=event(b64(c['inputBase64']));require(status==c['expectedStatus']);require(c['expectedCode']==('UNSUPPORTED_CONTRACT' if status==422 else 'INVALID_REQUEST'))
counts['invalid']=len(load('invalid-event-cases.json'))
for c in load('token-cases.json'):require(auth(c)==c['expectedAuthStatus'])
counts['tokens']=len(load('token-cases.json'))
# Structural cross-checks only, not a reserve/status runtime implementation.
for c in load('wire-examples.json'):
    q,a=c['request'],c['response'];require(q['sessionId']==a['sessionId'] and q['mockExamId']==a['mockExamId'])
    context=q.get('restartContext');transition=a.get('transition')
    if context:
        require(context['previousSessionId']!=q['sessionId'])
        if context['startReason']!='RESTART_INCOMPLETE':require(a['reservationKind']=='INITIAL')
        if a['reservationKind']=='INITIAL':require(transition=={k:context[k] for k in ['previousAttemptGroupId','previousSessionId']} and a['attemptGroupId']!=context['previousAttemptGroupId'])
        else:require(not transition and a['attemptGroupId']==context['previousAttemptGroupId'])
    else:require(not transition)
for c in load('status-examples.json'):
    a=c['response'];require(c['request']['operationId']==a['operationId'])
    if 'confirmRejection' in a:require(a['reservationStatus']=='CANCELED' and set(a['confirmRejection'])=={'code'} and 'terminalAt' in a)
# Offline decision-table check only: no server, proxy or Spring chain is exercised.
for c in load('http-cases.json'):
    if c.get('expectedDispatch'):
        require(not c['path'].startswith('/internal/v1/entitlements/access'));continue
    if c['port']=='unbound':require(c['expectedTransport']=='CONNECTION_FAILURE');continue
    if not c['enabled'] or c['port']!='workload':status=404
    elif c.get('query') is not None:status=401
    else:
        header=c.get('authorization') or ''
        if not re.fullmatch(r'Bearer [A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+',header):status=401
        else:
            status=auth({'token':header[7:],'now':'2026-10-14T00:00:00.000Z','keySet':'current'})
            if status==204 and (c.get('method')!='POST' or c.get('path')!='/internal/v1/entitlements/access/events'):status=403
    require(status==c['expectedStatus'])
counts['httpDecisionTable']=len(load('http-cases.json'))
manifest=load('manifest.json');require(manifest['deferred']==['S15','V07'])
contract=Path('docs/contracts/LC-BILLING-PAID-EXAM-CONTRACT-R2.md')
if contract.exists():require(hashlib.sha256(contract.read_bytes()).hexdigest()==manifest['contractSha256'])
ids=[]
for f in ['canonical-cases.json','invalid-event-cases.json','token-cases.json','http-cases.json','wire-examples.json','status-examples.json','state-scenarios.json']:
    cases=load(f);local=[c['id'] for c in cases];require(len(set(local))==len(local));ids+=local
require(all(any(i.startswith(prefix+'-') for i in ids) for prefix in manifest['included']))
require(all(c['execution']=='NOT_EXECUTED_REQUIRES_IMPLEMENTATION' for c in load('state-scenarios.json')))
print(json.dumps({'status':'OFFLINE_CHECKS_PASSED',**counts,'wireExamplesChecked':len(load('wire-examples.json')),'statusExamplesChecked':len(load('status-examples.json')),'httpExecuted':False,'stateScenariosExecuted':False,'lcIndependentValidation':False}))
