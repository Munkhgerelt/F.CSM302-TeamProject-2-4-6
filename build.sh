#!/usr/bin/env bash
# Compile all sources into out/ (requires JDK 17+). No external dependencies.
set -euo pipefail
cd "$(dirname "$0")"

REQUIRED_JAVA=17

# --- check that a suitable JDK (javac) is available -------------------------
if ! command -v javac >/dev/null 2>&1; then
  echo "Алдаа: javac олдсонгүй. JDK ${REQUIRED_JAVA} буюу түүнээс дээш хувилбар суулгана уу." >&2
  echo "  macOS : brew install --cask temurin@${REQUIRED_JAVA}" >&2
  echo "  Linux : sudo apt install openjdk-${REQUIRED_JAVA}-jdk   (эсвэл https://adoptium.net)" >&2
  exit 1
fi
JAVAC_VER=$(javac -version 2>&1 | grep -Eo 'javac [0-9]+' | head -1 | awk '{print $2}')
if [ "${JAVAC_VER:-0}" = "1" ]; then JAVAC_VER=8; fi   # "javac 1.8.0_xx" means Java 8
if [ "${JAVAC_VER:-0}" -lt "$REQUIRED_JAVA" ]; then
  echo "Алдаа: JDK ${REQUIRED_JAVA}+ хэрэгтэй, гэтэл одоогийн javac хувилбар: ${JAVAC_VER:-тодорхойгүй} ($(command -v javac))." >&2
  echo "  macOS : brew install --cask temurin@${REQUIRED_JAVA}" >&2
  echo "          export JAVA_HOME=\$(/usr/libexec/java_home -v ${REQUIRED_JAVA})" >&2
  echo "  Linux : sudo apt install openjdk-${REQUIRED_JAVA}-jdk" >&2
  echo "  Дараа нь 'javac -version' ${REQUIRED_JAVA}+ гэж харуулж байгааг шалгаад дахин ./build.sh ажиллуулна." >&2
  exit 1
fi

# --- compile -----------------------------------------------------------------
rm -rf out
mkdir -p out
find src/main/java -name '*.java' > sources.txt
javac --release "$REQUIRED_JAVA" -encoding UTF-8 -d out @sources.txt
rm -f sources.txt
if [ -d src/main/resources ]; then
  cp -R src/main/resources/. out/
fi
echo "Build OK (javac ${JAVAC_VER}) -> out/"
