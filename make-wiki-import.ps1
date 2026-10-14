<#
    生成 MediaWiki 导入用 XML（给 Special:Import 用）
    ============================================================
    用途：把本仓库 wiki\ 目录下的全部 .wiki 文件打包成一个 MediaWiki 能直接导入的 XML。
          以后改了页面，重跑一次、再到 Special:Import 上传一次即可（会在原页面上追加新修订）。

    用法（在本文件所在目录打开 PowerShell）：
        .\make-wiki-import.ps1
        .\make-wiki-import.ps1 -Wiki https://你的wiki.miraheze.org
        .\make-wiki-import.ps1 -Wiki https://xxx.miraheze.org -Out D:\wiki-import.xml

    说明：
      * 文件名 w 形如 "分类-生物.wiki" 会映射成标题 "Category:生物"（ns=14），其余映射成 ns=0 的普通条目；
      * -Wiki 只会写进 <base>/<sitename>，对导入内容没有影响（填不填都能导入成功）；
      * 文本里的 & < > 会被正确转义，所以 <code>/<div>/<pre> 等标签能原样导入。
#>
[CmdletBinding()]
param(
    [string]$Wiki      = "https://YOURWIKI.miraheze.org",
    [string]$SourceDir = (Join-Path $PSScriptRoot "wiki"),
    [string]$Out       = (Join-Path $PSScriptRoot "wiki-import.xml"),
    [string]$SiteName  = "JOE's Addons for ABMC Wiki",
    [string]$Comment   = "从本地 wikitext 批量导入（JOE's Addons for ABMC wiki）"
)

if (-not (Test-Path -LiteralPath $SourceDir)) { throw "找不到页面目录：$SourceDir" }

function ConvertTo-XmlText {
    param([string]$Text)
    # 顺序很重要：必须先转义 &，否则会把后面生成的实体再转义一次
    $Text.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;')
}

$timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$files     = Get-ChildItem -LiteralPath $SourceDir -Filter *.wiki | Sort-Object Name

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('<?xml version="1.0" encoding="UTF-8"?>')
[void]$sb.AppendLine('<mediawiki xmlns="http://www.mediawiki.org/xml/export-0.11/" ' +
    'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" ' +
    'xsi:schemaLocation="http://www.mediawiki.org/xml/export-0.11/ ' +
    'http://www.mediawiki.org/xml/export-0.11.xsd" version="0.11" xml:lang="zh">')
[void]$sb.AppendLine('  <siteinfo>')
[void]$sb.AppendLine('    <sitename>' + (ConvertTo-XmlText $SiteName) + '</sitename>')
[void]$sb.AppendLine('    <dbname>wikidb</dbname>')
[void]$sb.AppendLine('    <base>' + $Wiki.TrimEnd('/') + '/wiki/Main_Page</base>')
[void]$sb.AppendLine('    <generator>MediaWiki 1.43</generator>')
[void]$sb.AppendLine('    <case>first-letter</case>')
[void]$sb.AppendLine('    <namespaces>')
[void]$sb.AppendLine('      <namespace key="0" case="first-letter" />')
[void]$sb.AppendLine('      <namespace key="14" case="first-letter">Category</namespace>')
[void]$sb.AppendLine('    </namespaces>')
[void]$sb.AppendLine('  </siteinfo>')

$titles = @()
foreach ($f in $files) {
    $base = [System.IO.Path]::GetFileNameWithoutExtension($f.Name)
    if ($base.StartsWith('分类-')) {
        $title = 'Category:' + $base.Substring('分类-'.Length)
        $ns    = 14
    } else {
        $title = $base
        $ns    = 0
    }
    $text = Get-Content -LiteralPath $f.FullName -Raw -Encoding UTF8
    $bytes = [System.Text.Encoding]::UTF8.GetByteCount($text)

    [void]$sb.AppendLine('  <page>')
    [void]$sb.AppendLine('    <title>' + (ConvertTo-XmlText $title) + '</title>')
    [void]$sb.AppendLine('    <ns>' + $ns + '</ns>')
    [void]$sb.AppendLine('    <revision>')
    [void]$sb.AppendLine('      <timestamp>' + $timestamp + '</timestamp>')
    [void]$sb.AppendLine('      <contributor><username>WikiImporter</username></contributor>')
    [void]$sb.AppendLine('      <comment>' + (ConvertTo-XmlText $Comment) + '</comment>')
    [void]$sb.AppendLine('      <model>wikitext</model>')
    [void]$sb.AppendLine('      <format>text/x-wiki</format>')
    [void]$sb.AppendLine('      <text bytes="' + $bytes + '" xml:space="preserve">' + (ConvertTo-XmlText $text) + '</text>')
    [void]$sb.AppendLine('    </revision>')
    [void]$sb.AppendLine('  </page>')
    $titles += $title
}
[void]$sb.AppendLine('</mediawiki>')

# 不带 BOM 的 UTF-8（MediaWiki 对 BOM 敏感）
[System.IO.File]::WriteAllText($Out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))

# 自检：XML 能否解析、页数对不对
try {
    [xml]$check = Get-Content -LiteralPath $Out -Raw -Encoding UTF8
    $pageCount = $check.mediawiki.page.Count
    Write-Host ("已生成 {0}" -f $Out) -ForegroundColor Green
    Write-Host ("  页面数 : {0}（校验通过 ✅）" -f $pageCount) -ForegroundColor Green
    Write-Host ("  大小   : {0:N1} KB" -f ((Get-Item -LiteralPath $Out).Length / 1KB))
    Write-Host ("  分类页 : {0}" -f (($titles | Where-Object { $_ -like 'Category:*' }) -join '、'))
    Write-Host ''
    Write-Host '下一步：浏览器打开 <你的wiki>/wiki/Special:Import → 选「上传 XML 文件」→ 选择上面这个文件 → 导入。'
} catch {
    Write-Host ("XML 自检失败：{0}" -f $_.Exception.Message) -ForegroundColor Red
    exit 1
}
