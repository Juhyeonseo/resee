# 다시봄(Re:See) 시험 1: 링크 저장 입력 검사, 오늘의 다시보기, 재시작 후 기록 유지
# 사용법 (resee 폴더에서):
#   powershell -ExecutionPolicy Bypass -File .\tests\test-save.ps1            -> 2~3단계 AC 확인
#   powershell -ExecutionPolicy Bypass -File .\tests\test-save.ps1 -Restart   -> 위 확인 + 4단계(재실행 후 보존) 확인
# 주의: 시험을 위해 links 테이블의 기존 데이터를 모두 지우고 시작한다.
param([switch]$Restart)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Base = "http://localhost:8080"
$results = New-Object System.Collections.ArrayList

# .env 에서 DB 사용자 이름 읽기 (없으면 resee)
$DbUser = "resee"
if (Test-Path ".env") {
    $line = Get-Content ".env" -Encoding UTF8 | Where-Object { $_ -match '^\s*DB_USER\s*=' } | Select-Object -First 1
    if ($line) { $DbUser = ($line -split '=', 2)[1].Trim() }
}

function Sql([string]$query) {
    docker compose exec -T db psql -U $DbUser -d resee -t -A -c $query
}

function Check([string]$id, [string]$name, [bool]$ok, [string]$detail) {
    $mark = if ($ok) { "PASS" } else { "FAIL" }
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host ("[{0}] {1} {2}" -f $mark, $id, $name) -ForegroundColor $color
    if ($detail) { Write-Host ("       " + $detail) -ForegroundColor DarkGray }
    [void]$results.Add([pscustomobject]@{ AC = $id; 내용 = $name; 결과 = $mark })
}

# PowerShell 5.1 은 charset 없는 JSON 응답의 한글을 잘못 읽으므로 바이트를 UTF-8로 직접 읽는다
function ReadJson($resp) {
    $text = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    if ([string]::IsNullOrWhiteSpace($text)) { return $null }
    return $text | ConvertFrom-Json
}

function PostLink([hashtable]$body) {
    $json = $body | ConvertTo-Json -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$Base/links" -ContentType "application/json; charset=utf-8" -Body $bytes
        return @{ Code = [int]$resp.StatusCode; Body = (ReadJson $resp) }
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        $msg = if ($_.ErrorDetails) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        return @{ Code = $code; Body = $msg }
    }
}

function GetToday() { return @(ReadJson (Invoke-WebRequest -UseBasicParsing -Uri "$Base/links/today")) }
function LinkCount() { return [int](Sql "SELECT COUNT(*) FROM links;") }

$today = (Get-Date).Date
function D([int]$offset) { return $today.AddDays($offset).ToString("yyyy-MM-dd") }

Write-Host ""
Write-Host "=== 준비: links 테이블 비우기 ===" -ForegroundColor Cyan
Sql "TRUNCATE links RESTART IDENTITY;" | Out-Null

# ---------- 3단계: 오늘의 다시보기 ----------
Write-Host ""
Write-Host "=== R3. 오늘의 다시보기 ===" -ForegroundColor Cyan
$list = GetToday
Check "추가" "날짜가 된 미확인 링크가 없으면 빈 목록" ($list.Count -eq 0) ("결과 " + $list.Count + "건")

Sql ("INSERT INTO links (url, memo, due_date, status, created_at) VALUES " +
     "('https://example.com/A', 'A', '" + (D -1) + "', 'UNCHECKED', now())," +
     "('https://example.com/B', 'B', '" + (D 0) + "', 'UNCHECKED', now())," +
     "('https://example.com/C', 'C', '" + (D 1) + "', 'UNCHECKED', now())," +
     "('https://example.com/D', 'D', '" + (D 0) + "', 'CHECKED', now());") | Out-Null
$list = GetToday
$memos = ($list | ForEach-Object { $_.memo }) -join ","
Check "AC3-1" "공통 시작 상태(A~D)에서 A, B 순서로만 표시" ($memos -eq "A,B") ("결과: " + $memos)
$checked = [int](Sql "SELECT COUNT(*) FROM links WHERE status='CHECKED';")
Check "AC3-1" "조회 후에도 상태는 그대로" ($checked -eq 1) ("확인 완료 " + $checked + "건")

