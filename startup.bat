@echo off
setlocal EnableDelayedExpansion

:: ─── Config ────────────────────────────────────────────────────────────────────
set APP_NAME=loyalty-wallet
set JAR_PATTERN=target\wallet-*.jar
set LOG_DIR=logs
set LOG_FILE=%LOG_DIR%\startup.log
if not defined SERVER_PORT set SERVER_PORT=8080
if not defined JAVA_OPTS   set JAVA_OPTS=-Xms256m -Xmx512m

:: ─── Handle stop command ────────────────────────────────────────────────────────
if /i "%~1"=="stop" goto :STOP

:: ─── Create log directory ───────────────────────────────────────────────────────
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

:: ═══════════════════════════════════════════════════════════════════════════════
call :SECTION "Checking prerequisites"
:: ═══════════════════════════════════════════════════════════════════════════════

:: Check Java
where java >nul 2>&1
if errorlevel 1 (
    call :ERROR "Java is not installed or not on PATH. Install Java 17+."
    exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set RAW_VER=%%v
)
set RAW_VER=%RAW_VER:"=%
for /f "delims=." %%m in ("%RAW_VER%") do set JAVA_MAJOR=%%m
if %JAVA_MAJOR% LSS 17 (
    call :ERROR "Java 17+ required. Found version: %JAVA_MAJOR%"
    exit /b 1
)
call :INFO "Java OK  (major version: %JAVA_MAJOR%)"

:: Check Maven
where mvn >nul 2>&1
if errorlevel 1 (
    call :ERROR "Maven is not installed or not on PATH."
    exit /b 1
)
for /f "delims=" %%v in ('mvn -version 2^>^&1 ^| findstr /i "Apache Maven"') do (
    call :INFO "%%v"
    goto :MAVEN_OK
)
:MAVEN_OK

:: ═══════════════════════════════════════════════════════════════════════════════
call :SECTION "Checking port %SERVER_PORT%"
:: ═══════════════════════════════════════════════════════════════════════════════

netstat -ano | findstr ":%SERVER_PORT% " | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
    call :WARN "Port %SERVER_PORT% is already in use."
    set /p KILL_ANS="  Kill the existing process and continue? [y/N]: "
    if /i "!KILL_ANS!"=="y" (
        for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%SERVER_PORT% " ^| findstr "LISTENING"') do (
            taskkill /PID %%p /F >nul 2>&1
            call :INFO "Killed PID %%p on port %SERVER_PORT%."
        )
    ) else (
        call :ERROR "Startup aborted. Free port %SERVER_PORT% and retry."
        exit /b 1
    )
) else (
    call :INFO "Port %SERVER_PORT% is available."
)

:: ═══════════════════════════════════════════════════════════════════════════════
call :SECTION "Building %APP_NAME%"
:: ═══════════════════════════════════════════════════════════════════════════════

call :INFO "Running: mvn clean package -DskipTests"
mvn clean package -DskipTests 2>&1 | powershell -NoProfile -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
if errorlevel 1 (
    call :ERROR "Maven build failed. Check %LOG_FILE% for details."
    exit /b 1
)
call :INFO "Build successful."

:: Locate JAR
set JAR_FILE=
for %%f in (%JAR_PATTERN%) do set JAR_FILE=%%f
if not defined JAR_FILE (
    call :ERROR "No JAR found matching '%JAR_PATTERN%'. Build may have failed."
    exit /b 1
)
call :INFO "JAR: %JAR_FILE%"

:: ═══════════════════════════════════════════════════════════════════════════════
call :SECTION "Starting %APP_NAME%"
:: ═══════════════════════════════════════════════════════════════════════════════

call :INFO "JVM opts : %JAVA_OPTS%"
call :INFO "Port     : %SERVER_PORT%"
call :INFO "Log file : %LOG_DIR%\%APP_NAME%.log"

