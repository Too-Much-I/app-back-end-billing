import copy
import importlib.util
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch

with patch.dict(sys.modules, {"boto3": types.ModuleType("boto3")}):
    spec = importlib.util.spec_from_file_location("rollout", Path(__file__).with_name("referral-prod-online.py"))
    rollout = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(rollout)


class PreconditionsTest(unittest.TestCase):
    def setUp(self):
        self.service = {"desiredCount": 1, "runningCount": 1, "pendingCount": 0,
            "deployments": [{"rolloutState": "COMPLETED"}],
            "deploymentConfiguration": {"minimumHealthyPercent": 100, "maximumPercent": 200}}
        self.task = {"family": rollout.FAMILY, "containerDefinitions": [{"image": "example@sha256:" + "a" * 64,
            "environment": [{"name": "BILLING_EXPECTED_MONGO_DATABASE", "value": "to-teacher-billing-prod"}]}]}

    def test_live_service_accepted_without_mutation(self):
        before = copy.deepcopy((self.service, self.task))
        rollout.validate_service(self.service, self.task)
        self.assertEqual(before, (self.service, self.task))

    def test_offline_or_incomplete_service_rejected(self):
        for field, value in (("desiredCount", 0), ("runningCount", 0), ("pendingCount", 1)):
            s = copy.deepcopy(self.service)
            s[field] = value
            with self.assertRaises(AssertionError):
                rollout.validate_service(s, self.task)

    def test_stop_first_strategy_rejected(self):
        self.service["deploymentConfiguration"]["minimumHealthyPercent"] = 0
        with self.assertRaises(AssertionError):
            rollout.validate_service(self.service, self.task)

    def test_enabled_referral_or_swagger_rejected(self):
        for flag in ("REFERRAL_REWARD_ENABLED", "BILLING_SWAGGER_ENABLED"):
            t = copy.deepcopy(self.task)
            t["containerDefinitions"][0]["environment"].append({"name": flag, "value": "true"})
            with self.assertRaises(AssertionError):
                rollout.validate_service(self.service, t)

    def test_other_db_rejected(self):
        self.task["containerDefinitions"][0]["environment"][0]["value"] = "test"
        with self.assertRaises(AssertionError):
            rollout.validate_service(self.service, self.task)

    def test_duplicate_configuration_rejected(self):
        self.task["containerDefinitions"][0]["environment"] *= 2
        with self.assertRaises(AssertionError):
            rollout.validate_service(self.service, self.task)


if __name__ == "__main__":
    unittest.main()
