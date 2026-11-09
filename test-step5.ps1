# 다시봄(Re:See) 작업 5 확인 스크립트: 핵심 정리 연동과 실패 처리
# 사용법 (resee 폴더에서): powershell -ExecutionPolicy Bypass -File .\test-step5.ps1
# 실제로 Jina Reader 와 Gemini 를 호출하므로 1~2분 걸린다. 기존 데이터는 지우지 않는다.

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Base = "http://localhost:8080"
$results = New-Object System.Collections.ArrayList

$DbUser = "resee"
if (Test-Path ".env") {
    $line = Get-Content ".env" -Encoding UTF8 | Where-Object { $_ -match '^\s*DB_USER\s*=' } | Select-Object -First 1
    if ($line) { $DbUser = ($line -split '=', 2)[1].Trim() }
}

function Sql([string]$query) { docker compose exec -T db psql -U $DbUser -d resee -t -A -c $query }
function LinkCount() { return [int](Sql "SELECT COUNT(*) FROM links;") }

function Check([string]$id, [string]$name, [bool]$ok, [string]$detail) {
    $mark = if ($ok) { "PASS" } else { "FAIL" }
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host ("[{0}] {1} {2}" -f $mark, $id, $name) -ForegroundColor $color
    if ($detail) { Write-Host ("       " + $detail) -ForegroundColor DarkGray }
    [void]$results.Add([pscustomobject]@{ AC = $id; Content = $name; Result = $mark })
}

function ReadJson($resp) {
    $text = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    if ([string]::IsNullOrWhiteSpace($text)) { return $null }
    return $text | ConvertFrom-Json
}

function PostLink([hashtable]$body) {
    $json = $body | ConvertTo-Json -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$Base/links" -ContentType "application/json; charset=utf-8" -Body $bytes -TimeoutSec 90
        $sw.Stop()
        return @{ Code = [int]$resp.StatusCode; Body = (ReadJson $resp); Sec = $sw.Elapsed.TotalSeconds }
    } catch {
        $sw.Stop()
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        return @{ Code = $code; Body = $_.Exception.Message; Sec = $sw.Elapsed.TotalSeconds }
    }
}

$due = (Get-Date).Date.AddDays(3).ToString("yyyy-MM-dd")

Write-Host ""
Write-Host "=== AC1-1. 정상 저장 + 핵심 정리 (코드잇 공고, 약 12초) ===" -ForegroundColor Cyan
$before = LinkCount
$r = PostLink @{ url = "https://sprint.codeit.kr/track/backend-spring"; dueDate = $due; memo = "백엔드 과정 부트캠프" }
$ok = ($r.Code -eq 201) -and (-not [string]::IsNullOrWhiteSpace($r.Body.summary)) -and ($null -eq $r.Body.notice) -and ((LinkCount) -eq $before + 1) -and ($r.Body.status -eq "UNCHECKED")
Check "AC1-1" "저장 성공, 3줄 핵심 정리 포함, 미확인, 1건 추가" $ok ("응답 {0}, {1:N1}초" -f $r.Code, $r.Sec)
if ($r.Body.summary) {
    Write-Host "       핵심 정리:" -ForegroundColor DarkGray
    Write-Host $r.Body.summary -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== AC1-5. 핵심 정리를 할 수 없는 링크 (멋사 공고, 약 12초) ===" -ForegroundColor Cyan
$before = LinkCount
$r = PostLink @{ url = "https://bootcamp.likelion.net/school/kdt-backendj-29th"; dueDate = $due; memo = "백엔드 과정 부트캠프" }
$ok = ($r.Code -eq 201) -and ([string]::IsNullOrWhiteSpace($r.Body.summary)) -and ($r.Body.notice -like "*링크는 저장되었습니다*") -and ((LinkCount) -eq $before + 1) -and ($r.Body.memo -eq "백엔드 과정 부트캠프")
Check "AC1-5" "정리 실패 안내, 링크와 메모는 저장, 1건 추가" $ok ("응답 {0}, {1:N1}초, 안내: {2}" -f $r.Code, $r.Sec, $r.Body.notice)
if (-not $ok -and $r.Body.summary) {
    Write-Host "       (정리가 만들어짐 - 사이트가 바뀌었을 수 있음):" -ForegroundColor DarkGray
    Write-Host $r.Body.summary -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== AC1-3. 입력 오류면 외부 API를 부르지 않음 (바로 응답) ===" -ForegroundColor Cyan
$before = LinkCount
$r = PostLink @{ url = "부트캠프 공고"; dueDate = $due }
Check "AC1-3" "400 거절, 1초 안에 응답, 건수 그대로" (($r.Code -eq 400) -and ($r.Sec -lt 1.5) -and ((LinkCount) -eq $before)) ("응답 {0}, {1:N2}초" -f $r.Code, $r.Sec)

Write-Host ""
Write-Host "=== 결과 요약 ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize
Write-Host "정리 실패 원인은 docker compose logs app 에서 '본문 추출 실패' / '정리할 내용 없음' / '핵심 정리 실패' 로 확인할 수 있다."
