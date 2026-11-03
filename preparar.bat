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

rem O Maven precisa de um JAVA_HOME valido. Se estiver ausente ou apontar para uma pasta
rem que nao existe mais, usa o mesmo Java que o comando "java" ja enxerga.
if not exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_HOME="
    for /f "tokens=1,* delims==" %%a in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /C:"java.home"') do for /f "tokens=*" %%c in ("%%b") do set "JAVA_HOME=%%c"
)
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo Nao foi encontrado o compilador do Java. Instale o JDK 21 ou mais novo, nao so o JRE.
    pause
    exit /b 1
)

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
