@echo off
rem Polycore test helper: runs "gradlew build" and writes the output to build\polycore-test\build.log
rem The final exit code is written to build\polycore-test\build.status (RUNNING while in progress).
cd /d "%~dp0..\.."
set JAVA_HOME=
if not exist build\polycore-test mkdir build\polycore-test
echo RUNNING> build\polycore-test\build.status
title Polycore - build
call gradlew.bat build --console=plain > build\polycore-test\build.log 2>&1
>build\polycore-test\build.status echo %ERRORLEVEL%
