"""Explicit prod additive installer/activation. Never stops services or mutates controls.
Run while no other operator or deployment changes referral configuration.
Usage: python3 referral-prod-online.py status|install|activate [successful-schema-task-arn]
"""
import copy
import json
import sys
import boto3

REGION = "ap-northeast-2"
CLUSTER = "tosunsaeng-staging-cluster"
SERVICE = "tosunsaeng-billing-prod-service"
FAMILY = "tosunsaeng-billing-prod"
SCHEMA = FAMILY + "-referral-online"


def env(container):
    values = container.get("environment", [])
    assert len({x["name"] for x in values}) == len(values), "duplicate configuration"
    return {x["name"]: x["value"] for x in values}


def validate_service(service, task):
    assert service["desiredCount"] == service["runningCount"] > 0 and service["pendingCount"] == 0
    assert len(service["deployments"]) == 1 and service["deployments"][0]["rolloutState"] == "COMPLETED"
    assert service["deploymentConfiguration"]["minimumHealthyPercent"] == 100
    assert service["deploymentConfiguration"]["maximumPercent"] >= 200
    assert task["family"] == FAMILY and len(task["containerDefinitions"]) == 1
    c = task["containerDefinitions"][0]
    e = env(c)
    assert e["BILLING_EXPECTED_MONGO_DATABASE"] == "to-teacher-billing-prod"
    assert e.get("REFERRAL_REWARD_ENABLED", "false") == "false"
    assert e.get("BILLING_SWAGGER_ENABLED", "false") == "false"
    assert "@sha256:" in c["image"]
    return c, e


def main():
    stage = sys.argv[1]
    ecs = boto3.client("ecs", region_name=REGION)
    def service():
        r = ecs.describe_services(cluster=CLUSTER, services=[SERVICE])
        assert not r["failures"] and len(r["services"]) == 1
        return r["services"][0]
    def definition(arn):
        return ecs.describe_task_definition(taskDefinition=arn)["taskDefinition"]
    def register(t):
        allowed = ecs.meta.service_model.operation_model("RegisterTaskDefinition").input_shape.members
        return ecs.register_task_definition(**{k: v for k, v in t.items() if k in allowed})["taskDefinition"]["taskDefinitionArn"]
    s = service()
    t = definition(s["taskDefinition"])
    c = t["containerDefinitions"][0]
    if stage == "status":
        print(json.dumps({"task": s["taskDefinition"], "image": c["image"], "desired": s["desiredCount"],
            "running": s["runningCount"], "pending": s["pendingCount"], "rollouts": [d["rolloutState"] for d in s["deployments"]],
            "flags": {k: env(c).get(k, "false") for k in ("REFERRAL_REWARD_ENABLED", "INVITE_SHARE_REWARD_ENABLED", "REFERRAL_PROMOTION_ENDED", "BILLING_SWAGGER_ENABLED")}}))
        return
    c, e = validate_service(s, t)
    # All service tasks and any standalone task in the application family must be OFF.
    arns = set(ecs.list_tasks(cluster=CLUSTER, family=FAMILY, desiredStatus="RUNNING")["taskArns"])
    arns.update(ecs.list_tasks(cluster=CLUSTER, serviceName=SERVICE, desiredStatus="RUNNING")["taskArns"])
    assert arns
    for start in range(0, len(arns), 100):
        r = ecs.describe_tasks(cluster=CLUSTER, tasks=sorted(arns)[start:start+100])
        assert not r["failures"]
        for live in r["tasks"]:
            assert live["taskDefinitionArn"] == s["taskDefinition"] and live["lastStatus"] == "RUNNING"
            for override in live.get("overrides", {}).get("containerOverrides", []):
                assert not override.get("environment") and not override.get("command"), "unexpected writer override"
    assert not ecs.list_tasks(cluster=CLUSTER, family=SCHEMA, desiredStatus="RUNNING")["taskArns"]
    if stage == "install":
        m = copy.deepcopy(t)
        m["family"] = SCHEMA
        mc = m["containerDefinitions"][0]
        mc["environment"] = [{"name": k, "value": e[k]} for k in ("BILLING_EXPECTED_MONGO_DATABASE", "BILLING_EXPECTED_MONGO_HOST")]
        mc["environment"] += [{"name": "REFERRAL_REWARD_ENABLED", "value": "false"},
                              {"name": "REFERRAL_ONLINE_WRITERS_DISABLED", "value": "true"}]
        mc["secrets"] = [v for v in c.get("secrets", []) if v["name"] == "MONGODB_URI"]
        assert len(mc["secrets"]) == 1
        mc["entryPoint"] = ["java"]
        mc["command"] = ["-XX:MaxRAMPercentage=65.0", "-Dloader.main=web.tosunsaeng.billing.ops.ReferralOnlineBootstrap",
            "-cp", "/app/billing.jar", "org.springframework.boot.loader.launch.PropertiesLauncher", "install"]
        mc.pop("healthCheck", None)
        mc["portMappings"] = []
        assert service()["taskDefinition"] == s["taskDefinition"]
        arn = register(m)
        r = ecs.run_task(cluster=CLUSTER, taskDefinition=arn, launchType="FARGATE", networkConfiguration=s["networkConfiguration"], count=1)
        assert not r["failures"]
        print("SCHEMA_TASK", r["tasks"][0]["taskArn"])
    elif stage == "activate":
        r = ecs.describe_tasks(cluster=CLUSTER, tasks=[sys.argv[2]])
        assert not r["failures"] and len(r["tasks"]) == 1
        done = r["tasks"][0]
        assert definition(done["taskDefinitionArn"])["family"] == SCHEMA
        assert done["lastStatus"] == "STOPPED" and all(x.get("exitCode") == 0 for x in done["containers"])
        assert done["containers"][0]["image"] == c["image"]
        original = copy.deepcopy(t)
        e.update(REFERRAL_REWARD_ENABLED="true", INVITE_SHARE_REWARD_ENABLED="false", REFERRAL_PROMOTION_ENDED="false")
        c["environment"] = [{"name": k, "value": v} for k, v in e.items()]
        original["containerDefinitions"][0]["environment"] = c["environment"]
        assert original == t
        assert service()["taskDefinition"] == s["taskDefinition"]
        arn = register(t)
        ecs.update_service(cluster=CLUSTER, service=SERVICE, taskDefinition=arn)
        print("ROLLING_ACTIVATION_REQUESTED", arn)
    else:
        raise ValueError("unknown stage")


if __name__ == "__main__":
    main()
