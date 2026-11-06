@echo off
rem Polycore test helper: runs "gradlew runGameTestServer" and writes the output to build\polycore-test\gametests.log
rem The final exit code is written to build\polycore-test\gametests.status (RUNNING while in progress).
cd /d "%~dp0..\.."
set JAVA_HOME=
if not exist build\polycore-test mkdir build\polycore-test
echo RUNNING> build\polycore-test\gametests.status
title Polycore - runGameTestServer
call gradlew.bat runGameTestServer --console=plain > build\polycore-test\gametests.log 2>&1
echo %ERRORLEVEL%> build\polycore-test\gametests.status