# ---------- 2단계: 링크 저장 ----------
Write-Host ""
Write-Host "=== R1, R2. 링크 저장 (핵심 정리 제외) ===" -ForegroundColor Cyan
Sql "TRUNCATE links RESTART IDENTITY;" | Out-Null
Sql ("INSERT INTO links (url, due_date, status, created_at) VALUES " +
     "('https://example.com/old1', '" + (D 5) + "', 'UNCHECKED', now())," +
     "('https://example.com/old2', '" + (D 5) + "', 'UNCHECKED', now());") | Out-Null

$r = PostLink @{ url = "https://sprint.codeit.kr/track/backend-spring"; dueDate = (D 3); memo = "백엔드 과정 부트캠프" }
$ok = ($r.Code -eq 201) -and ($r.Body.status -eq "UNCHECKED") -and ($r.Body.dueDate -eq (D 3)) -and ($r.Body.memo -eq "백엔드 과정 부트캠프") -and ((LinkCount) -eq 3)
Check "추가" "정상 저장: 3건, 미확인, 날짜·메모 일치" $ok ("응답 " + $r.Code)

$r = PostLink @{ url = "naver.com"; dueDate = (D 3) }
Check "추가" "naver.com 입력 시 https:// 를 붙여 저장" (($r.Code -eq 201) -and ($r.Body.url -eq "https://naver.com")) ("저장된 URL: " + $r.Body.url)

$before = LinkCount
$r = PostLink @{ url = "부트캠프 공고"; dueDate = (D 3) }
Check "AC1-3" "웹 주소 형식이 아니면 거절, 건수 그대로" (($r.Code -eq 400) -and ((LinkCount) -eq $before)) ("응답 " + $r.Code + " " + $r.Body)

$r = PostLink @{ url = ""; dueDate = (D 3) }
Check "AC1-3" "빈 URL 거절, 건수 그대로" (($r.Code -eq 400) -and ((LinkCount) -eq $before)) ("응답 " + $r.Code + " " + $r.Body)

$r = PostLink @{ url = "https://example.com/memo"; dueDate = (D 3); memo = ("가" * 201) }
Check "추가" "메모 201자 거절, 건수 그대로" (($r.Code -eq 400) -and ((LinkCount) -eq $before)) ("응답 " + $r.Code + " " + $r.Body)

$r = PostLink @{ url = "https://example.com/past"; dueDate = (D -1) }
Check "AC2-3" "어제 날짜 거절, 건수 그대로" (($r.Code -eq 400) -and ((LinkCount) -eq $before)) ("응답 " + $r.Code + " " + $r.Body)

$r = PostLink @{ url = "https://example.com/today"; dueDate = (D 0) }
$inToday = @(GetToday | Where-Object { $_.url -eq "https://example.com/today" }).Count -eq 1
Check "추가" "오늘 날짜로 저장하면 바로 오늘의 다시보기에 표시" (($r.Code -eq 201) -and $inToday) ("응답 " + $r.Code)

# ---------- 4단계: 재실행 후 보존 ----------
if ($Restart) {
    Write-Host ""
    Write-Host "=== R6. 재실행 후 보존 ===" -ForegroundColor Cyan
    $beforeRows = Sql "SELECT id, url, coalesce(memo,''), due_date, status FROM links ORDER BY id;"
    Write-Host "컨테이너 종료 중 (docker compose down, 볼륨은 남김)..."
    docker compose down | Out-Null
    Write-Host "컨테이너 다시 실행 중 (docker compose up -d)..."
    docker compose up -d | Out-Null
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        try { Invoke-RestMethod -Uri "$Base/api/health" | Out-Null; $ready = $true; break } catch { }
    }
    if (-not $ready) {
        Check "AC6-1" "재실행 후 앱 응답" $false "2분 안에 앱이 응답하지 않음. docker compose logs app 확인"
    } else {
        $afterRows = Sql "SELECT id, url, coalesce(memo,''), due_date, status FROM links ORDER BY id;"
        $same = (($beforeRows -join "`n") -eq ($afterRows -join "`n")) -and ($beforeRows.Count -gt 0)
        Check "AC6-1" "재실행 후 모든 링크의 URL·메모·날짜·상태가 종료 전과 같음" $same ("종료 전 " + $beforeRows.Count + "건 / 재실행 후 " + $afterRows.Count + "건")
    }
}

Write-Host ""
Write-Host "=== 결과 요약 ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize
$fail = @($results | Where-Object { $_.결과 -eq "FAIL" }).Count
if ($fail -eq 0) { Write-Host "모두 통과" -ForegroundColor Green } else { Write-Host ("실패 " + $fail + "건") -ForegroundColor Red }
