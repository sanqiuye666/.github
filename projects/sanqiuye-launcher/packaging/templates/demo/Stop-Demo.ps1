$ErrorActionPreference = 'Stop'
$demoRoot = $PSScriptRoot
$jar = Join-Path $demoRoot 'sanqiuye-demo.jar'
$dataRoot = Join-Path $demoRoot 'demo-data'
$stateFile = Join-Path $dataRoot 'demo-process.json'
if (-not (Test-Path -LiteralPath $stateFile)) { Write-Output 'Demo 尚未启动。'; exit 0 }
$state = Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json
$child = Get-Process -Id $state.pid -ErrorAction SilentlyContinue
if (-not $child) { Write-Output 'Demo 已关闭。'; exit 0 }
$command = (Get-CimInstance Win32_Process -Filter "ProcessId=$($state.pid)").CommandLine
if ($child.StartTime.ToUniversalTime().ToString('o') -ne $state.startedAt -or -not $command -or -not $command.Contains($jar) -or -not $command.Contains($dataRoot)) { throw '进程归属不匹配，未关闭任何进程。' }
$headers = @{'X-SQY-Client'='launcher'}
$session = Invoke-RestMethod -Uri ($state.url + 'api/launcher/v1/session') -Headers $headers
$headers['X-SQY-Session'] = $session.sessionToken
$instances = Invoke-RestMethod -Uri ($state.url + 'api/launcher/v1/instances') -Headers $headers
foreach ($instance in $instances.instances) {
    $runs = Invoke-RestMethod -Uri ($state.url + 'api/launcher/v1/launches?instanceId=' + [Uri]::EscapeDataString($instance.id)) -Headers $headers
    if (@($runs.launches | Where-Object { $_.state -in @('PREPARING','STARTING','RUNNING','STOPPING') }).Count -gt 0) { throw '游戏正在运行或准备，请先正常退出游戏，再关闭 Demo。' }
}
$offset = 0
do {
    $tasks = Invoke-RestMethod -Uri ($state.url + "api/launcher/v1/tasks?offset=$offset&limit=100") -Headers $headers
    if (@($tasks.tasks | Where-Object { $_.state -in @('QUEUED','RESOLVING','DOWNLOADING','VERIFYING') }).Count -gt 0) { throw '文件任务尚未结束，请先完成或取消，再关闭 Demo。' }
    $offset += 100
} while ($offset -lt $tasks.total)
Stop-Process -Id $child.Id
Write-Output 'Demo 已关闭，实例和设置已保留。'
