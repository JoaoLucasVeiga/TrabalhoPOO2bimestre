@echo off
cd /d "%~dp0"
call preparar.bat
if errorlevel 1 exit /b 1
title Cliente do chat
java -cp target\chat-multiusuario-1.0.0.jar br.unicesumar.chat.ClienteTerminalMain localhost 5000
pause
