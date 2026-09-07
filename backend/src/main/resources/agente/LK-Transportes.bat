@echo off
title LK Transportes - Agente de Telemetria
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "lk-telemetria-launcher.ps1"
