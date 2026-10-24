#!/usr/bin/env bash
# Run the program. All arguments are passed to sorting.Main.
#   ./run.sh --input "7,12,19,3,18,4,2,6,15,8"
set -euo pipefail
cd "$(dirname "$0")"

REQUIRED_JAVA=17

if [ ! -d out ]; then
  echo "Алдаа: out/ олдсонгүй. Эхлээд ./build.sh ажиллуулна уу." >&2
  exit 1
fi
if ! command -v java >/dev/null 2>&1; then
  echo "Алдаа: java олдсонгүй. JDK ${REQUIRED_JAVA}+ суулгана уу (https://adoptium.net)." >&2
  exit 1
fi
JAVA_VER=$(java -version 2>&1 | grep -Eo 'version "[0-9]+' | head -1 | grep -Eo '[0-9]+$')
if [ "${JAVA_VER:-0}" = "1" ]; then JAVA_VER=8; fi
if [ "${JAVA_VER:-0}" -lt "$REQUIRED_JAVA" ]; then
  echo "Алдаа: Java ${REQUIRED_JAVA}+ хэрэгтэй, гэтэл одоогийн java хувилбар: ${JAVA_VER:-тодорхойгүй}." >&2
  echo "  macOS : export JAVA_HOME=\$(/usr/libexec/java_home -v ${REQUIRED_JAVA})" >&2
  exit 1
fi

exec java -Dfile.encoding=UTF-8 -cp out sorting.Main "$@"