:: Launch in a new window so this console stays interactive
start "%APP_NAME%" /min cmd /c ^
    "java %JAVA_OPTS% -jar %JAR_FILE% --server.port=%SERVER_PORT% >> %LOG_DIR%\%APP_NAME%.log 2>&1"

:: Give the JVM a moment to register its PID
timeout /t 2 /nobreak >nul

:: Capture PID of the java process listening on our port
set APP_PID=
:FIND_PID
for /f "tokens=5" %%p in ('netstat -ano 2^>nul ^| findstr ":%SERVER_PORT% " ^| findstr "LISTENING"') do (
    set APP_PID=%%p
)
if defined APP_PID (
    echo %APP_PID% > "%LOG_DIR%\%APP_NAME%.pid"
    call :INFO "PID %APP_PID% written to %LOG_DIR%\%APP_NAME%.pid"
) else (
    call :WARN "Could not resolve PID yet — app may still be starting."
)

:: ═══════════════════════════════════════════════════════════════════════════════
call :SECTION "Waiting for application to be ready"
:: ═══════════════════════════════════════════════════════════════════════════════

set HEALTH_URL=http://localhost:%SERVER_PORT%/v3/api-docs
set MAX_WAIT=20
set ATTEMPT=0

:HEALTH_LOOP
if %ATTEMPT% GEQ %MAX_WAIT% goto :TIMEOUT

curl -sf "%HEALTH_URL%" -o nul 2>nul
if not errorlevel 1 goto :READY

<nul set /p =.
timeout /t 3 /nobreak >nul
set /a ATTEMPT+=1
goto :HEALTH_LOOP

:TIMEOUT
echo.
call :ERROR "Application did not become ready in %MAX_WAIT% attempts. Check %LOG_DIR%\%APP_NAME%.log"
exit /b 1

:READY
echo.

:: ═══════════════════════════════════════════════════════════════════════════════
call :SECTION "Application is UP"
:: ═══════════════════════════════════════════════════════════════════════════════

echo.
echo   Swagger UI        ^>  http://localhost:%SERVER_PORT%/swagger-ui.html
echo   OpenAPI JSON      ^>  http://localhost:%SERVER_PORT%/v3/api-docs
echo   OpenAPI YAML      ^>  http://localhost:%SERVER_PORT%/v3/api-docs.yaml
echo.
echo   API Endpoints
echo   POST  http://localhost:%SERVER_PORT%/api/wallet/earn
echo   POST  http://localhost:%SERVER_PORT%/api/wallet/redeem
echo   GET   http://localhost:%SERVER_PORT%/api/wallet/{userId}
echo   GET   http://localhost:%SERVER_PORT%/api/wallet/{userId}/transactions
echo.
echo   Logs  ^>  %LOG_DIR%\%APP_NAME%.log
if defined APP_PID echo   PID   ^>  %APP_PID%  (saved in %LOG_DIR%\%APP_NAME%.pid^)
echo.
echo   To stop:  startup.bat stop
echo.

goto :EOF

:: ═══════════════════════════════════════════════════════════════════════════════
:STOP
:: ═══════════════════════════════════════════════════════════════════════════════
set PID_FILE=%LOG_DIR%\%APP_NAME%.pid
if exist "%PID_FILE%" (
    set /p STOP_PID=<"%PID_FILE%"
    taskkill /PID !STOP_PID! /F >nul 2>&1
    if errorlevel 1 (
        call :WARN "Process !STOP_PID! not found — may have already stopped."
    ) else (
        call :INFO "Stopped PID !STOP_PID!."
    )
    del "%PID_FILE%"
) else (
    call :WARN "No PID file found at %PID_FILE%."
)
goto :EOF

:: ═══════════════════════════════════════════════════════════════════════════════
:: Logging helpers
:: ═══════════════════════════════════════════════════════════════════════════════
:INFO
echo [INFO]  %~1
goto :EOF

:WARN
echo [WARN]  %~1
goto :EOF

:ERROR
echo [ERROR] %~1
goto :EOF

:SECTION
echo.
echo ==========================================
echo   %~1
echo ==========================================
goto :EOF
