#!/usr/bin/env bash
# 把本机 ROM 目录同步进 app assets（构建期步骤）。
# ROM 本体不入 git（见 .gitignore），换机器构建前需重新执行本脚本。
#
# 用法：scripts/sync-bundled-roms.sh [ROM 目录]
#   ROM 目录默认取环境变量 HUFFCART_ROMS，再默认 ~/Desktop/RIns/nesRoms/roms
# 源文件无论有无扩展名，一律以 <原名>.nes 落入 assets（游戏库按 .nes 扫描）；
# 非 iNES 头（NES\x1a）的文件跳过并提示。
set -euo pipefail

SRC="${1:-${HUFFCART_ROMS:-$HOME/Desktop/RIns/nesRoms/roms}}"
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/roms"

if [ ! -d "$SRC" ]; then
  echo "ROM 源目录不存在: $SRC" >&2
  exit 1
fi
mkdir -p "$DEST"

count=0
skipped=0
for f in "$SRC"/*; do
  [ -f "$f" ] || continue
  head=$(head -c 4 "$f" | od -An -tx1 | tr -d ' \n')
  if [ "$head" != "4e45531a" ]; then
    echo "跳过（非 iNES）: $(basename "$f")"
    skipped=$((skipped+1))
    continue
  fi
  base="$(basename "$f")"
  if [[ "$base" == *.nes || "$base" == *.NES ]]; then
    name="$base"
  else
    name="$base.nes"
  fi
  cp -f "$f" "$DEST/$name"
  count=$((count+1))
done

echo "已同步 $count 个 ROM 到 $DEST（跳过 $skipped 个）"
du -sh "$DEST"
