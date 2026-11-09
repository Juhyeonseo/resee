# 다시봄(Re:See) 0단계: 외부 API 시험 스크립트
# 사용법: powershell -ExecutionPolicy Bypass -File .\test-apis.ps1 -Url "링크" -Key "Gemini API 키"
param(
    [Parameter(Mandatory = $true)][string]$Url,
    [Parameter(Mandatory = $true)][string]$Key,
    [string]$Model = "gemini-2.5-flash"
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 1) Jina Reader 본문 추출 ===" -ForegroundColor Cyan
Write-Host "링크: $Url"
$sw = [Diagnostics.Stopwatch]::StartNew()
try {
    $resp = Invoke-WebRequest -Uri ("https://r.jina.ai/" + $Url) -UseBasicParsing -TimeoutSec 30
    $bytes = $resp.RawContentStream.ToArray()
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
} catch {
    $sw.Stop()
    Write-Host ("본문 추출 실패 ({0:N1}초): {1}" -f $sw.Elapsed.TotalSeconds, $_.Exception.Message) -ForegroundColor Red
    exit 1
}
$sw.Stop()
$jinaSec = $sw.Elapsed.TotalSeconds
Write-Host ("응답 시간: {0:N1}초 / 본문 길이: {1}자" -f $jinaSec, $text.Length) -ForegroundColor Green
$preview = $text.Substring(0, [Math]::Min(300, $text.Length))
Write-Host "본문 앞부분:"
Write-Host $preview -ForegroundColor DarkGray

if ($text.Length -gt 8000) { $text = $text.Substring(0, 8000) }

Write-Host ""
Write-Host "=== 2) Gemini 핵심 정리 ===" -ForegroundColor Cyan
$prompt = "다음 웹페이지 본문의 핵심을 한국어로 3줄 이내로 정리해 줘. 마감일, 기간, 비용 같은 날짜나 숫자 정보가 있으면 꼭 포함해 줘.`n`n" + $text
$body = @{ contents = @(@{ parts = @(@{ text = $prompt }) }) } | ConvertTo-Json -Depth 6
$uri = "https://generativelanguage.googleapis.com/v1beta/models/" + $Model + ":generateContent"
$sw = [Diagnostics.Stopwatch]::StartNew()
try {
    $r = Invoke-RestMethod -Uri $uri -Method Post -Headers @{ "x-goog-api-key" = $Key } `
        -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 30
} catch {
    $sw.Stop()
    Write-Host ("핵심 정리 실패 ({0:N1}초): {1}" -f $sw.Elapsed.TotalSeconds, $_.Exception.Message) -ForegroundColor Red
    exit 1
}
$sw.Stop()
$geminiSec = $sw.Elapsed.TotalSeconds
Write-Host ("응답 시간: {0:N1}초" -f $geminiSec) -ForegroundColor Green
Write-Host "핵심 정리 결과:"
Write-Host $r.candidates[0].content.parts[0].text -ForegroundColor Yellow

Write-Host ""
Write-Host "=== 합계 ===" -ForegroundColor Cyan
Write-Host ("본문 추출 {0:N1}초 + 핵심 정리 {1:N1}초 = 총 {2:N1}초" -f $jinaSec, $geminiSec, ($jinaSec + $geminiSec))
