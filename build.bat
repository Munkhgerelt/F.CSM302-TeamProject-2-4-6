@echo off
rem Compile all sources into out\ (requires JDK 17+). No external dependencies.
setlocal EnableDelayedExpansion
cd /d "%~dp0"
chcp 65001 >nul
set REQUIRED_JAVA=17

where javac >nul 2>nul
if errorlevel 1 (
  echo Алдаа: javac олдсонгүй. JDK %REQUIRED_JAVA%+ суулгана уу: https://adoptium.net
  exit /b 1
)
set JMAJOR=0
for /f "tokens=2" %%v in ('javac -version 2^>^&1 ^| findstr /b "javac"') do set JV=%%v
for /f "tokens=1 delims=." %%m in ("!JV!") do set JMAJOR=%%m
if "!JMAJOR!"=="1" set JMAJOR=8
if !JMAJOR! LSS %REQUIRED_JAVA% (
  echo Алдаа: JDK %REQUIRED_JAVA%+ хэрэгтэй, одоогийн javac хувилбар: !JV!
  echo   https://adoptium.net -ээс Temurin %REQUIRED_JAVA% суулгаад PATH/JAVA_HOME-оо шинэчилнэ үү.
  exit /b 1
)

if exist out rmdir /s /q out
mkdir out
dir /s /b src\main\java\*.java > sources.txt
javac --release %REQUIRED_JAVA% -encoding UTF-8 -d out @sources.txt
if errorlevel 1 (
  del sources.txt
  echo Build FAILED
  exit /b 1
)
del sources.txt
if exist src\main\resources xcopy /e /i /q /y src\main\resources out >nul
echo Build OK (javac !JV!) -^> out\
