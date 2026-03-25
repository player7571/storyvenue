# Status.md

## Current project status
현재 저장소는 MVP 핵심 흐름 코드가 연결된 상태다.

구현 완료 범위:
- FastAPI server
  - `/health`
  - `/auth/sign-up`, `/auth/sign-in`, `/auth/refresh`, `/auth/me`
  - `/sessions` 목록/생성/조회
  - `/messages/{session_id}`
  - `/voice/turn`, `/voice/repeat-last`
  - `/memory/extract`
  - `/chapters` 목록/생성/조회/수정/재생성
  - `/book/compile`, `/book/versions`, `/book/versions/{book_id}`
  - `/feed/publish`, `/feed`, `/feed/people/recommended`
  - `/feed/{post_id}`, `/feed/{post_id}/comments`, `/feed/{post_id}/read`
  - `/chat/rooms`, `/chat/rooms/{room_id}/messages`
- Android app
  - 이메일 로그인/회원가입 화면
  - 로그인 세션 복구
  - 홈에서 세션 생성/선택
  - Voice Interview 실제 업로드/재생 흐름
  - Draft 화면에서 chapter 생성/수정/재생성
  - Book Preview 화면에서 chapter ordering 과 최종 버전 저장/조회
  - Feed 화면에서 자서전 게시, 추천 목록, 비슷한 사람 추천
  - Feed 상세 화면에서 읽기 기록, 댓글 작성, 채팅 시작
  - Chat 화면에서 1:1 대화방 목록/메시지 전송
- 문서
  - API 문서 최신화
  - DB schema 문서 최신화
  - Supabase 적용용 SQL 추가

## Decisions made
- 앱은 서버의 `/auth/*` 엔드포인트를 통해 Supabase Auth 를 사용한다.
- 서버는 Bearer token 기반 인증을 기본으로 하고, local 에서만 `X-User-Id` fallback 을 허용할 수 있다.
- `/voice/turn` 안에서 memory extraction 을 자동 실행한다.
- assistant 텍스트 응답 생성은 mock 대신 OpenAI text generation 으로 전환했다.
- TTS 생성 파일은 로컬 디스크에 저장하되 `OPENAI_TTS_RETENTION_HOURS` 기준으로 오래된 파일을 정리한다.
- 최종 자서전 저장은 `autobiography_versions.chapter_ids` 에 chapter 순서를 함께 저장한다.
- 피드 추천은 `feed_read_events` 읽기 행동과 AI가 분석한 `topics/emotions/experiences` 를 함께 반영한다.
- 사람 추천은 자서전/읽기 기록에서 겹치는 주제와 경험을 기준으로 계산한다.
- 피드 게시는 저장된 `autobiography_versions` 를 원본으로 사용한다.
- 채팅은 1:1 room_key 기반으로 중복 없이 재사용한다.

## Done
- server auth service / route 추가
- auth token 기반 현재 사용자 확인 dependency 추가
- profiles upsert service 추가
- sessions 목록 API 추가
- chapters 목록 / 단건 조회 API 추가
- book compile / list / get API 추가
- OpenAI text generation service 추가
- `/voice/turn` memory 자동 추출 연결
- TTS 만료 파일 정리 정책 추가
- Android app placeholder 로그인 제거
- Android app 세션 생성 / 선택 흐름 연결
- Android app Voice Interview 를 실제 auth/session 기반으로 연결
- Android app Draft 화면 chapter API 연결
- Android app Book Preview 화면 book API 연결
- `server/sql/mvp_schema.sql` 추가
- `server/sql/social_feed_schema.sql` 추가
- server feed analysis / feed service / chat service 추가
- server social feed / comment / read-event / chat route 추가
- Android app Feed / Feed detail / Chat 화면 추가
- Android app 피드 게시 / 댓글 / 읽기 기록 / 채팅 API 연결
- `docs/API.md`, `docs/DB_SCHEMA.md`, `docs/Status.md` 최신화

## Remaining issues
- 실기기에서 MediaRecorder, 한국어 STT/TTS 품질, 네트워크 지연을 아직 검증하지 못했다.
- 안전 판단 품질은 실제 사용자 발화 로그 기준 추가 튜닝 여지가 있다.
- 새 소셜 기능을 쓰려면 [social_feed_schema.sql](/Users/player7571/storyvenue/server/sql/social_feed_schema.sql) 을 추가 적용해야 한다.
- 피드 추천 품질은 실제 읽기 로그가 쌓여야 더 안정화된다.
- 이메일 확인 정책이 켜져 있으면 테스트 계정 확인 메일 처리 여부에 따라 로그인 UX 가 달라질 수 있다.

## Next
1. `server/sql/social_feed_schema.sql` 적용
2. 앱에서 자서전 게시 -> 댓글 -> 채팅 흐름 end-to-end 확인
3. 실제 사용자 2명 이상으로 추천/채팅 흐름 검증
4. 실기기에서 마이크 권한, 녹음, TTS 청취 테스트
5. production 서버 주소 확정 후 앱 기본 서버 주소 반영

## Risks
- Supabase 이메일 확인 정책에 따라 회원가입 UX 가 달라질 수 있음
- 한국어 TTS 음색과 속도는 OpenAI voice 선택에 따라 추가 조정이 필요할 수 있음
- 실사용 데이터에서 안전 표현, 방언, 불완전 발화 처리 튜닝이 필요할 수 있음
- 읽기 로그가 적은 초기 상태에서는 추천이 인기/최근성에 더 치우칠 수 있음
- Feed 분석용 OpenAI 호출 비용과 지연이 게시 시점에 추가됨

## Validation
app:
1. `cd app`
2. `ANDROID_HOME=/Users/player7571/Library/Android/sdk ANDROID_SDK_ROOT=/Users/player7571/Library/Android/sdk ./gradlew :app:compileDebugKotlin`
3. `ANDROID_HOME=/Users/player7571/Library/Android/sdk ANDROID_SDK_ROOT=/Users/player7571/Library/Android/sdk ./gradlew :app:assembleDebug`

server:
1. `cd server`
2. `python3 -m venv .venv`
3. `source .venv/bin/activate`
4. `pip install -r requirements.txt`
5. `.env.example` 기준으로 `.env` 작성
6. `python -m compileall app`
7. `python -c "from app.main import app; print(app.title)"`
8. `uvicorn app.main:app --reload`
9. `curl http://127.0.0.1:8000/health`
10. `fastapi.testclient` 로 `/health` 200, `/feed` 401, `/chat/rooms` 401 확인

실기기 테스트 필요 여부:
- 필요
- 마이크 권한 허용/거부
- 3턴 이상 음성 인터뷰
- chapter 생성/수정/재생성
- book 저장 및 다시 조회
- 자서전 피드 게시
- 피드 댓글 작성
- 서로 다른 두 계정 간 채팅
- 몇 개 글을 읽은 뒤 추천 목록 변화 확인
