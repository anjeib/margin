#!/bin/bash
# Publishes server/proxy as a new version of «margin-proxy»: invite codes from server/codes.local (one per line).
# Remove a code from the file and run this again to switch that tester off.
set -e
cd "$(dirname "$0")"
YC=${YC:-$HOME/yandex-cloud/bin/yc}
export YC_CLI_INITIALIZATION_SILENCE=true
FOLDER=$($YC config get folder-id)
SA=$($YC iam service-account get margin-app --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
CODES=$(grep -v '^\s*$' codes.local | paste -sd' ' -)
$YC serverless function version create \
  --function-name margin-proxy --runtime python312 --entrypoint index.handler \
  --memory 128m --execution-timeout 120s --service-account-id "$SA" \
  --source-path proxy --environment "FOLDER_ID=$FOLDER,MARGIN_CODES=$CODES" >/dev/null
$YC serverless function allow-unauthenticated-invoke margin-proxy >/dev/null
echo "margin-proxy: новая версия, кодов: $(echo "$CODES" | tr ' ' '\n' | wc -l | tr -d ' ')"
