# Status.md

## Current project status
Milestone 1 범위의 server/app 뼈대 구현 완료.
현재는 FastAPI `/health`, `/sessions`, `/messages/{session_id}`, `/voice/turn`, `/memory/extract`, Android placeholder 화면 5개, Supabase 설정/클라이언트 초기화 구조, 이메일 로그인 뼈대, Voice Interview 상태/UI 이벤트/권한/녹음/업로드 뼈대까지 준비된 최소 실행 단계다.

## Decisions made
- 플랫폼: Android native Kotlin
- backend: FastAPI
- database/auth/storage: Supabase
- llm 호출 위치: server only
- MVP 인터랙션: 앱 내 push-to-talk 음성 인터뷰
- 음성 파이프라인: STT -> text LLM -> TTS
- 텍스트 로그는 항상 저장
- 실시간 speech-to-speech 는 MVP에서 제외
- Milestone 1 server 범위는 실행 가능한 FastAPI 앱과 `GET /health` 까지만 구현한다.
- Milestone 1 app 범위는 접근성 우선 placeholder 화면과 단순 navigation 까지만 구현한다.
- server 의 Supabase 연결은 `.env` 기반 설정 모듈과 최소 클라이언트 초기화 코드로 분리한다.
- `/sessions` 구현은 실제 인증 미들웨어 전까지 임시 `X-User-Id` header 로 사용자 문맥을 받는다.
- `/messages/{session_id}` 구현도 실제 인증 미들웨어 전까지 임시 `X-User-Id` header 로 사용자 문맥을 받는다.
- `/voice/turn` 구현도 실제 인증 미들웨어 전까지 임시 `X-User-Id` header 로 사용자 문맥을 받는다.
- `/memory/extract` 구현도 실제 인증 미들웨어 전까지 임시 `X-User-Id` header 로 사용자 문맥을 받는다.
- OpenAI STT key 와 모델 설정은 server `.env` 로만 관리한다.
- OpenAI memory extraction 은 Structured Outputs 기반 Pydantic schema 로 파싱한다.
- OpenAI TTS 결과는 서버 로컬 mp3 파일로 저장하고 `/generated-audio/...` 경로로 노출한다.
- Android app 의 `/voice/turn` 연결은 테스트용 서버 주소, 사용자 ID, 세션 ID 입력값으로 동작시킨다.

