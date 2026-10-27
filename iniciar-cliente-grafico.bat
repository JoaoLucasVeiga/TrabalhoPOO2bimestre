@echo off
cd /d "%~dp0"
call preparar.bat
if errorlevel 1 exit /b 1
start "" javaw -cp target\chat-multiusuario-1.0.0.jar br.unicesumar.chat.ClienteGuiMain
