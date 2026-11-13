# 다시봄 (Re:See)

저장한 링크, 나중에 다시 보아요. 링크를 저장하면 AI가 핵심을 3줄로 정리해 두고, 정한 날에 "오늘의 다시보기"로 다시 보여 주는 서비스입니다.

## 웹 주소(URL)로 바로 접속해 쓰기

**http://54.163.203.15:8080** (평가 기간 동안 켜 둡니다)

1. 아이디(2~20자)와 비밀번호(4자 이상)를 입력하고 **회원가입**을 누릅니다. 가입하면 바로 로그인됩니다.
2. 링크를 입력하고 **핵심 정리 보기**를 누르면 AI가 정리한 핵심 내용 3줄을 보여 줍니다. 정리가 실패해도 링크 저장은 됩니다.
3. 다시 볼 날짜(기본값: 3일 뒤, '내일'/'3일 후'/'7일 후' 버튼)를 고르고 메모를 작성한 뒤 **저장**을 누릅니다.
4. 첫 화면의 일주일 띠에서 날짜를 눌러 그날 볼 링크를 확인하고, 카드의 **확인 완료**나 **다시 보기** 버튼으로 상태를 바꿉니다. 오늘 볼 링크 수는 달력 오늘 칸과 브라우저 탭 제목에서 확인할 수 있습니다.
5. 오른쪽 위 **보관함 전체 보기** 버튼을 누르면 전체 링크를 상태별(전체·미확인·확인 완료)로 걸러 보고, 최신순·오래된 순으로 정렬할 수 있습니다.

## 내 컴퓨터에 설치해 실행하기

실행하려면 코드 ZIP 파일, Docker Desktop(docker.com에서 설치), 본인의 Gemini API 키(Google AI Studio에서 무료 발급)가 필요합니다. 키가 없으면 AI 핵심 정리만 동작하지 않습니다.

1. 코드 ZIP 파일의 압축을 풀고, 이 README와 `docker-compose.yml`이 있는 폴더에서 PowerShell을 엽니다.
2. `copy .env.example .env` 명령으로 설정 파일 `.env`를 만들고, 메모장으로 열어 `DB_PASSWORD`(원하는 값)와 `GEMINI_API_KEY`(발급받은 키)를 채운 뒤 저장합니다.
3. Docker Desktop을 켜 둔 상태에서 실행합니다. 처음에는 몇 분 걸릴 수 있습니다.
   ```
   docker compose up -d --build
   ```
4. 브라우저에서 http://localhost:8080 으로 접속합니다. 화면이 안 뜨면 잠시 후 새로고침합니다.
5. 끌 때는 `docker compose down`을 실행합니다. **`-v`는 붙이지 마세요.** 가입한 계정과 저장한 링크가 모두 지워집니다.

`.env`에는 API 키가 들어가므로 다른 사람과 공유하거나 코드에 넣지 마세요.

## 시험 스크립트

앱이 실행 중일 때 PowerShell에서 차례로 실행합니다. 시험 항목별 통과·실패가 출력됩니다.

```
powershell -ExecutionPolicy Bypass -File .\tests\test-save.ps1
powershell -ExecutionPolicy Bypass -File .\tests\test-status.ps1
powershell -ExecutionPolicy Bypass -File .\tests\test-summary.ps1
```

`test-summary.ps1`은 실제 Jina Reader와 Gemini를 호출하므로 `GEMINI_API_KEY`가 필요합니다.

## 구성

| 구분 | 내용 |
| --- | --- |
| 화면 | `src/main/resources/static/index.html` 한 파일 (HTML + CSS + JavaScript) |
| 서버 | Java 17, Spring Boot, `src/main/java/com/resee` (12개 파일) |
| 저장소 | PostgreSQL 16 (Docker 볼륨에 보관) |
| 외부 API | Jina Reader(본문 추출), Gemini(`gemini-3.5-flash-lite`, 3줄 정리) |
| 실행 | Docker Compose (앱 + DB), 배포는 AWS EC2 한 대 |

Java 파일의 역할:

- `LinkController`, `UserController`: 요청을 받아 넘기고 JSON으로 돌려줌
- `LinkService`, `UserService`: 입력 검사와 규칙 (링크, 계정)
- `SummaryClient`: Jina Reader → Gemini 호출, 실패하면 정리 없이 진행
- `Link`, `User`, `LinkRepository`, `UserRepository`: DB 테이블과 읽기·쓰기
- `ApiException`, `ApiExceptionHandler`: 거절 이유를 `{"error": 안내}`로 응답
- `ReseeApplication`: 시작점