## Done
- 프로젝트 문서 초안 작성
- MVP 범위 정의
- 마일스톤 계획 수립
- 노인 대상 음성 UX 원칙 정의
- 안전 대응 기본 원칙 정의
- `server/` FastAPI 최소 구조 정리
- `GET /health` 엔드포인트 확인
- `server/requirements.txt` 정리
- 서버 import 확인 및 `/health` smoke test 통과
- `server/.env.example` 추가
- Supabase 설정 모듈(`app.core.config`) 추가
- Supabase 최소 클라이언트 초기화 모듈(`app.db.supabase`) 추가
- 설정 import 및 Supabase 클라이언트 초기화 확인
- `POST /sessions`, `GET /sessions/{session_id}` request/response schema 추가
- Session service 계층과 기본 에러 처리 추가
- `/sessions` route smoke test 확인
- `GET /messages/{session_id}` response schema 추가
- Message service 계층과 기본 에러 처리 추가
- 세션 소유권 확인 후 메시지 `created_at` 오름차순 조회 추가
- `/messages` route smoke test 확인
- `POST /voice/turn` multipart 입력 처리 추가
- STT, 텍스트 응답 생성, TTS service 인터페이스와 mock 구현 추가
- transcript 저장과 assistant 메시지 저장을 포함한 VoiceTurn service 추가
- `/voice/turn` route smoke test 확인
- `POST /memory/extract` request/response schema 추가
- OpenAI memory extraction service 와 Structured Outputs Pydantic schema 추가
- memory_items 저장 service 와 fallback raw_text 저장 처리 추가
- `/memory/extract` route smoke test 확인
- OpenAI speech-to-text service 추가
- `UploadedAudio`, `SpeechToTextResult` 타입 확장
- `/voice/turn` 기본 STT 를 OpenAI service 로 교체
- OpenAI STT service 단위 smoke test 확인
- OpenAI text-to-speech service 추가
- `TextToSpeechResult` 에 mp3 파일 경로, 포맷, content type 정의 추가
- `/voice/turn` 기본 TTS 를 OpenAI service 로 교체
- FastAPI 정적 경로 `/generated-audio` 추가
- OpenAI TTS service 단위 smoke test 확인
- Android app 에 `/voice/turn` multipart 업로드 클라이언트 추가
- Voice Interview 화면에 서버 주소, 사용자 ID, 세션 ID 입력 필드 추가
- 업로드 로딩, transcript 표시, assistant 텍스트 표시, 실패 상태 표시 추가
- assistant 오디오 응답 URL 표시와 `MediaPlayer` 재생 연결 지점 추가
- Android app 기본 프로젝트 구조 생성
- Login, Home, Voice Interview, Draft, Book Preview placeholder 화면 추가
- Compose 기반 단일 Activity 와 navigation 구조 추가
- 이메일 로그인 입력 필드, 로그인 버튼, 로딩/실패 placeholder 상태 추가
- 로그인 ViewModel 과 placeholder AuthRepository 구조 추가
- 로그인 성공 시 Home 화면 이동 흐름 추가
- Voice Interview ViewModel 과 상태 구조 추가
- 큰 마이크 버튼, 현재 상태, 마지막 질문, 인식 결과, 다시 듣기, 다시 말하기, 세션 종료 UI 연결
- Voice Interview placeholder 이벤트 흐름(듣기 -> 변환 -> 답변 -> 재생 -> 대기) 추가
- `RECORD_AUDIO` 권한 요청 흐름 추가
- 권한 거부 상태 UI 및 재요청 흐름 추가
- MediaRecorder 기반 시작/중지 뼈대와 cache 임시 파일 저장 구조 추가
- `:app:compileDebugKotlin`, `:app:assembleDebug` 검증 통과

## In progress
- 없음

## Remaining issues
- Supabase 스키마와 인증은 아직 구현되지 않았다.
- `/voice/turn` 의 STT 와 TTS 는 OpenAI 기반이지만 assistant 텍스트 응답 생성은 아직 mock 구현이다.
- `/memory/extract` 는 OpenAI extraction 실패 시 raw_text 중심 fallback item 을 저장한다.
- `/voice/turn` 는 memory extraction, safety 판별, 오디오 장기 저장 없이 최소 응답만 반환한다.
- TTS 파일은 현재 서버 로컬 디스크에 저장되며 만료/정리 정책이 아직 없다.
- OpenAI TTS voices 는 영어 최적화 기준이라 한국어 음성 품질과 속도는 실기기 테스트가 필요하다.
- app 의 voice 업로드는 현재 실제 로그인/세션 생성 연동이 없어서 사용자 ID 와 세션 ID 를 수동 입력해야 한다.
- app 의 오디오 응답 재생은 연결 지점까지 구현했지만, 한국어 음성 품질과 로컬 서버 네트워크 조건은 수동 청취 테스트가 필요하다.
- memory, chapter 쪽 DB 접근용 service/repository 계층은 아직 없다.
- 로그인은 placeholder AuthRepository 기반이며 실제 Supabase Auth 연동이 아직 없다.
- Voice Interview 는 `/voice/turn` 업로드 흐름까지 연결했지만, 실제 auth/session 값을 자동 주입하는 연결은 아직 없다.
- MediaRecorder 동작과 권한 UX 는 실기기 확인이 아직 없다.
- Draft, Book Preview 화면은 아직 실제 데이터 연결이 없다.

## Next
1. Supabase 스키마 초안 작성
2. 로그인 화면에 실제 Supabase Auth 연동
3. app 의 로그인/세션 생성 흐름과 `/voice/turn` 입력값을 실제 auth/session 데이터로 연결
4. 서버의 chapter API 초안 구현
5. placeholder 화면을 실제 데이터 흐름과 연결

