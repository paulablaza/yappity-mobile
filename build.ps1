# Builds bin\Yappity-<version>.apk with the Android command line tools. No Gradle, no Android Studio.
# Needs a JDK (javac) and the Android SDK with build-tools 35.0.0 and platforms;android-35.
#   powershell -ExecutionPolicy Bypass -File build.ps1            build only
#   powershell -ExecutionPolicy Bypass -File build.ps1 -Install   build and install on the phone over adb
param([switch]$Install)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$bt = Join-Path $sdk 'build-tools\35.0.0'
$androidJar = Join-Path $sdk 'platforms\android-35\android.jar'
$exe = if ($env:OS -eq 'Windows_NT') { '.exe' } else { '' }
$bat = if ($env:OS -eq 'Windows_NT') { '.bat' } else { '' }
$aapt2 = Join-Path $bt "aapt2$exe"
$zipalign = Join-Path $bt "zipalign$exe"
$d8 = Join-Path $bt "d8$bat"
$apksigner = Join-Path $bt "apksigner$bat"

$javac = 'javac'
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac$exe"))) { $javac = Join-Path $env:JAVA_HOME "bin\javac$exe" }
elseif (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    $jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -Last 1
    if ($jdk) { $javac = Join-Path $jdk.FullName 'bin\javac.exe'; $env:JAVA_HOME = $jdk.FullName }
}
$keytool = Join-Path (Split-Path $javac) "keytool$exe"

function Run($file, [string[]]$argv) {
    & $file @argv
    if ($LASTEXITCODE -ne 0) { throw "$file failed ($LASTEXITCODE)" }
}

$version = (Select-String -Path 'AndroidManifest.xml' -Pattern 'android:versionName="([^"]+)"').Matches[0].Groups[1].Value
$b = 'build'
if (Test-Path $b) { Remove-Item -Recurse -Force $b }
New-Item -ItemType Directory -Force "$b\res", "$b\gen", "$b\classes", "$b\dex", 'bin' | Out-Null

Write-Host "Resources"
Run $aapt2 @('compile', '--dir', 'res', '-o', "$b\res.zip")
Run $aapt2 @('link', '-I', $androidJar, '--manifest', 'AndroidManifest.xml', '-o', "$b\unsigned.apk",
    '--java', "$b\gen", '--min-sdk-version', '26', '--target-sdk-version', '35', "$b\res.zip")

Write-Host "Java"
$sources = @(Get-ChildItem -Recurse -Filter *.java 'src', "$b\gen" | ForEach-Object { $_.FullName })
Run $javac (@('-nowarn', '-Xlint:-options', '-encoding', 'UTF-8', '-source', '8', '-target', '8',
    '-bootclasspath', $androidJar, '-d', "$b\classes") + $sources)

Write-Host "Dex"
$classes = @(Get-ChildItem -Recurse -Filter *.class "$b\classes" | ForEach-Object { $_.FullName })
Run $d8 (@('--release', '--min-api', '26', '--lib', $androidJar, '--output', "$b\dex") + $classes)

Write-Host "Package"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open((Resolve-Path "$b\unsigned.apk"), 'Update')
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Resolve-Path "$b\dex\classes.dex"), 'classes.dex') | Out-Null
$zip.Dispose()
Run $zipalign @('-f', '-p', '4', "$b\unsigned.apk", "$b\aligned.apk")

# Signing. Release builds use YAPPITY_KEYSTORE / YAPPITY_KEY_PASS. Without them a local test key in keys\ is used.
$ks = $env:YAPPITY_KEYSTORE
$pass = $env:YAPPITY_KEY_PASS
if (-not $ks) {
    $ks = 'keys\debug.jks'; $pass = 'android'
    if (-not (Test-Path $ks)) {
        New-Item -ItemType Directory -Force 'keys' | Out-Null
        Run $keytool @('-genkeypair', '-keystore', $ks, '-alias', 'yappity', '-keyalg', 'RSA', '-keysize', '2048',
            '-validity', '10000', '-storepass', $pass, '-keypass', $pass, '-dname', 'CN=Yappity Test')
    }
}
$out = "bin\Yappity-$version.apk"
Run $apksigner @('sign', '--ks', $ks, '--ks-pass', "pass:$pass", '--ks-key-alias', 'yappity', '--out', $out, "$b\aligned.apk")
if (Test-Path "$out.idsig") { Remove-Item "$out.idsig" }
$kb = [math]::Round((Get-Item $out).Length / 1KB)
Write-Host "Built $out ($kb KB)"

if ($Install) {
    $adb = 'adb'
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        foreach ($p in @("$sdk\platform-tools\adb.exe", "$env:LOCALAPPDATA\phone-harness-tools\platform-tools\adb.exe")) { if (Test-Path $p) { $adb = $p } }
    }
    Run $adb @('install', '-r', $out)
}
