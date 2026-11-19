#!/usr/bin/env sh
# Runs the combat-logic tests: the REAL controller sources compiled against tiny stand-ins for the Minecraft types.
# Needs a JDK (javac) and python3. Does not need Minecraft or Gradle.
set -e
cd "$(dirname "$0")"
SRC=../src/main/java/dev/pvpai
W=$(mktemp -d)
mkdir -p "$W/dev/pvpai/config" "$W/dev/pvpai/core" "$W/dev/pvpai/act" "$W/dev/pvpai/observe" "$W/dev/pvpai/rules"
cp -r stubs/* "$W/"
cp Main.java "$W/Main.java"
for f in AccentColor InputMode JumpInput JumpResetMode Kit Strategy TotemMethod UiFont WatermarkStyle HudCorner; do cp $SRC/config/$f.java "$W/dev/pvpai/config/"; done
cp $SRC/core/AiMode.java "$W/dev/pvpai/core/"
cp $SRC/act/Action.java "$W/dev/pvpai/act/"
cp $SRC/observe/FightTracker.java "$W/dev/pvpai/observe/"
for f in StrictController LungeController MaceController Utility; do cp $SRC/rules/$f.java "$W/dev/pvpai/rules/"; done
python3 - "$SRC" "$W" <<'PY'
import sys
src, w = sys.argv[1], sys.argv[2]
m = open(src + '/config/ModConfig.java').read()
body = m[m.index('    public int configVersion'):m.index('    public static ModConfig get()')]
open(w + '/dev/pvpai/config/ModConfig.java', 'w').write('package dev.pvpai.config;\nimport dev.pvpai.core.AiMode;\nimport java.util.ArrayList;\nimport java.util.List;\npublic final class ModConfig {\n' + body + '}\n')
c = open(src + '/observe/Ctx.java').read()
fields = c[c.index('    public float yaw, pitch'):c.index('    public static Ctx build(')]
open(w + '/dev/pvpai/observe/Ctx.java', 'w').write('package dev.pvpai.observe;\npublic final class Ctx {\n' + fields + '}\n')
PY
javac -d "$W/out" $(find "$W" -name '*.java')
java -cp "$W/out" Main
