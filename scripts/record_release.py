#!/usr/bin/env python3
"""Pin the freshly built image (by digest) in the production overlay.

CI runs this after pushing the image; Argo CD then deploys exactly that digest.
Only the two files below are ever written.
"""
import argparse
import datetime
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OVERLAY = ROOT / "k8s" / "overlays" / "kassa-prod"

SEMVER = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")
DIGEST = re.compile(r"sha256:[0-9a-f]{64}")
BUILD_ID = re.compile(r"[0-9]+\.[0-9]+")
SHA = re.compile(r"[0-9a-f]{40}")
IMAGE = re.compile(r"[a-z0-9][a-z0-9.-]*(:[0-9]+)?/[a-z0-9._/-]+:[A-Za-z0-9._-]+")

TEMPLATE = """apiVersion: apps/v1
kind: Deployment
metadata:
  name: ghesta-server
spec:
  template:
    metadata:
      annotations:
        ghesta.app/version: "{version}"
        ghesta.app/build-id: "{build_id}"
        ghesta.app/source-revision: "{source_revision}"
    spec:
      containers:
        - name: ghesta-server
          image: {image}@{digest}
"""


def check(value, pattern, what):
    if not pattern.fullmatch(value):
        raise ValueError(f"invalid {what}: {value!r}")


def render(image, digest, version, build_id, source_revision):
    check(image, IMAGE, "image")
    check(digest, DIGEST, "digest")
    check(version, SEMVER, "version")
    check(build_id, BUILD_ID, "build id")
    check(source_revision, SHA, "source revision")
    if f":{version}-prod.{build_id}" not in image:
        raise ValueError("image tag does not match version and build id")
    return TEMPLATE.format(image=image, digest=digest, version=version,
                           build_id=build_id, source_revision=source_revision)


def record(overlay, image, digest, version, build_id, source_revision, now=None):
    patch = render(image, digest, version, build_id, source_revision)
    (overlay / "image-patch.yaml").write_text(patch)
    release = {
        "version": version,
        "build_id": build_id,
        "source_revision": source_revision,
        "image": image,
        "digest": digest,
        "published_at": (now or datetime.datetime.now(datetime.timezone.utc)).isoformat(),
    }
    (overlay / "release.json").write_text(json.dumps(release, indent=2) + "\n")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", required=True, help="registry/repo:tag (without digest)")
    parser.add_argument("--digest", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--build-id", required=True)
    parser.add_argument("--source-revision", required=True)
    parser.add_argument("--overlay", default=str(OVERLAY))
    args = parser.parse_args(argv)
    try:
        record(Path(args.overlay), args.image, args.digest, args.version, args.build_id, args.source_revision)
    except ValueError as error:
        print(f"Release not recorded: {error}", file=sys.stderr)
        return 1
    print(f"Recorded {args.image}@{args.digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
