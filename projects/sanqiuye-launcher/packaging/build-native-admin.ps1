[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$NativeExecutable,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$JdkHome=$env:JAVA_HOME,
    [string]$Python='python'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=Split-Path -Parent $PSScriptRoot
$destination=[IO.Path]::GetFullPath($OutputDirectory)
$publicOutputRoot=[IO.Path]::GetFullPath((Join-Path $repo 'target/portable'))+[IO.Path]::DirectorySeparatorChar
if(-not $destination.StartsWith($publicOutputRoot,[StringComparison]::OrdinalIgnoreCase)){throw '输出必须位于项目target/portable的新子目录'}
if((Test-Path -LiteralPath $destination) -or (Test-Path -LiteralPath ($destination+'.zip'))){throw '管理输出目录或ZIP已存在，请保留原件并使用新的输出名称'}
$native=[IO.Path]::GetFullPath($NativeExecutable)
$jlink=Join-Path $JdkHome 'bin/jlink.exe'
if(-not(Test-Path -LiteralPath $native -PathType Leaf)){throw '管理原生EXE缺失，请先完成Tauri正式构建'}
if(-not(Test-Path -LiteralPath $jlink -PathType Leaf)){throw '需要带jlink的JDK21'}
if((Get-Content -Raw -LiteralPath (Join-Path $JdkHome 'release')) -notmatch 'JAVA_VERSION="21\.'){throw '管理随包运行时仅接受JDK21'}
Push-Location $repo
try {
    $identity=(& node packaging/build-identity.mjs --print).Trim()
    if($LASTEXITCODE -ne 0 -or $identity -notmatch '^[a-f0-9]{64}$'){throw '无法取得源码身份'}
    & node packaging/check-build-resources.mjs admin-desktop
    if($LASTEXITCODE -ne 0){throw '管理界面资源身份不一致'}
    & $Python -X utf8 packaging/check-native-admin.py --exe $native --expected $identity
    if($LASTEXITCODE -ne 0){throw '管理EXE身份或GUI头无效'}
    $admin=Join-Path $repo 'management-cli/target/dfs-admin.jar'
    $bootstrap=Join-Path $repo 'bootstrap-agent/target/bootstrap-agent-0.1.0-SNAPSHOT.jar'
    & $Python -X utf8 packaging/check-artifact-identity.py --expected $identity --admin-jar $admin --bootstrap-jar $bootstrap
    if($LASTEXITCODE -ne 0){throw '管理Java产物身份不一致'}
    New-Item -ItemType Directory -Path (Join-Path $destination 'core/support') | Out-Null
    Copy-Item -LiteralPath $native -Destination (Join-Path $destination '三秋叶管理端.exe')
    Copy-Item -LiteralPath $admin -Destination (Join-Path $destination 'core/dfs-admin.jar')
    Copy-Item -LiteralPath $bootstrap -Destination (Join-Path $destination 'core/support/bootstrap-agent.jar')
    Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination (Join-Path $destination 'LICENSE')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/ADMIN-NATIVE.md') -Destination (Join-Path $destination '使用说明.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/CUSTOM-INTRO-ACCOUNTS.md') -Destination (Join-Path $destination '自定义开屏与账号.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/CLOUD-ACCOUNTS.md') -Destination (Join-Path $destination 'CLOUD-ACCOUNTS.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/CUSTOM-INTRO-ACCOUNTS.md') -Destination (Join-Path $destination 'CUSTOM-INTRO-ACCOUNTS.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/SERVER-INTERFACES.md') -Destination (Join-Path $destination 'SERVER-INTERFACES.md')
    Copy-Item -LiteralPath (Join-Path $repo 'packaging/cloud/admin.env.example') -Destination (Join-Path $destination '账号配置.env.example')
    & $Python -X utf8 packaging/check-artifact-identity.py --expected $identity --core-directory (Join-Path $destination 'core') --write-core-info
    if($LASTEXITCODE -ne 0){throw '管理核心封装校验失败'}
    Copy-Item -LiteralPath (Join-Path $repo 'docs/SERVER-MOCK.md') -Destination (Join-Path $destination '离线演练说明.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/third-party') -Destination (Join-Path $destination 'third-party') -Recurse
    & $jlink --add-modules 'java.naming,java.net.http,java.sql,jdk.httpserver,jdk.crypto.ec,jdk.unsupported' --strip-debug --no-man-pages --no-header-files --output (Join-Path $destination 'core/runtime')
    if($LASTEXITCODE -ne 0){throw '管理随包Java生成失败，保留失败目录供检查'}
    & (Join-Path $destination 'core/runtime/bin/java.exe') -jar (Join-Path $destination 'core/dfs-admin.jar') --version
    if($LASTEXITCODE -ne 0){throw '随包管理核心无法实际启动'}
    $hashes=[ordered]@{}
    foreach($file in Get-ChildItem -LiteralPath $destination -Recurse -File | Sort-Object FullName){$relative=$file.FullName.Substring($destination.Length+1).Replace('\','/');$hashes[$relative]=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
    $metadata=[ordered]@{schemaVersion=1;product='三秋叶管理端';buildId=$identity;framework='Tauri';platform='windows-x64';kind='portable-test';signed=$false;javaBundled=$true;selfUpdateConfigured=$false;files=$hashes}
    [IO.File]::WriteAllText((Join-Path $destination 'native-build.json'),($metadata|ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::CreateFromDirectory($destination,($destination+'.zip'),[IO.Compression.CompressionLevel]::Optimal,$false,[Text.Encoding]::UTF8)
    Write-Output "管理便携包：$destination"
    Write-Output "构建身份：$identity"
    Write-Output ('ZIP SHA-256：'+(Get-FileHash -LiteralPath ($destination+'.zip') -Algorithm SHA256).Hash.ToLowerInvariant())
} finally {Pop-Location}
