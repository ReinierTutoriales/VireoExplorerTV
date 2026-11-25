import subprocess
import unittest
from unittest.mock import patch
from tv_runtime import Tv


class SetupTests(unittest.TestCase):
    def test_readiness_requires_package_manager_and_two_good_probes(self):
        tv = Tv("emulator-5554")
        responses = ["device", "1", "", "device", "1", "package:/system/framework/framework-res.apk",
                     "device", "1", "package:/system/framework/framework-res.apk"]
        with patch.object(tv, "setup_command", side_effect=responses) as command, patch("tv_runtime.time.sleep"):
            tv.wait_ready()
        self.assertEqual(command.call_count, 9)

    def test_transient_install_timeout_retries_once(self):
        tv = Tv("emulator-5554")
        with patch.object(tv, "wait_ready") as ready, patch.object(
                tv, "setup_command", side_effect=[subprocess.TimeoutExpired("adb", 90), "Success\n"]) as command:
            with patch("tv_runtime.subprocess.run") as reset:
                tv.install("fixture.apk")
        self.assertEqual(ready.call_count, 2)
        self.assertEqual(command.call_count, 2)
        self.assertEqual(reset.call_count, 2)
        command.assert_called_with("install", "--no-streaming", "-r", "fixture.apk", timeout=90)

    def test_deterministic_install_error_not_retried(self):
        tv = Tv("emulator-5554")
        error = subprocess.CalledProcessError(1, "adb", output="Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]")
        with patch.object(tv, "wait_ready"), patch.object(tv, "setup_command", side_effect=error) as command:
            with self.assertRaises(subprocess.CalledProcessError):
                tv.install("fixture.apk")
        self.assertEqual(command.call_count, 1)

    def test_second_timeout_propagates(self):
        tv = Tv("emulator-5554")
        with patch.object(tv, "wait_ready"), patch.object(
                tv, "setup_command", side_effect=subprocess.TimeoutExpired("adb", 90)) as command:
            with patch("tv_runtime.subprocess.run"):
                with self.assertRaises(subprocess.TimeoutExpired):
                    tv.install("fixture.apk")
        self.assertEqual(command.call_count, 2)

    def test_runtime_configuration_after_readiness(self):
        tv = Tv("emulator-5554")
        with patch.object(tv, "wait_ready") as ready, patch.object(
                tv, "setup_command", side_effect=["", "0.0", "", "0.0", "", "0.0"]) as command:
            tv.configure_runtime()
        ready.assert_called_once()
        self.assertEqual(command.call_count, 6)

    def test_runtime_configuration_rejects_unapplied_setting(self):
        tv = Tv("emulator-5554")
        with patch.object(tv, "wait_ready"), patch.object(
                tv, "setup_command", side_effect=["", "1.0"]):
            with self.assertRaises(AssertionError):
                tv.configure_runtime()
