@echo off
if exist build (
    set /p choice="The 'build' directory already exists. Removing it will delete previous build artifacts. Continue? (y/n): "
    if /i "%choice%"=="y" (
        gradle clean
        echo Previous build directory removed.
    ) else (
        echo Aborted.
        exit /b 1
    )
)

gradle build
