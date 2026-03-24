# Status.md

## Current project status
Milestone 1 범위의 server/app 뼈대 구현 완료.
현재는 FastAPI `/health` 와 Android placeholder 화면 5개가 연결된 최소 실행 단계다.

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
- Android app 기본 프로젝트 구조 생성
- Login, Home, Voice Interview, Draft, Book Preview placeholder 화면 추가
- Compose 기반 단일 Activity 와 navigation 구조 추가
- `:app:compileDebugKotlin`, `:app:assembleDebug` 검증 통과

## In progress
- 없음

## Remaining issues
- Supabase 스키마와 인증은 아직 구현되지 않았다.
- 음성 업로드, STT, TTS, safety 관련 API는 아직 없다.
- 앱 화면은 모두 placeholder 이며 실제 데이터 연결이 없다.

## Next
1. Supabase 스키마 초안 작성
2. 로그인 화면에 실제 인증 흐름 연결
3. Voice Interview 화면에 마이크 권한 및 상태 전이 연결
4. 서버의 세션/voice turn API 초안 구현
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

server 실행:
1. `cd server`
2. `python3 -m venv .venv`
3. `source .venv/bin/activate`
4. `pip install -r requirements.txt`
5. `uvicorn app.main:app --reload`
6. `curl http://127.0.0.1:8000/health`

server 검증:
- import 확인: `PYTHONPATH=/Users/player7571/storyvenue/server python -c "from app.main import app; print(app.title)"`
- `/health` 응답 확인: `{"status":"ok"}`

## Device test
- 현재 app milestone 은 빌드와 placeholder navigation 까지 검증했다.
- Voice Interview 화면의 마이크 권한, 녹음, TTS 를 붙이는 시점부터 실기기 테스트가 필요하다.

## Changed files
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
- `app/app/src/main/res/values/strings.xml`
- `app/app/src/main/java/com/storyvenue/app/MainActivity.kt`
- `app/app/src/main/java/com/storyvenue/app/ui/StoryVenueApp.kt`

## Notes
- MVP는 텍스트 입력 대체가 아니라 음성 중심 인터뷰다.
- 음성 발화도 반드시 텍스트로 함께 저장한다.
- 안전 관련 발화는 자서전 인터뷰보다 우선한다.
- 음성 기능은 실기기에서 검증해야 한다.
