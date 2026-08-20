@echo off
title LK Transportes - Telemetria
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lk-telemetria.ps1" %*
if errorlevel 1 pause
