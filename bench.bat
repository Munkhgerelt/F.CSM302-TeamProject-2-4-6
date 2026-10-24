@echo off
rem Timing measurements for the report. Results: tests\results\benchmark.md and benchmark.csv
setlocal
cd /d "%~dp0"
chcp 65001 >nul
call build.bat
if errorlevel 1 exit /b 1
if exist out-test rmdir /s /q out-test
mkdir out-test
javac --release 17 -encoding UTF-8 -cp out -d out-test tests\Benchmark.java
if errorlevel 1 exit /b 1
java -cp "out;out-test" Benchmark "%cd%" %*
exit /b %errorlevel%
