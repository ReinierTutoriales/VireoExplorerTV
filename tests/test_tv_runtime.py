import subprocess
import unittest
from unittest.mock import patch
from tv_runtime import Tv, initial_install


class SetupTests(unittest.TestCase):
    def test_readiness_requires_package_manager_and_two_good_probes(self):
        tv = Tv("emulator-5554")
        responses = ["device", "1", "", "device", "1", "package:/system/framework/framework-res.apk",
                     "device", "1", "package:/system/framework/framework-res.apk"]
        with patch.object(tv, "setup_command", side_effect=responses) as command, patch("tv_runtime.time.sleep"):
            tv.wait_ready()
        self.assertEqual(command.call_count, 9)

    def test_install_success(self):
        tv = Tv("emulator-5554")
        with patch.object(tv, "wait_ready"), patch.object(tv, "setup_command", return_value="Success\n") as command:
            tv.install("fixture.apk")
        command.assert_called_once_with("install", "--no-streaming", "-r", "fixture.apk", timeout=90)

    def test_install_timeout_propagates_without_replay(self):
        tv = Tv("emulator-5554")
        with patch.object(tv, "wait_ready"), patch.object(
                tv, "setup_command", side_effect=subprocess.TimeoutExpired("adb", 90)) as command:
            with self.assertRaises(subprocess.TimeoutExpired):
                tv.install("fixture.apk")
        command.assert_called_once()

    def test_deterministic_install_error_not_retried(self):
        tv = Tv("emulator-5554")
        error = subprocess.CalledProcessError(1, "adb", output="Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]")
        with patch.object(tv, "wait_ready"), patch.object(tv, "setup_command", side_effect=error) as command:
            with self.assertRaises(subprocess.CalledProcessError):
                tv.install("fixture.apk")
        command.assert_called_once()

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

    def test_initial_install_timeout_is_reported_for_clean_boot_retry(self):
        tv = Tv("emulator-5554")
        with patch.object(tv, "install", side_effect=subprocess.TimeoutExpired("adb", 90)):
            with self.assertRaises(SystemExit) as result:
                initial_install(tv, "hierarchy.apk", "release.apk")
        self.assertEqual(result.exception.code, 75)

    def test_initial_install_deterministic_failure_propagates(self):
        tv = Tv("emulator-5554")
        error = subprocess.CalledProcessError(1, "adb", output="INSTALL_FAILED_UPDATE_INCOMPATIBLE")
        with patch.object(tv, "install", side_effect=error):
            with self.assertRaises(subprocess.CalledProcessError):
                initial_install(tv, "hierarchy.apk", "release.apk")
