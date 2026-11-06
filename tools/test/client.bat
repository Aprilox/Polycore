@echo off
rem Polycore test helper: runs "gradlew runClient" and writes the output to build\polycore-test\client.log
rem The final exit code is written to build\polycore-test\client.status (RUNNING while in progress).
cd /d "%~dp0..\.."
set JAVA_HOME=
if not exist build\polycore-test mkdir build\polycore-test
echo RUNNING> build\polycore-test\client.status
title Polycore - runClient
call gradlew.bat runClient --console=plain > build\polycore-test\client.log 2>&1
echo %ERRORLEVEL%> build\polycore-test\client.status
