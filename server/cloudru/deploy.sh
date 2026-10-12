#!/bin/bash
# Builds Margin's door (server/cloudru/app.py) into an image and pushes it to Cloud.ru Artifact Registry — no Docker needed:
# crane appends the code as a layer to python:3.12-alpine. Usage: server/cloudru/deploy.sh [tag]   (default «v.1»)
# The container deploys «v.1» by itself («Автоматическое развертывание», owner, 12 окт); another tag needs a new revision
# in the console. Registry login: CR_REGISTRY / CR_KEY_ID / CR_KEY_SECRET in secrets.properties.
set -euo pipefail
cd "$(dirname "$0")/../.."
TAG=${1:-v.1}
g() { grep "^$1=" secrets.properties | cut -d= -f2- | tr -d '\r '; }
REG=$(g CR_REGISTRY)
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
mkdir -p "$W/root/app"; cp server/cloudru/app.py "$W/root/app/"
(cd "$W/root" && COPYFILE_DISABLE=1 tar --uid 0 --gid 0 -cf ../layer.tar app)
g CR_KEY_SECRET | crane auth login "$REG" -u "$(g CR_KEY_ID)" --password-stdin >/dev/null
crane append --platform linux/amd64 -b python:3.12-alpine -f "$W/layer.tar" -t "$REG/margin-door:base$TAG" >/dev/null
crane mutate "$REG/margin-door:base$TAG" --entrypoint python3,/app/app.py --workdir /app --env PORT=8080,PYTHONUNBUFFERED=1 -t "$REG/margin-door:$TAG"