## Risks
- 범위가 커질 가능성
- 음성 UX를 너무 복잡하게 설계할 가능성
- 한국어 TTS 품질이 기대보다 떨어질 가능성
- 실기기 테스트가 늦어지면 음성 관련 문제를 늦게 발견할 수 있음

## Run / validation
app 실행:
1. `cd app`
2. `ANDROID_HOME=/Users/player7571/Library/Android/sdk ANDROID_SDK_ROOT=/Users/player7571/Library/Android/sdk ./gradlew :app:assembleDebug`

app 검증:
- Kotlin 컴파일 확인: `./gradlew :app:compileDebugKotlin`
- Debug 빌드 확인: `./gradlew :app:assembleDebug` 성공
- Voice Interview 수동 테스트:
- server 에서 `uvicorn app.main:app --reload` 실행
- 별도 터미널에서 같은 `X-User-Id` 로 세션을 먼저 생성해 `session_uuid` 확보
- Android Emulator 기준 서버 주소에 `http://10.0.2.2:8000` 입력
- app Voice Interview 화면에서 사용자 ID 와 세션 ID 입력
- 마이크 권한 허용 후 짧게 녹음하고 업로드
- 업로드 중 로딩 상태, transcript 표시, assistant 텍스트 표시 확인
- 서버를 중지하거나 잘못된 주소를 넣어 실패 상태 카드 표시 확인
- 다시 듣기 버튼으로 mp3 재생 연결이 시도되는지 확인

server .env 항목:
- `APP_ENV=local`
- `SUPABASE_URL=https://your-project-ref.supabase.co`
- `SUPABASE_ANON_KEY=your_supabase_anon_key`
- `SUPABASE_SERVICE_ROLE_KEY=your_supabase_service_role_key`
- `OPENAI_API_KEY=your_openai_api_key`
- `OPENAI_STT_MODEL=gpt-4o-transcribe`
- `OPENAI_STT_LANGUAGE=ko`
- `OPENAI_STT_PROMPT=optional_stt_prompt`
- `OPENAI_MEMORY_MODEL=gpt-4.1-mini`
- `OPENAI_MEMORY_PROMPT=optional_memory_prompt`
- `OPENAI_TTS_MODEL=gpt-4o-mini-tts`
- `OPENAI_TTS_VOICE=coral`
- `OPENAI_TTS_FORMAT=mp3`
- `OPENAI_TTS_PUBLIC_PATH=/generated-audio`
- `OPENAI_TTS_OUTPUT_DIR=.generated-audio`
- `OPENAI_TTS_INSTRUCTIONS=optional_tts_instructions`

server 실행:
1. `cd server`
2. `.env.example` 을 참고해 `.env` 작성
3. `python3 -m venv .venv`
4. `source .venv/bin/activate`
5. `pip install -r requirements.txt`
6. `uvicorn app.main:app --reload`
7. `curl http://127.0.0.1:8000/health`

