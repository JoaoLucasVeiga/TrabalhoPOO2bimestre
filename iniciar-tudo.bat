@echo off
cd /d "%~dp0"
call preparar.bat
if errorlevel 1 exit /b 1
start "Servidor do chat" cmd /k java -cp target\chat-multiusuario-1.0.0.jar br.unicesumar.chat.ServidorMain 5000
timeout /t 3 /nobreak >nul
start "" javaw -cp target\chat-multiusuario-1.0.0.jar br.unicesumar.chat.ClienteGuiMain
start "" javaw -cp target\chat-multiusuario-1.0.0.jar br.unicesumar.chat.ClienteGuiMain
start "" javaw -cp target\chat-multiusuario-1.0.0.jar br.unicesumar.chat.ClienteGuiMain
