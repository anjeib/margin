#!/bin/bash
# Builds Margin's door (server/cloudru/app.py) into an image and pushes it to Cloud.ru Artifact Registry — no Docker needed:
# crane appends the code as a layer to python:3.12-alpine. Usage: server/cloudru/deploy.sh <tag>   (e.g. 3)
# Then in the console: Container Apps → margin-door → «Создать ревизию на основе выбранной» → image with this tag
# (or nothing, if «Автоматическое развертывание» is on). Registry login: CR_REGISTRY / CR_KEY_ID / CR_KEY_SECRET in secrets.properties.
set -euo pipefail
cd "$(dirname "$0")/../.."
TAG=${1:?tag}
g() { grep "^$1=" secrets.properties | cut -d= -f2- | tr -d '\r '; }
REG=$(g CR_REGISTRY)
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
mkdir -p "$W/root/app"; cp server/cloudru/app.py "$W/root/app/"
(cd "$W/root" && COPYFILE_DISABLE=1 tar --uid 0 --gid 0 -cf ../layer.tar app)
g CR_KEY_SECRET | crane auth login "$REG" -u "$(g CR_KEY_ID)" --password-stdin >/dev/null
crane append --platform linux/amd64 -b python:3.12-alpine -f "$W/layer.tar" -t "$REG/margin-door:base$TAG" >/dev/null
crane mutate "$REG/margin-door:base$TAG" --entrypoint python3,/app/app.py --workdir /app --env PORT=8080,PYTHONUNBUFFERED=1 -t "$REG/margin-door:$TAG"
