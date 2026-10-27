@echo off
rem Compila o projeto na primeira vez (precisa de Java 21 e internet so nessa vez).
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
    echo Java nao encontrado. Instale o Java 21 ou mais novo e abra este arquivo de novo.
    pause
    exit /b 1
)
if exist "target\chat-multiusuario-1.0.0.jar" if exist "target\lib" exit /b 0
echo Compilando o projeto pela primeira vez. Isso pode levar alguns minutos...
call mvnw.cmd -B -q package -DskipTests
if errorlevel 1 (
    echo.
    echo Nao foi possivel compilar. Confira se o Java e a versao 21 ou mais nova ^(java -version^)
    echo e se ha conexao com a internet, e tente de novo.
    pause
    exit /b 1
)
exit /b 0
