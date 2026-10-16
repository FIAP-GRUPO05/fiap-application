"""The production gate invokes the route inventory smoke test."""

import subprocess

subprocess.run(["./gradlew", "test", "--tests", "*EndpointRegressionTest", "--no-daemon"], check=True)
