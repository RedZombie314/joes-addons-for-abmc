<#
    启动一个「带调试端口」的浏览器，供 wiki-cdp.js 使用
    ============================================================================
    为什么需要它：Miraheze 站在 Cloudflare 的托管挑战后面，命令行 curl 一律 403；
    只有浏览器能通过挑战。所以 wiki-cdp.js 是通过 CDP（Chrome DevTools Protocol）
    把请求交给浏览器去发的——浏览器用哪个都行（Chrome / Edge 都是 Chromium）。

    用法：
        .\open-wiki-browser.ps1 -TempProfile       # ★推荐：持久独立配置（%LOCALAPPDATA%），登录一次长期有效
        .\open-wiki-browser.ps1                    # Edge 常态配置（注意下方限制，通常拿不到端口）
        .\open-wiki-browser.ps1 -UseMyLogin        # 试着复制你平时的 cookie（新版浏览器常因加密而无效）
        .\open-wiki-browser.ps1 -Browser Chrome    # 想用 Chrome
        .\open-wiki-browser.ps1 -Port 9333         # 换端口（同时给 wiki-cdp.js 设 CDP_PORT）

    关于「为什么不能直接连上我已经打开的浏览器」：
        Chromium（Edge/Chrome）有两条安全限制——
        ① 浏览器已在运行时，新命令的参数会被转交给已有进程并丢弃（只新开标签页）；
        ② 用**默认配置目录**启动时会直接忽略 --remote-debugging-port（M136+）。
        所以只能另起一个带参数的实例。又因为新版浏览器的 cookie 采用「应用绑定加密」，
        把常态配置的 cookie 复制出来通常**解不开**（表现为复制成功但仍显示未登录），
        因此最省事的做法是：用 -TempProfile 起一个持久独立实例，在里面**登录一次**。
        之后只要那个实例还开着就能直接操作；关掉了重跑本脚本即可（不用再登录）。

    搭配使用：
        node wiki-cdp.js check                     # 确认连上、看登录状态
        node wiki-cdp.js push  <目录>
        node wiki-cdp.js upload <映射.json>
    ============================================================================
#>
[CmdletBinding()]
param(
    [ValidateSet('Edge', 'Chrome')][string]$Browser = 'Edge',
    [int]$Port = 9222,
    [switch]$TempProfile,
    [switch]$UseMyLogin,
    [switch]$Yes,
    [string]$Url = 'https://jafa.miraheze.org/wiki/Special:Version'
)

$ErrorActionPreference = 'Stop'

function Get-BrowserPath([string]$which) {
    $cands = if ($which -eq 'Edge') {
        @("$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
          "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
          "$env:LOCALAPPDATA\Microsoft\Edge\Application\msedge.exe")
    } else {
        @("$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
          "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
          "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe")
    }
    foreach ($c in $cands) { if (Test-Path -LiteralPath $c) { return $c } }
    throw "找不到 $which 的可执行文件"
}

$exe = Get-BrowserPath $Browser
$procName = if ($Browser -eq 'Edge') { 'msedge' } else { 'chrome' }

# 1) 端口已经在监听？直接复用
$alive = & curl.exe -s -o NUL -w '%{http_code}' "http://127.0.0.1:$Port/json/version" 2>$null
if ($alive -eq '200') {
    $ver = (& curl.exe -s "http://127.0.0.1:$Port/json/version") | ConvertFrom-Json
    Write-Host "调试端口 $Port 已在监听（$($ver.Browser)），直接复用。" -ForegroundColor Green
} else {
    $args = @("--remote-debugging-port=$Port", '--remote-allow-origins=*',
              '--no-first-run', '--no-default-browser-check')
    if ($TempProfile) {
        $profileDir = Join-Path $env:LOCALAPPDATA ("jafa-" + $Browser.ToLower() + "-temp")
        $args += @("--user-data-dir=$profileDir", '--new-window', $Url)
        Write-Host "使用独立临时配置：$profileDir（首次需要在这个窗口里登录一次 wiki）" -ForegroundColor Yellow
    } elseif ($UseMyLogin) {
        # ★推荐：关掉浏览器 → 复制你平时的 cookie 与解密密钥 → 用这份副本启动
        # 这样既能被 CDP 连上（不是默认配置目录），又带着你的登录态（不用重新输密码）
        $running = Get-Process -Name $procName -ErrorAction SilentlyContinue
        if ($running) {
            Write-Host "$Browser 正在运行 —— 需要先关掉它才能复制 cookie（Edge 会独占锁定 Cookies 文件）。" -ForegroundColor Yellow
            $ans = if ($Yes) { 'y' } else { Read-Host "现在帮你关闭 $Browser？(y/N)" }
            if ($ans -ne 'y') { Write-Host "已取消。"; exit 1 }
            $running | ForEach-Object { try { $_.CloseMainWindow() | Out-Null } catch {} }
            Start-Sleep -Seconds 4
            $left = Get-Process -Name $procName -ErrorAction SilentlyContinue
            if ($left) { $left | Stop-Process -Force; Start-Sleep -Seconds 3 }
        }
        $userData = if ($Browser -eq 'Edge') { Join-Path $env:LOCALAPPDATA 'Microsoft\Edge\User Data' }
                    else { Join-Path $env:LOCALAPPDATA 'Google\Chrome\User Data' }
        $profileDir = Join-Path $env:LOCALAPPDATA ("jafa-" + $Browser.ToLower() + "-profile")
        New-Item -ItemType Directory -Force -Path (Join-Path $profileDir 'Default\Network') | Out-Null
        Copy-Item -LiteralPath (Join-Path $userData 'Local State') -Destination (Join-Path $profileDir 'Local State') -Force
        Copy-Item -LiteralPath (Join-Path $userData 'Default\Network\Cookies') -Destination (Join-Path $profileDir 'Default\Network\Cookies') -Force
        Write-Host "已复制登录态到：$profileDir" -ForegroundColor Green
        Write-Host "（可以重新打开你平时的 $Browser 了，两边互不影响）" -ForegroundColor Green
        $args += @("--user-data-dir=$profileDir", '--new-window', $Url)
    } else {
        # 用你平时的配置：注意 M136+ 对**默认配置目录**会忽略调试端口，所以这条路通常拿不到端口
        $args += '--restore-last-session'
        $args += @('--new-window', $Url)
        Write-Host "提示：Chromium 对**默认配置目录**会忽略 --remote-debugging-port（安全限制）。" -ForegroundColor Yellow
        Write-Host "想带着登录态用调试端口，请改用 -UseMyLogin；想干净地登录一次用 -TempProfile。" -ForegroundColor Yellow
    }
    $p = Start-Process -FilePath $exe -PassThru -ArgumentList $args
    Write-Host "已启动 $Browser（PID=$($p.Id)，端口 $Port）" -ForegroundColor Green
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 700
        if ((& curl.exe -s -o NUL -w '%{http_code}' "http://127.0.0.1:$Port/json/version" 2>$null) -eq '200') { break }
    }
    $alive = & curl.exe -s -o NUL -w '%{http_code}' "http://127.0.0.1:$Port/json/version" 2>$null
    if ($alive -ne '200') { Write-Host "端口没起来，请手动确认浏览器是否正常打开。" -ForegroundColor Red; exit 1 }
}

# 2) 端口通了，跑一次 wiki-cdp.js 的连接/登录自检
$node = 'C:\Users\xueye\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe'
if (-not (Test-Path -LiteralPath $node)) { $node = 'node' }
$env:CDP_PORT = "$Port"
Write-Host ""
& $node (Join-Path $PSScriptRoot 'wiki-cdp.js') check
