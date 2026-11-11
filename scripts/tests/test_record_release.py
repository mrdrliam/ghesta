import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import record_release as rr  # noqa: E402

GOOD = dict(
    image="ghcr.io:443/mrdrliam/ghesta-server:0.1.0-prod.123.1",
    digest="sha256:" + "a" * 64,
    version="0.1.0",
    build_id="123.1",
    source_revision="b" * 40,
)


class RecordReleaseTest(unittest.TestCase):
    def test_writes_pinned_image_and_release_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            overlay = Path(tmp)
            rr.record(overlay, **GOOD)
            patch = (overlay / "image-patch.yaml").read_text()
            self.assertIn(f"image: {GOOD['image']}@{GOOD['digest']}", patch)
            self.assertIn('ghesta.app/build-id: "123.1"', patch)
            release = json.loads((overlay / "release.json").read_text())
            self.assertEqual(release["digest"], GOOD["digest"])

    def test_rejects_bad_digest(self):
        with self.assertRaises(ValueError):
            rr.render(**{**GOOD, "digest": "sha256:abc"})

    def test_rejects_tag_that_does_not_match_identity(self):
        with self.assertRaises(ValueError):
            rr.render(**{**GOOD, "build_id": "999.1"})

    def test_rejects_shell_or_yaml_injection(self):
        with self.assertRaises(ValueError):
            rr.render(**{**GOOD, "image": "ghcr.io/x/y:1.0.0-prod.123.1\n  evil: true"})
        with self.assertRaises(ValueError):
            rr.render(**{**GOOD, "version": "0.1.0\"\n"})

    def test_cli_exit_codes(self):
        script = Path(rr.__file__)
        with tempfile.TemporaryDirectory() as tmp:
            ok = subprocess.run([sys.executable, str(script), "--overlay", tmp, "--image", GOOD["image"],
                                 "--digest", GOOD["digest"], "--version", GOOD["version"],
                                 "--build-id", GOOD["build_id"], "--source-revision", GOOD["source_revision"]],
                                capture_output=True, text=True)
            self.assertEqual(ok.returncode, 0, ok.stderr)
            bad = subprocess.run([sys.executable, str(script), "--overlay", tmp, "--image", GOOD["image"],
                                  "--digest", "nope", "--version", GOOD["version"],
                                  "--build-id", GOOD["build_id"], "--source-revision", GOOD["source_revision"]],
                                 capture_output=True, text=True)
            self.assertEqual(bad.returncode, 1)


if __name__ == "__main__":
    unittest.main()
