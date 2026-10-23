# 检查 src/main/java 的注释是否引用阶段一文档编号。
# 用法：pwsh -File scripts/check-comment-refs.ps1
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$javaRoot = Join-Path $root 'src\main\java'

if (-not (Test-Path -LiteralPath $javaRoot)) {
    Write-Host "source directory not found: $javaRoot"
    exit 1
}

$pattern = '§|DEC-|FR-|BR-|INV-|I11-|P10-|Q-'
$hits = Get-ChildItem -LiteralPath $javaRoot -Recurse -Filter *.java |
    Select-String -Pattern $pattern

if ($hits) {
    Write-Host 'Forbidden document references found in src/main/java:' -ForegroundColor Red
    foreach ($hit in $hits) {
        Write-Host ("{0}:{1}: {2}" -f $hit.Path, $hit.LineNumber, $hit.Line.Trim())
    }
    exit 1
}

Write-Host 'OK: no document references in src/main/java'
