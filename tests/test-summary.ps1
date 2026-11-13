# 다시봄(Re:See) 시험 2: 핵심 정리 미리 보기(Jina·Gemini)와 저장, 실패 처리
# 사용법 (resee 폴더에서): powershell -ExecutionPolicy Bypass -File .\tests\test-summary.ps1
# 실제로 Jina Reader 와 Gemini 를 호출하므로 1~2분 걸린다. 기존 데이터는 지우지 않는다.

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Base = "http://localhost:8080"
$User = "tester"; $Pass = "test1234"   # 시험용 계정 (없으면 만들고 로그인)
# 회원가입(이미 있으면 무시) 후 로그인해서 "Authorization: Bearer 토큰" 헤더를 돌려준다
function Login([string]$name, [string]$pw) {
    $body = [Text.Encoding]::UTF8.GetBytes((@{ username = $name; password = $pw } | ConvertTo-Json))
    try { Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$Base/users/signup" -ContentType "application/json; charset=utf-8" -Body $body | Out-Null } catch {}
    $r = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$Base/users/login" -ContentType "application/json; charset=utf-8" -Body $body
    return @{ Authorization = "Bearer " + (ConvertFrom-Json $r.Content).token }
}
$Auth = Login $User $Pass
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

function Post([string]$path, [hashtable]$body) {
    $json = $body | ConvertTo-Json -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$Base$path" -Headers $Auth -ContentType "application/json; charset=utf-8" -Body $bytes -TimeoutSec 90
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
Write-Host "=== AC1-1. 핵심 정리 미리 보기 후 저장 (코드잇 공고, 약 12초) ===" -ForegroundColor Cyan
$before = LinkCount
$p = Post "/links/preview" @{ url = "https://sprint.codeit.kr/track/backend-spring" }
$ok = ($p.Code -eq 200) -and (-not [string]::IsNullOrWhiteSpace($p.Body.summary)) -and ($null -eq $p.Body.notice) -and ((LinkCount) -eq $before)
Check "AC1-1" "미리 보기: 3줄 핵심 정리를 보여 주고, 아직 저장하지 않음" $ok ("응답 {0}, {1:N1}초" -f $p.Code, $p.Sec)
if ($p.Body.summary) {
    Write-Host "       핵심 정리:" -ForegroundColor DarkGray
    Write-Host $p.Body.summary -ForegroundColor Yellow
}
$r = Post "/links" @{ url = $p.Body.url; summary = $p.Body.summary; dueDate = $due; memo = "백엔드 과정 부트캠프" }
$ok = ($r.Code -eq 201) -and (-not [string]::IsNullOrWhiteSpace($r.Body.summary)) -and ($r.Body.summary -eq $p.Body.summary) -and ($r.Body.status -eq "UNCHECKED") -and ((LinkCount) -eq $before + 1) -and ($r.Sec -lt 1.5)
Check "AC1-1" "저장: 핵심 정리와 함께 저장, 미확인, 1건 추가, 외부 API 없이 바로 응답" $ok ("응답 {0}, {1:N2}초" -f $r.Code, $r.Sec)

Write-Host ""
Write-Host "=== AC1-5. 핵심 정리를 할 수 없는 링크 (약 12초) ===" -ForegroundColor Cyan
$before = LinkCount
$p = Post "/links/preview" @{ url = "$p = Post "/links/preview" @{ url = "https://no-such-site.invalid/notice" }" }
$ok = ($p.Code -eq 200) -and ([string]::IsNullOrWhiteSpace($p.Body.summary)) -and ($p.Body.notice -like "*메모*")
Check "AC1-5" "미리 보기: 정리 실패 안내, 메모를 적도록 안내" $ok ("응답 {0}, {1:N1}초, 안내: {2}" -f $p.Code, $p.Sec, $p.Body.notice)
if (-not $ok -and $p.Body.summary) {
    Write-Host "       (정리가 만들어짐 - 사이트가 바뀌었을 수 있음):" -ForegroundColor DarkGray
    Write-Host $p.Body.summary -ForegroundColor Yellow
}
$r = Post "/links" @{ url = $p.Body.url; summary = $null; dueDate = $due; memo = "멋사 백엔드 부트캠프, 마감일 직접 확인" }
$ok = ($r.Code -eq 201) -and ([string]::IsNullOrWhiteSpace($r.Body.summary)) -and ($r.Body.memo -eq "멋사 백엔드 부트캠프, 마감일 직접 확인") -and ((LinkCount) -eq $before + 1)
Check "AC1-5" "저장: 핵심 정리 없이 링크와 메모 저장, 1건 추가" $ok ("응답 {0}" -f $r.Code)

Write-Host ""
Write-Host "=== AC1-3. 입력 오류면 외부 API를 부르지 않음 (바로 응답) ===" -ForegroundColor Cyan
$before = LinkCount
$p = Post "/links/preview" @{ url = "부트캠프 공고" }
Check "AC1-3" "미리 보기: 웹 주소가 아니면 400 거절, 1초 안에 응답" (($p.Code -eq 400) -and ($p.Sec -lt 1.5)) ("응답 {0}, {1:N2}초" -f $p.Code, $p.Sec)
$yesterday = (Get-Date).Date.AddDays(-1).ToString("yyyy-MM-dd")
$r = Post "/links" @{ url = "https://example.com"; dueDate = $yesterday }
Check "AC1-3" "저장: 지난 날짜면 400 거절, 건수 그대로" (($r.Code -eq 400) -and ((LinkCount) -eq $before)) ("응답 {0}" -f $r.Code)

Write-Host ""
Write-Host "=== 결과 요약 ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize
Write-Host "정리 실패 원인은 docker compose logs app 에서 '본문 추출 실패' / '정리할 내용 없음' / '핵심 정리 실패' 로 확인할 수 있다."
