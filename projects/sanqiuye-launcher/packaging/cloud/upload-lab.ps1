[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$ConnectionFile,[Parameter(Mandatory=$true)][string]$BundleDirectory)
$ErrorActionPreference='Stop'
$sqy=Get-Content -LiteralPath $ConnectionFile -Raw | ConvertFrom-Json
if($sqy.host -notmatch '^[A-Za-z0-9.:-]+$' -or $sqy.user -notmatch '^[a-z_][a-z0-9_-]*$' -or $sqy.remoteInbox -notmatch '^[A-Za-z0-9_-]+$' -or $sqy.remoteRoot -notmatch '^[A-Za-z0-9_-]+$'){throw '连接或专用目录配置无效'}
if([int]$sqy.apiPort -lt 1024 -or [int]$sqy.apiPort -gt 65535 -or $sqy.protectedPorts -contains [int]$sqy.apiPort){throw 'API端口无效或与保护端口冲突'}
$sqyKey=(Resolve-Path -LiteralPath $sqy.privateKey).Path
$sqyTarget=$sqy.user+'@'+$sqy.host
$sqySsh=@('-i',$sqyKey,'-p',[string]$sqy.port,'-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=10',$sqyTarget)
& ssh @sqySsh mkdir -p -- $sqy.remoteInbox
if($LASTEXITCODE -ne 0){throw '创建专用上传目录失败'}
$sqyFiles=@((Join-Path $BundleDirectory 'dfs-admin.jar'),(Join-Path $BundleDirectory 'serve-lab.sh'))
foreach($sqyFile in $sqyFiles){if(-not(Test-Path -LiteralPath $sqyFile -PathType Leaf)){throw '服务包缺少必要文件，请先完成构建'}}
& scp -i $sqyKey -P ([string]$sqy.port) -o BatchMode=yes -o StrictHostKeyChecking=yes @sqyFiles ($sqyTarget+':'+$sqy.remoteInbox+'/')
if($LASTEXITCODE -ne 0){throw '服务包上传未完成'}
Write-Output '服务包已上传。下一条命令会在专用目录先备份再启动账号API；不会停止已有进程或改nginx/Minecraft：'
Write-Output ('ssh -i "'+$sqyKey+'" -p '+$sqy.port+' -o StrictHostKeyChecking=yes '+$sqyTarget+' bash '+$sqy.remoteInbox+'/serve-lab.sh '+$sqy.remoteRoot+' '+$sqy.apiPort+' '+$sqy.remoteInbox+'/dfs-admin.jar '+($sqy.protectedPorts -join ','))
