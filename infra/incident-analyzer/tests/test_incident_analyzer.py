import importlib.util
import pathlib
import sys
import unittest
from unittest.mock import patch


MODULE_PATH = pathlib.Path(__file__).parents[1] / "incident_analyzer.py"
SPEC = importlib.util.spec_from_file_location("incident_analyzer", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class IncidentAnalyzerTest(unittest.TestCase):
    @staticmethod
    def config():
        return MODULE.Config(
            enabled=True,
            ai_api_key="test-key",
            ai_model="test-model",
            ai_base_url="https://example.test",
            ai_path="/v1beta/interactions",
            teams_webhook_url="https://example.test/webhook",
            loki_base_url="http://loki:3100",
            prometheus_base_url="http://prometheus:9090",
            lookback_seconds=300,
            cooldown_seconds=900,
            http_timeout=1,
            max_log_lines=120,
            environment="test",
            grafana_base_url="",
        )

    def test_redact_removes_credentials_and_email(self):
        value = MODULE.redact(
            "api_key=secret-value Authorization:Bearer abc.def "
            "url=https://example.test?sig=hello&x=1 user@example.com"
        )
        self.assertNotIn("secret-value", value)
        self.assertNotIn("abc.def", value)
        self.assertNotIn("hello", value)
        self.assertNotIn("user@example.com", value)
        self.assertIn("[REDACTED]", value)

    def test_fingerprint_is_stable_across_alert_order(self):
        first = {"alerts": [{"fingerprint": "b"}, {"fingerprint": "a"}]}
        second = {"alerts": [{"fingerprint": "a"}, {"fingerprint": "b"}]}
        self.assertEqual(
            MODULE.alert_fingerprint(first), MODULE.alert_fingerprint(second)
        )

    def test_cooldown_suppresses_duplicate(self):
        cache = MODULE.CooldownCache(60)
        self.assertTrue(cache.accept("incident", now=100))
        self.assertFalse(cache.accept("incident", now=120))
        self.assertTrue(cache.accept("incident", now=161))

    def test_incident_summary_uses_alert_annotations(self):
        result = MODULE.incident_summary(
            {
                "status": "firing",
                "alerts": [
                    {
                        "labels": {"severity": "critical"},
                        "annotations": {"summary": "backend down"},
                        "startsAt": "2026-08-28T00:00:00Z",
                    }
                ],
            }
        )
        self.assertEqual("backend down", result["title"])
        self.assertEqual("critical", result["labels"]["severity"])
        self.assertEqual(1, result["alertCount"])

    def test_completed_gemini_interaction_is_parsed(self):
        expected = {
            "suspectedCause": "heap pressure",
            "evidence": ["heap 90%"],
            "impact": "slow response",
            "immediateActions": ["check heap dump"],
            "confidence": "high",
        }
        response = {
            "status": "completed",
            "steps": [
                {
                    "type": "model_output",
                    "content": [{"type": "text", "text": MODULE.json.dumps(expected)}],
                }
            ],
        }
        analyzer = MODULE.IncidentAnalyzer(self.config())
        with patch.object(MODULE, "request_json", return_value=response) as request:
            actual = analyzer.analyze_with_ai({"title": "alert"}, [], {})
        self.assertEqual(expected, actual)
        self.assertEqual("test-key", request.call_args.kwargs["headers"]["x-goog-api-key"])


if __name__ == "__main__":
    unittest.main()
