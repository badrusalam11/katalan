@echo off
REM Bump version script for katalan project (Windows)
REM Usage: bump-version.bat [patch|minor|major]
REM - patch: 1.0.0 -> 1.0.1
REM - minor: 1.0.0 -> 1.1.0
REM - major: 1.0.0 -> 2.0.0

setlocal enabledelayedexpansion

REM Check if bump type is provided
set BUMP_TYPE=%1
if "%BUMP_TYPE%"=="" set BUMP_TYPE=patch

if /i not "%BUMP_TYPE%"=="patch" (
    if /i not "%BUMP_TYPE%"=="minor" (
        if /i not "%BUMP_TYPE%"=="major" (
            echo [91m❌ Invalid bump type: %BUMP_TYPE%[0m
            echo Usage: %0 [patch^|minor^|major]
            exit /b 1
        )
    )
)

REM Get current version from pom.xml
for /f "tokens=2 delims=<>" %%a in ('findstr /r "<version>[0-9]*\.[0-9]*\.[0-9]*</version>" pom.xml') do (
    set CURRENT_VERSION=%%a
    goto :version_found
)

:version_found
if "%CURRENT_VERSION%"=="" (
    echo [91m❌ Could not extract current version from pom.xml[0m
    exit /b 1
)

echo [96m📦 Current version: %CURRENT_VERSION%[0m

REM Parse version parts
for /f "tokens=1,2,3 delims=." %%a in ("%CURRENT_VERSION%") do (
    set MAJOR=%%a
    set MINOR=%%b
    set PATCH=%%c
)

REM Bump version based on type
if /i "%BUMP_TYPE%"=="patch" (
    set /a PATCH+=1
) else if /i "%BUMP_TYPE%"=="minor" (
    set /a MINOR+=1
    set PATCH=0
) else if /i "%BUMP_TYPE%"=="major" (
    set /a MAJOR+=1
    set MINOR=0
    set PATCH=0
)

set NEW_VERSION=%MAJOR%.%MINOR%.%PATCH%
echo [92m🚀 New version: %NEW_VERSION%[0m

REM Update version in pom.xml
echo [93m📝 Updating pom.xml...[0m
powershell -Command "(Get-Content pom.xml) -replace '<version>%CURRENT_VERSION%</version>', '<version>%NEW_VERSION%</version>' | Set-Content pom.xml"

REM Verify the change
for /f "tokens=2 delims=<>" %%a in ('findstr /r "<version>[0-9]*\.[0-9]*\.[0-9]*</version>" pom.xml') do (
    set UPDATED_VERSION=%%a
    goto :verify_done
)

:verify_done
if not "%UPDATED_VERSION%"=="%NEW_VERSION%" (
    echo [91m❌ Version update failed. Expected %NEW_VERSION%, got %UPDATED_VERSION%[0m
    exit /b 1
)

echo [92m✅ Version updated in pom.xml[0m

REM Git operations
echo [96m📌 Creating git tag v%NEW_VERSION%...[0m
git add pom.xml
git commit -m "Bump version to %NEW_VERSION%"
git tag "v%NEW_VERSION%"

echo [96m🚢 Pushing to remote...[0m
git push origin main
git push origin "v%NEW_VERSION%"

echo.
echo [92m✨ Version bump completed successfully![0m
echo    Old version: %CURRENT_VERSION%
echo    New version: %NEW_VERSION%
echo    Tag: v%NEW_VERSION%
echo.
echo [93m🎉 GitHub Actions will now build and create a release automatically.[0m

endlocal
