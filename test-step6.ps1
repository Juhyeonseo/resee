# 다시봄(Re:See) 작업 6 확인 스크립트: 상태 변경, 메모 수정, 보관함
# 사용법 (resee 폴더에서): powershell -ExecutionPolicy Bypass -File .\test-step6.ps1
# 주의: 시험을 위해 links 테이블의 기존 데이터를 지우고 시험용 링크를 넣는다. (외부 API는 부르지 않음)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Base = "http://localhost:8080"
$results = New-Object System.Collections.ArrayList

$DbUser = "resee"
if (Test-Path ".env") {
    $line = Get-Content ".env" -Encoding UTF8 | Where-Object { $_ -match '^\s*DB_USER\s*=' } | Select-Object -First 1
    if ($line) { $DbUser = ($line -split '=', 2)[1].Trim() }
}

function Sql([string]$query) { docker compose exec -T db psql -U $DbUser -d resee -t -A -c $query }

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

function Req([string]$method, [string]$path, $body) {
    $p = @{ UseBasicParsing = $true; Method = $method; Uri = "$Base$path" }
    if ($null -ne $body) {
        $p.ContentType = "application/json; charset=utf-8"
        $p.Body = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress))
    }
    try {
        $resp = Invoke-WebRequest @p
        return @{ Code = [int]$resp.StatusCode; Body = (ReadJson $resp) }
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        return @{ Code = $code; Body = $null }
    }
}

$today = (Get-Date).Date
function D([int]$offset) { return $today.AddDays($offset).ToString("yyyy-MM-dd") }

# 공통 시작 상태 A~D (id 1~4)
function ResetAD() {
    Sql "TRUNCATE links RESTART IDENTITY;" | Out-Null
    Sql ("INSERT INTO links (url, memo, due_date, status, created_at) VALUES " +
         "('https://example.com/A', 'A', '" + (D -1) + "', 'UNCHECKED', now())," +
         "('https://example.com/B', 'B', '" + (D 0) + "', 'UNCHECKED', now())," +
         "('https://example.com/C', 'C', '" + (D 1) + "', 'UNCHECKED', now())," +
         "('https://example.com/D', 'D', '" + (D 0) + "', 'CHECKED', now());") | Out-Null
}
function TodayMemos() { return ((@((Req GET "/links/today" $null).Body) | ForEach-Object { $_.memo }) -join ",") }
function Row([int]$id) { return (Sql ("SELECT status || ' ' || due_date FROM links WHERE id=" + $id + ";")) }

Write-Host ""
Write-Host "=== R4. 상태 변경 (시작 상태: A~D) ===" -ForegroundColor Cyan

ResetAD
$r = Req PATCH "/links/2/status" @{ action = "CHECK" }
Check "AC4-1" "B '확인 완료' -> B 확인 완료, 다시보기에는 A만" (($r.Code -eq 200) -and ($r.Body.status -eq "CHECKED") -and ((TodayMemos) -eq "A")) ("응답 " + $r.Code + ", 다시보기: " + (TodayMemos))

ResetAD
$r = Req PATCH "/links/1/status" @{ action = "REVIEW"; dueDate = (D 3) }
Check "AC4-2" "A '다시 보기'(3일 뒤) -> 미확인 유지, 날짜 변경, 다시보기에서 빠짐" (($r.Code -eq 200) -and ($r.Body.status -eq "UNCHECKED") -and ($r.Body.dueDate -eq (D 3)) -and ((TodayMemos) -eq "B")) ("응답 " + $r.Code + ", 다시보기: " + (TodayMemos))

ResetAD
$before = Row 1
$r = Req PATCH "/links/1/status" @{ action = "REVIEW"; dueDate = (D -1) }
Check "AC4-3" "A '다시 보기'(어제) -> 400 거절, 상태와 날짜 그대로" (($r.Code -eq 400) -and ((Row 1) -eq $before)) ("응답 " + $r.Code + ", A: " + (Row 1))

$r = Req PATCH "/links/999999/status" @{ action = "CHECK" }
Check "추가" "저장되어 있지 않은 링크 -> 404" ($r.Code -eq 404) ("응답 " + $r.Code)

Write-Host ""
Write-Host "=== R1-2. 메모 수정 ===" -ForegroundColor Cyan
$r = Req PATCH "/links/3/memo" @{ memo = "접수 마감 11/20" }
Check "추가" "메모 직접 수정" (($r.Code -eq 200) -and ($r.Body.memo -eq "접수 마감 11/20")) ("응답 " + $r.Code)
$r = Req PATCH "/links/3/memo" @{ memo = ("가" * 201) }
Check "추가" "메모 201자 -> 400 거절" ($r.Code -eq 400) ("응답 " + $r.Code)

Write-Host ""
Write-Host "=== R5. 보관함 ===" -ForegroundColor Cyan
Sql "TRUNCATE links RESTART IDENTITY;" | Out-Null
Sql ("INSERT INTO links (url, memo, due_date, status, created_at) VALUES " +
     "('https://example.com/1', '11/1', '" + (D 5) + "', 'UNCHECKED', '2026-11-01 10:00')," +
     "('https://example.com/2', '11/2', '" + (D 5) + "', 'CHECKED',   '2026-11-02 10:00')," +
     "('https://example.com/3', '11/3', '" + (D 5) + "', 'UNCHECKED', '2026-11-03 10:00');") | Out-Null
$all = ((@((Req GET "/links" $null).Body) | ForEach-Object { $_.memo }) -join ",")
$filtered = ((@((Req GET "/links?status=UNCHECKED&sort=oldest" $null).Body) | ForEach-Object { $_.memo }) -join ",")
Check "AC5-1" "기본은 최신순 11/3,11/2,11/1, 미확인+오래된 순은 11/1,11/3" (($all -eq "11/3,11/2,11/1") -and ($filtered -eq "11/1,11/3")) ("기본: " + $all + " / 필터: " + $filtered)

Write-Host ""
Write-Host "=== 결과 요약 ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize
