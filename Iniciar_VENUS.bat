@echo off
title VENUS - Iniciador
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Iniciar_VENUS.ps1"
pause
