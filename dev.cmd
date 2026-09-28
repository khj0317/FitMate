@echo off
chcp 65001 > nul
rem FitMate 개발 서버 한 번에 켜기: 이 파일을 더블클릭하세요. (끄기: Ctrl + C)
cd /d "%~dp0"
call npm.cmd run dev
pause