server 검증:
- import 확인: `PYTHONPATH=/Users/player7571/storyvenue/server python -c "from app.main import app; print(app.title)"`
- 설정 확인: `PYTHONPATH=/Users/player7571/storyvenue/server python -c "from app.core.config import get_settings; print(get_settings().app_env)"`
- Supabase 클라이언트 확인: `PYTHONPATH=/Users/player7571/storyvenue/server python -c "from app.db.supabase import get_supabase_anon_client; print(type(get_supabase_anon_client()).__name__)"`
- OpenAI STT 설정 확인: `PYTHONPATH=/Users/player7571/storyvenue/server python -c "from app.core.config import get_settings; print(get_settings().openai_stt_model)"`
- OpenAI memory extraction 설정 확인: `PYTHONPATH=/Users/player7571/storyvenue/server python -c "from app.core.config import get_settings; print(get_settings().openai_memory_model)"`
- OpenAI TTS 설정 확인: `PYTHONPATH=/Users/player7571/storyvenue/server python -c "from app.core.config import get_settings; print(get_settings().openai_tts_model, get_settings().openai_tts_voice)"`
- `/health` 응답 확인: `{"status":"ok"}`
- 세션 생성 예시: `curl -X POST http://127.0.0.1:8000/sessions -H "Content-Type: application/json" -H "X-User-Id: <user_uuid>" -d '{"title":"어린 시절 인터뷰","theme":"childhood"}'`
- 세션 조회 예시: `curl http://127.0.0.1:8000/sessions/<session_uuid> -H "X-User-Id: <user_uuid>"`
- 메시지 조회 예시: `curl http://127.0.0.1:8000/messages/<session_uuid> -H "X-User-Id: <user_uuid>"`
- 음성 턴 예시: `curl -X POST http://127.0.0.1:8000/voice/turn -H "X-User-Id: <user_uuid>" -F "session_id=<session_uuid>" -F "audio_file=@sample.m4a" -F "language_hint=ko"`
- memory 추출 예시: `curl -X POST http://127.0.0.1:8000/memory/extract -H "Content-Type: application/json" -H "X-User-Id: <user_uuid>" -d '{"session_id":"<session_uuid>","message_id":"<message_uuid>"}'`
- OpenAI STT unit test 예시: `PYTHONPATH=/Users/player7571/storyvenue/server python -c "from app.services.stt_service import OpenAISpeechToTextService; print(OpenAISpeechToTextService.__name__)"`
- OpenAI memory extraction unit test 예시: `PYTHONPATH=/Users/player7571/storyvenue/server python -c "from app.services.memory_extraction_service import OpenAIMemoryExtractionService; print(OpenAIMemoryExtractionService.__name__)"`
- OpenAI TTS unit test 예시: `PYTHONPATH=/Users/player7571/storyvenue/server python -c "from app.services.tts_service import OpenAITextToSpeechService; print(OpenAITextToSpeechService.__name__)"`
- route smoke test:
- `/sessions` dependency override 기반 `POST 201`, `GET 200`, `GET missing 404` 확인
- `/messages` dependency override 기반 `GET 200`, 빈 세션 `GET 200 []`, 없는 세션 `GET 404` 확인
- `/memory/extract` dependency override 기반 `POST 200`, message missing `POST 404` 확인
- `/voice/turn` dependency override 기반 multipart `POST 200`, 빈 파일 `POST 400`, 없는 세션 `POST 404` 확인
- OpenAI STT service 는 fake OpenAI client 기반으로 transcript 반환과 빈 입력 예외를 확인
- OpenAI memory extraction service 는 fake OpenAI client 기반으로 structured parse 결과와 failure 경로를 확인
- OpenAI TTS service 는 fake OpenAI client 기반으로 mp3 파일 저장과 URL 반환을 확인

## Device test
- 현재 app milestone 은 빌드, 로그인 뼈대, Voice Interview 권한/녹음/업로드 흐름까지 검증했다.
- 실기기 테스트 필요 항목:
- `RECORD_AUDIO` 권한 허용 / 1회 거부 / 재요청 흐름 확인
- 녹음 시작 후 중지 시 `cache/voice-recordings/*.m4a` 임시 파일 생성 확인
- 너무 짧은 녹음에서도 앱이 비정상 종료하지 않는지 확인
- `/voice/turn` 업로드 후 transcript 와 assistant 텍스트가 정상 표시되는지 확인
- `/generated-audio/...mp3` 응답이 실제 기기에서 재생되는지 확인
- 세션 종료 시 진행 중 녹음 정리 동작 확인
- 실제 기기 마이크 품질과 MediaRecorder 시작/중지 타이밍 확인

