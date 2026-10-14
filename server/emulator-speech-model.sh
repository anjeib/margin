#!/bin/zsh
# Puts the speech model (GigaAM v3) straight into Margin on the emulator — release and debug — so it never has to be
# downloaded there. Run again after `adb uninstall` / `pm clear`; a reinstall over the app (`install -r`) keeps it.
# The model lies in third_party/speech-model/<version>/ (not in git); missing files are fetched from Hugging Face once.
set -e
ADB=${ADB:-/opt/homebrew/share/android-commandlinetools/platform-tools/adb}
VER=gigaam-v3-rnnt-punct-2025-12-16
SRC=${0:A:h}/../third_party/speech-model/$VER
HF=https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16/resolve/main
mkdir -p $SRC
for f in tokens.txt decoder.onnx joiner.onnx encoder.int8.onnx; do
  [[ -s $SRC/$f ]] || curl -sL -C - -o $SRC/$f $HF/$f
done
[[ -s $SRC/manifest.json ]] || { echo "manifest.json missing in $SRC"; exit 1; }

$ADB root >/dev/null; $ADB wait-for-device
$ADB shell mkdir -p /data/local/tmp/speech
for f in tokens.txt decoder.onnx joiner.onnx encoder.int8.onnx manifest.json; do $ADB push -q $SRC/$f /data/local/tmp/speech/; done
for pkg in ru.margin.app ru.margin.app.debug; do
  $ADB shell pm path $pkg >/dev/null 2>&1 || continue
  $ADB shell am force-stop $pkg
  uid=$($ADB shell stat -c %u /data/data/$pkg | tr -d '\r')
  d=/data/data/$pkg/files/models/speech
  $ADB shell "mkdir -p $d/$VER && cp /data/local/tmp/speech/* $d/$VER/ && echo $VER > $d/current && chown -R $uid:$uid /data/data/$pkg/files/models && restorecon -R /data/data/$pkg/files/models"
  echo "$pkg: model in place"
done
$ADB shell rm -rf /data/local/tmp/speech
