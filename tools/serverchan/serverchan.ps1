# ============================================================================
# serverchan.ps1 —— Server酱 / Server酱³（方糖）微信推送工具
#
# 用途：
#   在 Trae 里对 AI 说「干完 X 后通知我」时，AI 调用本脚本把结果/构建状态
#   一键推送到你的微信。属于"单向通知"：服务器 -> 微信。
#
# 前置：
#   1) 在 https://sct.ftqq.com 登录获取 SendKey（形如 SCT215961TxXXXX）。
#   2) 把 SendKey 写入本目录 config.json（参照 config.example.json），
#      或设置环境变量 SERVERCHAN_KEY，或每次用 -Key 传入。
#
# 用法：
#   .\serverchan.ps1 -Title "构建完成" -Message "joes_addons_for_abmc-3.17.1.jar 已生成"
#   .\serverchan.ps1 -Title "任务失败" -Message "compileJava 报错，详见日志" -Server "https://sct.ftqq.com"
#
# 参数：
#   -Title    标题（必填，微信卡片标题）
#   -Message  正文（可选，支持 \n 换行 / Markdown 简式）
#   -Server   API 地址（默认 https://sctapi.ftqq.com，Server酱³ 使用；
#             老版方糖 Server酱 用 https://sc.ftqq.com 或 https://sct.ftqq.com）
#   -Key      直接传 SendKey（优先级：-Key > 环境变量 > config.json）
# ============================================================================

param(
    [Parameter(Mandatory = $true)]
    [string]$Title,
    [string]$Message = "",
    [string]$Server = "https://sctapi.ftqq.com",
    [string]$Key = ""
)

$ErrorActionPreference = "Stop"

function Get-SendKey {
    param([string]$InlineKey)

    # 1) 内联参数
    if ($InlineKey) { return $InlineKey.Trim() }

    # 2) 环境变量
    $envKey = [Environment]::GetEnvironmentVariable("SERVERCHAN_KEY")
    if ($envKey) { return $envKey.Trim() }

    # 3) 本地 config.json（不提交到 git）
    $cfg = Join-Path $PSScriptRoot "config.json"
    if (Test-Path $cfg) {
        try {
            $json = Get-Content $cfg -Raw -Encoding UTF8 | ConvertFrom-Json
            if ($json.send_key) { return $json.send_key.Trim() }
        } catch {
            Write-Warning "config.json 解析失败：$($_.Exception.Message)"
        }
    }

    return ""
}

$sendKey = Get-SendKey -InlineKey $Key
if (-not $sendKey) {
    Write-Error ("未找到 Server酱 SendKey！`n" +
        "请任选其一：`n" +
        "  1) 在 tools/serverchan/ 下把 config.example.json 复制为 config.json，并填入你的 SendKey；`n" +
        "  2) 或设置环境变量 SERVERCHAN_KEY；`n" +
        "  3) 或用 -Key 'SCTxxxxxxxxxx' 传入。`n" +
        "（获取地址：https://sct.ftqq.com）")
    exit 1
}

# 校验 SendKey 基本格式（SCT 开头）
if ($sendKey -notmatch "^SCT") {
    Write-Warning "SendKey 看起来不像 Server酱³ 的格式（应以 SCT 开头）。若你用的是老版方糖 SCKEY，请用 -Server https://sc.ftqq.com"
}

$endpoint = $Server.TrimEnd("/") + "/" + $sendKey + ".send"

# 构造表单（Server酱³ 与老版均接受 title / desp 两个字段）
$body = @{ title = $Title; desp = $Message }
Write-Host "正在推送到微信：$Title"

try {
    $resp = Invoke-RestMethod -Uri $endpoint -Method Post -Body $body -TimeoutSec 20
    $errno = $resp.errno
    $errmsg = $resp.errmsg
    if ($null -eq $errno -or $errno -eq 0) {
        Write-Host "推送成功：" + $errmsg
        exit 0
    } else {
        Write-Error "推送失败（errno=$errno）：$errmsg"
        exit 2
    }
} catch {
    Write-Error "推送请求异常：$($_.Exception.Message)"
    exit 3
}
