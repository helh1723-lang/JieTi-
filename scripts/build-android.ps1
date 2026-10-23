param([switch]$Release)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$env:ANDROID_HOME) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
if (!$env:ANDROID_HOME) { throw 'Set ANDROID_HOME to your Android SDK directory.' }
$env:GRADLE_USER_HOME = Join-Path $env:TEMP 'jieti-gradle'
$gradleExecutable = Join-Path $projectRoot 'android\gradlew.bat'
if (!(Test-Path -LiteralPath $gradleExecutable)) { throw 'Install Gradle 8.10.2 or use android/gradlew.bat.' }
$buildRoot = Join-Path $env:TEMP 'jieti-android'
New-Item -ItemType Directory -Path (Join-Path $buildRoot 'app') -Force | Out-Null
foreach ($name in @('settings.gradle.kts','build.gradle.kts','gradle.properties')) { Copy-Item -LiteralPath (Join-Path $projectRoot "android\$name") -Destination $buildRoot -Force }
Copy-Item -LiteralPath (Join-Path $projectRoot 'android\app\build.gradle.kts') -Destination (Join-Path $buildRoot 'app') -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'android\app\src') -Destination (Join-Path $buildRoot 'app') -Recurse -Force
$objectModel = Join-Path $projectRoot 'android\app\objectbox-models'
if (Test-Path -LiteralPath $objectModel) { Copy-Item -LiteralPath $objectModel -Destination (Join-Path $buildRoot 'app') -Recurse -Force }
Push-Location $buildRoot
try {
 & $gradleExecutable testDebugUnitTest lintDebug assembleDebug --no-daemon
 if ($LASTEXITCODE -ne 0) { throw 'Android verification failed.' }
 $deliveryRoot = Join-Path $projectRoot 'release\android'
 New-Item -ItemType Directory -Path $deliveryRoot -Force | Out-Null
 Copy-Item -LiteralPath (Join-Path $buildRoot 'app\build\outputs\apk\debug\app-debug.apk') -Destination (Join-Path $deliveryRoot 'Jieti-0.1.1-debug.apk') -Force
 if (Test-Path -LiteralPath 'app\objectbox-models') { Copy-Item -LiteralPath 'app\objectbox-models' -Destination (Join-Path $projectRoot 'android\app') -Recurse -Force }
 if (Test-Path -LiteralPath 'app\schemas') { Copy-Item -LiteralPath 'app\schemas' -Destination (Join-Path $projectRoot 'android\app') -Recurse -Force }
 & $gradleExecutable wrapper --gradle-version 8.10.2 --no-validate-url --no-daemon
 if ($LASTEXITCODE -eq 0) { Copy-Item -LiteralPath 'gradle','gradlew','gradlew.bat' -Destination (Join-Path $projectRoot 'android') -Recurse -Force }
} finally { Pop-Location }
