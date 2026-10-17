"""Regression tests for the workflow source checker."""
import unittest

from check_workflows import check_python_heredocs


class EmbeddedPythonValidationTests(unittest.TestCase):
    def test_valid_heredoc(self):
        script = "python3 - <<'PY'\nprint('OK')\nPY"
        self.assertEqual([], check_python_heredocs(script, "example"))

    def test_invalid_python_is_reported(self):
        script = "python3 <<'PY'\nif True print('oops')\nPY"
        errors = check_python_heredocs(script, "example")
        self.assertEqual(1, len(errors))
        self.assertIn("syntax error", errors[0])

    def test_missing_terminator_is_reported(self):
        script = "python3 - <<'PATCH'\nprint('oops')"
        errors = check_python_heredocs(script, "example")
        self.assertEqual(1, len(errors))
        self.assertIn("unclosed", errors[0])

    def test_other_shell_commands_ignored(self):
        self.assertEqual([], check_python_heredocs("echo hello\n./gradlew test", "example"))


if __name__ == "__main__":
    unittest.main()
