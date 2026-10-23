"""Explicit test-only referral POST allowlist. Requires operator authorization."""
import boto3

REGION = "ap-northeast-2"
LISTENER = "arn:aws:elasticloadbalancing:ap-northeast-2:889384901776:listener/app/tosunsaeng-staging-alb/455925e3ff93252c/fd9dacab3823ed54"
HOST = "billing-test.to-teacher.com"
PATHS = ["/api/v1/rewards/referral-code", "/api/v1/rewards/referrals"]
alb = boto3.client("elbv2", region_name=REGION)
rules = alb.describe_rules(ListenerArn=LISTENER)["Rules"]
invite = next(r for r in rules if r["Priority"] == "98")
def values(c):
    return c.get("Values", c.get("HttpRequestMethodConfig", {}).get("Values", []))

conditions = {c["Field"]: values(c) for c in invite["Conditions"]}
assert conditions["host-header"] == [HOST]
assert conditions["path-pattern"] == ["/api/v1/rewards/invite-shares"]
assert set(conditions["http-request-method"]) == {"GET", "POST"}
actions = invite["Actions"]
assert len(actions) == 1 and actions[0]["Type"] == "forward"
target = actions[0]["TargetGroupArn"]
group = alb.describe_target_groups(TargetGroupArns=[target])["TargetGroups"][0]
assert group["Port"] == 8083
desired = [
    {"Field": "host-header", "HostHeaderConfig": {"Values": [HOST]}},
    {"Field": "path-pattern", "PathPatternConfig": {"Values": PATHS}},
    {"Field": "http-request-method", "HttpRequestMethodConfig": {"Values": ["POST"]}},
]
assert not any(r["Priority"] == "97" for r in rules), "priority occupied; inspect before retry"
created = alb.create_rule(ListenerArn=LISTENER, Priority=97, Conditions=desired, Actions=actions)["Rules"][0]
actual = alb.describe_rules(RuleArns=[created["RuleArn"]])["Rules"][0]
assert {c["Field"]: values(c) for c in actual["Conditions"]} == {
    "host-header": [HOST], "path-pattern": PATHS, "http-request-method": ["POST"]}
assert actual["Actions"] == actions
print("REFERRAL_TEST_ALB_OK priority=97 host=test method=POST paths=2 port=8083")
