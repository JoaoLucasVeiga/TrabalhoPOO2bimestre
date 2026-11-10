@echo off
rem Mostra na pratica mensagens simultaneas: varios clientes enviam ao mesmo tempo e o programa
rem confere se todos receberam tudo, sem perda e na mesma ordem.
rem O servidor precisa estar rodando (iniciar-tudo.bat ou iniciar-servidor.bat). Se os clientes graficos
rem estiverem abertos na sala geral, voce ve as mensagens chegando ao vivo.
rem Opcional: demonstracao-simultanea.bat [servidor] [porta] [clientes] [mensagens] [sala]
cd /d "%~dp0"
call preparar.bat
if errorlevel 1 exit /b 1
java -cp target\chat-multiusuario-1.0.0.jar br.unicesumar.chat.DemonstracaoSimultaneaMain %*
echo.
pause
