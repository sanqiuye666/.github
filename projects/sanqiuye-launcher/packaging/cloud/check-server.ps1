[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$ConnectionFile)
$ErrorActionPreference='Stop'
$sqyConnection=Get-Content -LiteralPath $ConnectionFile -Raw | ConvertFrom-Json
if($sqyConnection.host -notmatch '^[A-Za-z0-9.:-]+$' -or $sqyConnection.user -notmatch '^[a-z_][a-z0-9_-]*$' -or [int]$sqyConnection.port -lt 1 -or [int]$sqyConnection.port -gt 65535){throw '连接配置格式无效'}
$sqyKey=(Resolve-Path -LiteralPath $sqyConnection.privateKey).Path
$sqyTarget=$sqyConnection.user+'@'+$sqyConnection.host
$sqyArguments=@('-i',$sqyKey,'-p',[string]$sqyConnection.port,'-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=10',$sqyTarget)
Write-Output '只读检查Java运行时：'
& ssh @sqyArguments java -version
$sqyJavaCode=$LASTEXITCODE
Write-Output ('Java检查退出码：'+$sqyJavaCode)
Write-Output '只读检查监听端口：'
& ssh @sqyArguments ss -ltn
if($LASTEXITCODE -ne 0){throw 'SSH只读检查未完成；请保留错误，不要发送任何凭据'}
Write-Output '检查结束；没有更改服务器服务或文件。'
