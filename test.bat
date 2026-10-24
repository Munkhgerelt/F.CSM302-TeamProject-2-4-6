@echo off
rem Build the application, compile tests\TestRunner.java and run all automated tests.
rem The result table is written to tests\results\test-results.md.
setlocal
cd /d "%~dp0"
chcp 65001 >nul

call build.bat
if errorlevel 1 exit /b 1
if exist out-test rmdir /s /q out-test
mkdir out-test
javac --release 17 -encoding UTF-8 -cp out -d out-test tests\TestRunner.java
if errorlevel 1 (
  echo Test build FAILED
  exit /b 1
)
java -cp "out;out-test" TestRunner "%cd%"
exit /b %errorlevel%