## Changed files
- `server/requirements.txt`
- `server/.gitignore`
- `server/.env.example`
- `server/app/core/__init__.py`
- `server/app/core/config.py`
- `server/app/db/__init__.py`
- `server/app/db/supabase.py`
- `server/app/api/router.py`
- `server/app/api/dependencies/__init__.py`
- `server/app/api/dependencies/auth.py`
- `server/app/api/dependencies/services.py`
- `server/app/api/routes/memory.py`
- `server/app/api/routes/messages.py`
- `server/app/api/routes/sessions.py`
- `server/app/api/routes/voice.py`
- `server/app/api/schemas/__init__.py`
- `server/app/api/schemas/memory.py`
- `server/app/api/schemas/message.py`
- `server/app/api/schemas/session.py`
- `server/app/api/schemas/voice.py`
- `server/app/services/__init__.py`
- `server/app/services/memory_extraction_service.py`
- `server/app/services/memory_service.py`
- `server/app/services/message_service.py`
- `server/app/services/session_service.py`
- `server/app/services/stt_service.py`
- `server/app/services/text_generation_service.py`
- `server/app/services/tts_service.py`
- `server/app/services/voice_turn_service.py`
- `docs/API.md`
- `app/.gitignore`
- `app/settings.gradle.kts`
- `app/build.gradle.kts`
- `app/gradle.properties`
- `app/gradlew`
- `app/gradlew.bat`
- `app/gradle/wrapper/gradle-wrapper.jar`
- `app/gradle/wrapper/gradle-wrapper.properties`
- `app/app/build.gradle.kts`
- `app/app/proguard-rules.pro`
- `app/app/src/main/AndroidManifest.xml`
- `app/app/src/main/java/com/storyvenue/app/auth/AuthRepository.kt`
- `app/app/src/main/java/com/storyvenue/app/auth/PlaceholderAuthRepository.kt`
- `app/app/src/main/java/com/storyvenue/app/auth/LoginViewModel.kt`
- `app/app/src/main/java/com/storyvenue/app/voice/AudioReplyPlayer.kt`
- `app/app/src/main/java/com/storyvenue/app/voice/VoiceInterviewViewModel.kt`
- `app/app/src/main/java/com/storyvenue/app/voice/VoiceRecorder.kt`
- `app/app/src/main/java/com/storyvenue/app/voice/VoiceRecordingFileStore.kt`
- `app/app/src/main/java/com/storyvenue/app/voice/VoiceTurnRepository.kt`
- `app/app/src/main/res/values/strings.xml`
- `app/app/src/main/java/com/storyvenue/app/MainActivity.kt`
- `app/app/src/main/java/com/storyvenue/app/ui/StoryVenueApp.kt`

## Notes
- MVP는 텍스트 입력 대체가 아니라 음성 중심 인터뷰다.
- 음성 발화도 반드시 텍스트로 함께 저장한다.
- 안전 관련 발화는 자서전 인터뷰보다 우선한다.
- 음성 기능은 실기기에서 검증해야 한다.
- 로그인은 현재 placeholder 인증 흐름이며 실제 Supabase Auth 교체가 TODO 로 남아 있다.
- Voice Interview 는 현재 `/voice/turn` 업로드, transcript 표시, assistant 텍스트 표시, 오디오 재생 연결 지점까지 구현했고 auth/session 자동 연결이 TODO 로 남아 있다.
- `/sessions` 는 현재 임시 `X-User-Id` header 기반이며 실제 Supabase Auth 검증으로 교체해야 한다.
- `/messages` 도 현재 임시 `X-User-Id` header 기반이며 실제 Supabase Auth 검증으로 교체해야 한다.
- `/memory/extract` 도 현재 임시 `X-User-Id` header 기반이며 실제 Supabase Auth 검증으로 교체해야 한다.
- `/voice/turn` 도 현재 임시 `X-User-Id` header 기반이며 실제 Supabase Auth 검증으로 교체해야 한다.
- `/memory/extract` 는 현재 OpenAI Structured Outputs + fallback raw_text 저장으로 동작한다.
- `/voice/turn` 의 STT 와 TTS 는 현재 OpenAI service 로 처리하고, assistant 텍스트 응답 생성은 아직 mock 구현이다.
- 한국어 TTS voice 선택과 발화 속도는 실기기 청취 테스트 후 조정해야 한다.
- Android app 의 Voice Interview 업로드는 현재 테스트용 서버 주소, 사용자 ID, 세션 ID 수동 입력 방식이다.
