"""Offline contract models only; no production imports, HTTP, DB or runtime tests."""
import base64
import datetime as dt
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('src/test/resources/contracts/lc-billing-paid-exam-simplified-exact-r1-fixtures-2')
REVISION = 'lc-billing-paid-exam-simplified-exact-r1-fixtures-2'
DAYS120_MS = 10_368_000_000

def require(ok, label='validation'):
    if not ok:
        raise ValueError(label + ' failed')

def pairs(items):
    result = {}
    for key, value in items:
        require(key not in result, 'duplicate JSON field')
        result[key] = value
    return result

def no_constant(_):
    raise ValueError('invalid JSON constant')

def scalar(value):
    if isinstance(value, str):
        require(not any(0xd800 <= ord(x) <= 0xdfff for x in value), 'Unicode scalar')
    elif isinstance(value, dict):
        for key, item in value.items():
            scalar(key)
            scalar(item)
    elif isinstance(value, list):
        for item in value:
            scalar(item)

def parse(raw):
    value = json.loads(raw.decode('utf-8', 'strict'), object_pairs_hook=pairs, parse_constant=no_constant)
    scalar(value)
    return value

def load(name):
    return parse((ROOT / name).read_bytes())

def error_category(data):
    # Expected labels never enter this function.
    try:
        value = parse(base64.b64decode(data['rawBodyBase64'], validate=True))
        if not isinstance(value, dict) or set(value) != {'code','message','retryable','correlationId'}:
            return 'CONTRACT_ERROR'
        if type(value['retryable']) is not bool or any(type(value[k]) is not str for k in ['code','message','correlationId']):
            return 'CONTRACT_ERROR'
        if data['endpoint'] not in {'reserve','status'}:
            return 'CONTRACT_ERROR'
        code, status, retry = value['code'], data['http'], value['retryable']
        if code == 'MEMBER_EVIDENCE_PENDING' and status == 503 and retry:
            return 'WAITING_MEMBER'
        if code == 'RESERVE_REJECTED_BEFORE_CREATION' and status == 409 and not retry and data['endpoint'] == 'status':
            return 'PRE_RESERVE_TERMINAL'
        if code == 'OPERATION_OWNER_MISMATCH' and status == 409 and not retry and value['message'] == 'The operation owner does not match the current owner.':
            return 'OWNER_BLOCKED'
        if code == 'OPERATION_NOT_FOUND' and status == 404 and not retry and data['endpoint'] == 'status':
            return 'OPERATION_NOT_FOUND'
        if code == 'BILLING_TEMPORARILY_UNAVAILABLE' and status == 503 and retry:
            return 'TEMPORARY_FAILURE'
    except (ValueError, UnicodeError, TypeError):
        pass
    return 'CONTRACT_ERROR'

def resume_decision(value):
    if not value['authAllowed'] or value['quarantine'] == 'BLOCKED_AUTH':
        return 'AUTH_BLOCKED'
    if not value['ownerAllowed'] or value['quarantine'] == 'BLOCKED_OWNER':
        return 'OWNER_BLOCKED'
    if not all(value[x] for x in ['leaseValid','versionMatches','ownsGuard']):
        return 'RELOAD_REQUIRED'
    if value['phase'] != 'PREPARED':
        return 'EXISTING_STATE_RECOVERY'
    if any(value[x] for x in ['newSessionExists','commitMarker','reservationKnown','cancelSent','cancelUnknown']):
        return 'REVIEW_REQUIRED'
    if not value['withinWindow']:
        return 'REVIEW_REQUIRED'
    if value['remote'] != 'MEMBER_PENDING':
        return 'STATUS_RECOVERY_REQUIRED'
    if not value['memberWaitPersisted'] or value['mixedCause']:
        return 'REVIEW_REQUIRED'
    if value['quarantine'] == 'NEEDS_REVIEW':
        if value['reason'] != 'retry_exhausted' or not value['exhaustionLinked']:
            return 'REVIEW_REQUIRED'
    elif value['quarantine'] != 'NONE':
        return 'REVIEW_REQUIRED'
    if value['intent'] not in {'WAITING_MEMBER','CLEANUP_PRECOMMIT'}:
        return 'REVIEW_REQUIRED'
    return 'HTTP_RESUME_ALLOWED'

def millis(text):
    match = re.fullmatch(r'(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})\.(\d{3})Z', text)
    require(match is not None, 'millisecond format')
    instant = dt.datetime.strptime(match[1], '%Y-%m-%dT%H:%M:%S')
    delta = instant - dt.datetime(1970, 1, 1)
    return (delta.days * 86400 + delta.seconds) * 1000 + int(match[2])

def phone_valid(case):
    req, val, status = case['request'], case['reserveSnapshot'], case['statusSnapshot']
    fields = {'reservationKind','entitlementSource','attemptGroupId','sessionId','mockExamId','continuationReason','continuationId'}
    if set(val) != fields or set(status) != fields or val != status:
        return False
    if any(val[k] != req[k] for k in ['sessionId','mockExamId','continuationReason','continuationId']):
        return False
    if val['reservationKind'] == 'INITIAL':
        return val['entitlementSource'] == 'SUBSCRIPTION' and val['attemptGroupId'] != req['expectedAttemptGroupId']
    return val['reservationKind'] == 'REPLACEMENT' and val['entitlementSource'] == 'EXAM_PASS' and val['attemptGroupId'] == req['expectedAttemptGroupId']

