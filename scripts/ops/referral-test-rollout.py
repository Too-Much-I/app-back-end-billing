"""One-off test rollout. Run each stage explicitly; never targets production."""
import base64
import copy
import json
import re
import sys
import boto3

ecs = boto3.client("ecs", region_name="ap-northeast-2")
CLUSTER = "tosunsaeng-staging-cluster"
SERVICE = "tosunsaeng-billing-test-service"
FAMILY = "tosunsaeng-billing-test"
SCHEMA = FAMILY + "-referral-schema"

def service():
    result = ecs.describe_services(cluster=CLUSTER, services=[SERVICE])
    assert not result["failures"]
    return result["services"][0]

def definition(arn):
    return ecs.describe_task_definition(taskDefinition=arn)["taskDefinition"]

def register(task):
    allowed = ecs.meta.service_model.operation_model("RegisterTaskDefinition").input_shape.members
    return ecs.register_task_definition(**{k: v for k, v in task.items() if k in allowed})["taskDefinition"]["taskDefinitionArn"]

stage = sys.argv[1]
s = service()
t = definition(s["taskDefinition"])
assert t["family"] == FAMILY and len(t["containerDefinitions"]) == 1
c = t["containerDefinitions"][0]
env = {v["name"]: v["value"] for v in c["environment"]}
assert env["BILLING_EXPECTED_MONGO_DATABASE"] == "to-teacher-billing-test"

if stage == "prepare":
    assert s["desiredCount"] == s["runningCount"] == 1 and s["pendingCount"] == 0
    assert len(s["deployments"]) == 1 and s["deployments"][0]["rolloutState"] == "COMPLETED"
    assert c["image"] == sys.argv[2], "expected newly built image digest required"
    payload = sys.argv[3]
    assert base64.b64decode(payload).startswith(bytes.fromhex("cafebabe"))
    m = definition(FAMILY + "-invite-schema")
    assert m["family"] == FAMILY + "-invite-schema"
    m["family"] = SCHEMA
    mc = m["containerDefinitions"][0]
    mc["image"] = c["image"]
    menv = {v["name"]: v["value"] for v in mc["environment"]}
    assert menv["BILLING_EXPECTED_MONGO_DATABASE"] == env["BILLING_EXPECTED_MONGO_DATABASE"]
    assert menv["BILLING_EXPECTED_MONGO_HOST"] == env["BILLING_EXPECTED_MONGO_HOST"]
    assert next(x for x in mc["secrets"] if x["name"] == "MONGODB_URI") == next(x for x in c["secrets"] if x["name"] == "MONGODB_URI")
    menv.pop("INVITE_MAINTENANCE_WRITERS_STOPPED", None)
    menv["REFERRAL_MAINTENANCE_WRITERS_STOPPED"] = "true"
    mc["environment"] = [{"name": k, "value": v} for k, v in menv.items()]
    old = mc["command"][0]
    new, count = re.subn(r"yv66vg[A-Za-z0-9+/=]+", payload, old)
    assert count == 1
    mc["command"] = [new.replace("InviteRewardMaintenance", "ReferralRewardMaintenance").replace("/tmp/invite-maint", "/tmp/referral-maint")]
    print("SCHEMA_REGISTERED", register(m))
elif stage == "stop":
    assert s["desiredCount"] == s["runningCount"] == 1 and len(s["deployments"]) == 1
    ecs.update_service(cluster=CLUSTER, service=SERVICE, desiredCount=0)
    print("TEST_STOP_REQUESTED")
elif stage == "install":
    assert s["desiredCount"] == s["runningCount"] == s["pendingCount"] == 0
    assert not ecs.list_tasks(cluster=CLUSTER, serviceName=SERVICE, desiredStatus="RUNNING")["taskArns"]
    assert not ecs.list_tasks(cluster=CLUSTER, family=FAMILY, desiredStatus="RUNNING")["taskArns"]
    assert not ecs.list_tasks(cluster=CLUSTER, family=SCHEMA, desiredStatus="RUNNING")["taskArns"]
    m = definition(SCHEMA)
    assert m["containerDefinitions"][0]["image"] == c["image"]
    result = ecs.run_task(cluster=CLUSTER, taskDefinition=m["taskDefinitionArn"], launchType="FARGATE", networkConfiguration=s["networkConfiguration"], count=1)
    assert not result["failures"]
    print("SCHEMA_TASK", result["tasks"][0]["taskArn"])
elif stage == "activate":
    assert s["pendingCount"] == 0 and s["desiredCount"] == s["runningCount"] and s["desiredCount"] in (0, 1)
    assert len(s["deployments"]) == 1 and s["deployments"][0]["rolloutState"] == "COMPLETED"
    result = ecs.describe_tasks(cluster=CLUSTER, tasks=[sys.argv[2]])
    assert not result["failures"]
    done = result["tasks"][0]
    assert definition(done["taskDefinitionArn"])["family"] == SCHEMA
    assert done["lastStatus"] == "STOPPED" and all(v.get("exitCode") == 0 for v in done["containers"])
    assert done["containers"][0]["image"] == c["image"]
    old = copy.deepcopy(t)
    env.update(REFERRAL_REWARD_ENABLED="true", INVITE_SHARE_REWARD_ENABLED="false")
    c["environment"] = [{"name": k, "value": v} for k, v in env.items()]
    old["containerDefinitions"][0]["environment"] = c["environment"]
    assert old == t, "only reward flags may change"
    arn = register(t)
    ecs.update_service(cluster=CLUSTER, service=SERVICE, taskDefinition=arn, desiredCount=1)
    print("TEST_ACTIVATION_REQUESTED", arn)
elif stage == "status":
    print(json.dumps({"task": s["taskDefinition"], "image": c["image"], "desired": s["desiredCount"], "running": s["runningCount"], "pending": s["pendingCount"], "deployments": [d["rolloutState"] for d in s["deployments"]], "referral": env.get("REFERRAL_REWARD_ENABLED", "false"), "invite": env.get("INVITE_SHARE_REWARD_ENABLED", "false")}))
else:
    raise ValueError("unknown stage")
