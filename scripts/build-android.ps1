param([switch]$Tablet,[switch]$Release,[switch]$Instrumentation,[switch]$Offline,[string]$GradleExecutable)
$ErrorActionPreference = 'Stop'
if ($Tablet -and $Release) { throw 'Choose one target variant.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$env:ANDROID_HOME) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
if (!$env:ANDROID_HOME) { throw 'Set ANDROID_HOME or ANDROID_SDK_ROOT.' }
if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $env:TEMP 'jieti-gradle' }
if (!$GradleExecutable) { $GradleExecutable = Join-Path $projectRoot 'android/gradlew.bat' }
$buildRoot = Join-Path $env:TEMP 'jieti-public-android-023'
New-Item -ItemType Directory -Path (Join-Path $buildRoot 'app') -Force | Out-Null
foreach ($name in @('settings.gradle.kts','build.gradle.kts','gradle.properties')) { Copy-Item -LiteralPath (Join-Path $projectRoot "android/$name") -Destination $buildRoot -Force }
foreach ($name in @('build.gradle.kts','proguard-rules.pro')) { Copy-Item -LiteralPath (Join-Path $projectRoot "android/app/$name") -Destination (Join-Path $buildRoot 'app') -Force }
Copy-Item -LiteralPath (Join-Path $projectRoot 'android/app/src') -Destination (Join-Path $buildRoot 'app') -Recurse -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'android/app/objectbox-models') -Destination (Join-Path $buildRoot 'app') -Recurse -Force
$variant = if ($Tablet) { 'Tablet' } elseif ($Release) { 'Release' } else { 'Debug' }
$tasks = @("test${variant}UnitTest","lint$variant","assemble$variant")
if ($Instrumentation) { $tasks += @('assembleDebug','assembleDebugAndroidTest') }
$options = @('--no-daemon')
if ($Offline) { $options += '--offline' }
if (!$Offline -and $env:HTTPS_PROXY) {
 $proxyUri = [uri]$env:HTTPS_PROXY
 if ($proxyUri.Host -and !$proxyUri.UserInfo) { $options += @("-Dhttps.proxyHost=$($proxyUri.Host)","-Dhttps.proxyPort=$($proxyUri.Port)","-Dhttp.proxyHost=$($proxyUri.Host)","-Dhttp.proxyPort=$($proxyUri.Port)") }
}
& $GradleExecutable -p $buildRoot @tasks @options
if ($LASTEXITCODE -ne 0) { throw 'Android verification failed.' }
$version = [regex]::Match((Get-Content -LiteralPath (Join-Path $projectRoot 'android/app/build.gradle.kts') -Raw), 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$outputDir = Join-Path $projectRoot 'release/android'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$artifact = if ($Tablet) { 'tablet/app-tablet.apk' } elseif ($Release) { 'release/app-release-unsigned.apk' } else { 'debug/app-debug.apk' }
$suffix = if ($Tablet) { 'tablet' } elseif ($Release) { 'release-unsigned' } else { 'debug' }
Copy-Item -LiteralPath (Join-Path $buildRoot "app/build/outputs/apk/$artifact") -Destination (Join-Path $outputDir "Jieti-$version-$suffix.apk") -Force
