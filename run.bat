@echo off
rem Run the program. All arguments are passed to sorting.Main.
rem   run.bat --input "7,12,19,3,18,4,2,6,15,8"
setlocal
cd /d "%~dp0"
chcp 65001 >nul
if not exist out (
  echo Алдаа: out\ олдсонгүй. Эхлээд build.bat ажиллуулна уу.
  exit /b 1
)
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out sorting.Main %*
