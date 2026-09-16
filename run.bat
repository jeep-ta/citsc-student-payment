@echo off
cd /d "%~dp0"

if "%1"=="rebuild" (
    echo Rebuilding project...
    call mvn package -DskipTests
) else if not exist "target\student-payment-db-1.0-SNAPSHOT.jar" (
    echo Application JAR not found. Building project...
    call mvn package -DskipTests
    if not exist "target\student-payment-db-1.0-SNAPSHOT.jar" (
        echo Build failed or JAR file was not created.
        pause
        exit /b 1
    )
)

start "" javaw -jar target\student-payment-db-1.0-SNAPSHOT.jar