def main():
    manifest = load('manifest.json')
    require(manifest['revision'] == REVISION, 'revision')
    require(manifest['runtimeTested'] is False and manifest['httpExecuted'] is False and manifest['lcIndependentValidation'] is False, 'scope')
    require(manifest['deferred'] == ['deletion-route-auth','historical-owner-deletion','final-record-retention-purge'], 'deferred')
    lines = (ROOT / 'SHA256SUMS').read_text().splitlines()
    names = []
    for line in lines:
        match = re.fullmatch(r'([0-9a-f]{64})  ([a-zA-Z0-9.-]+)', line)
        require(match is not None, 'hash manifest syntax')
        digest, name = match.groups()
        require(name not in names, 'hash file uniqueness')
        names.append(name)
        require(hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest, 'byte hash ' + name)
    require(set(names) == {x.name for x in ROOT.iterdir()} - {'SHA256SUMS'}, 'hash coverage')
    require(len(names) == 9, 'hash count')
    snapshot = (ROOT / 'contract-snapshot.md').read_bytes()
    require(hashlib.sha256(snapshot).hexdigest() == manifest['contractSha256'], 'contract snapshot hash')
    # If verifying inside the producer repository, also check the source without changing it.
    source = Path(manifest['contractPath'])
    if source.exists():
        require(source.read_bytes() == snapshot, 'source snapshot equality')
    groups = {
        'errors':load('error-cases.json'), 'publicExamples':load('public-error-examples.json'),
        'time':load('time-cases.json'), 'memberResume':load('member-resume-cases.json'),
        'phone':load('phone-snapshot-cases.json'), 'stateScenarios':load('state-scenarios.json')}
    all_ids = []
    for name, cases in groups.items():
        require(len(cases) == manifest['counts'][name], 'count '+name)
        ids = [x['id'] for x in cases]
        require(len(set(ids)) == len(ids), 'IDs '+name)
        all_ids.extend(ids)
    require(len(all_ids) == len(set(all_ids)), 'global IDs')
    for case in groups['errors']:
        require(error_category(case['input']) == case['expected'], case['id'])
    public = {
        'member':(503,'BILLING_TEMPORARILY_UNAVAILABLE','시험 사용권을 일시적으로 확인할 수 없습니다.','1'),
        'delete-during-creation':(409,'EXAM_CREATION_PROCESSING','시험 준비 중입니다. 잠시 후 다시 시도해 주세요.','1'),
        'creation-processing':(409,'EXAM_CREATION_PROCESSING','시험 생성이 처리 중입니다. 같은 요청으로 다시 시도해 주세요.','1'),
        'deleted-replay':(404,'EXAM_4004','해당 모의고사 세션을 찾을 수 없습니다.',None)}
    for case in groups['publicExamples']:
        status, code, message, after = public[case['kind']]
        body = parse(base64.b64decode(case['rawBodyBase64'], validate=True))
        require(body == {'isSuccess':False,'code':code,'message':message} and set(body) == {'isSuccess','code','message'}, case['id'])
        require(case['http'] == status and case['retryAfter'] == after, case['id'])
    for case in groups['time']:
        if 'firstAcceptedAt' in case:
            end = millis(case['firstAcceptedAt']) + DAYS120_MS
            require(end == millis(case['expectedGuaranteeUntil']), case['id'])
            require((millis(case['now']) < end) is case['expectedWithin'], case['id'])
        else:
            eligible = millis(case['now']) >= millis(case['terminalAt']) + DAYS120_MS and not case['unresolved'] and not case['hold']
            require(eligible is case['expectedPurgeEligible'], case['id'])
    for case in groups['memberResume']:
        require(resume_decision(case['input']) == case['expected'], case['id'])
    for case in groups['phone']:
        require(phone_valid(case) is case['expectedValid'], case['id'])
    required = {f'{prefix}{i:02}' for prefix, count in [('E',6),('T',6),('P',5),('C',3)] for i in range(1,count+1)} | {'E02-'+c for c in 'abcdefgh'}
    require({x['id'] for x in groups['stateScenarios']} == required, 'state coverage')
    for case in groups['stateScenarios']:
        require(case['execution'] == 'NOT_EXECUTED_REQUIRES_IMPLEMENTATION', 'state execution label')
        require(case['requirement'] in snapshot.decode('utf-8'), 'state contract traceability')
    print(json.dumps({'status':'OFFLINE_CHECKS_PASSED','hashFiles':len(names),**{k:len(v) for k,v in groups.items()},'httpExecuted':False,'stateScenariosExecuted':False,'lcIndependentValidation':False}))

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Only case IDs/labels; never emit raw fixture bodies or parser contents.
        print('OFFLINE_CHECKS_FAILED: '+ (str(error) if type(error) is ValueError and str(error).endswith(' failed') else type(error).__name__), file=sys.stderr)
        sys.exit(1)
