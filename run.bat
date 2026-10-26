@echo off
rem Run the program. All arguments are passed to sorting.Main.
rem   run.bat --input "7,12,19,3,18,4,2,6,15,8"
setlocal EnableDelayedExpansion
cd /d "%~dp0"
chcp 65001 >nul
set REQUIRED_JAVA=17
if not exist out (
  echo Алдаа: out\ олдсонгүй. Эхлээд build.bat ажиллуулна уу.
  exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
  echo Алдаа: java олдсонгүй. JDK %REQUIRED_JAVA%+ суулгана уу: https://adoptium.net
  exit /b 1
)
set JMAJOR=0
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JV=%%~v
for /f "tokens=1 delims=." %%m in ("!JV!") do set JMAJOR=%%m
if "!JMAJOR!"=="1" set JMAJOR=8
if !JMAJOR! LSS %REQUIRED_JAVA% (
  echo Алдаа: Java %REQUIRED_JAVA%+ хэрэгтэй, одоогийн java хувилбар: !JV!
  exit /b 1
)
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out sorting.Main %*
