# Status.md

## Current project status
Milestone 1 범위의 server 뼈대 구현 완료.
현재는 FastAPI 앱과 `/health` 확인 경로만 있는 최소 실행 단계다.

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

## In progress
- 없음

## Remaining issues
- app Android 프로젝트는 아직 없다.
- Supabase 스키마와 인증은 아직 구현되지 않았다.
- 음성 업로드, STT, TTS, safety 관련 API는 아직 없다.

## Next
1. app Android 프로젝트 생성
2. Supabase 스키마 초안 작성
3. 로그인 화면 및 인증 흐름 구현
4. Voice Interview 화면 뼈대 추가
5. 음성 업로드 API 범위 정리

## Risks
- 범위가 커질 가능성
- 음성 UX를 너무 복잡하게 설계할 가능성
- 한국어 TTS 품질이 기대보다 떨어질 가능성
- 실기기 테스트가 늦어지면 음성 관련 문제를 늦게 발견할 수 있음

## Run / validation
실행:
1. `cd server`
2. `python3 -m venv .venv`
3. `source .venv/bin/activate`
4. `pip install -r requirements.txt`
5. `uvicorn app.main:app --reload`
6. `curl http://127.0.0.1:8000/health`

검증:
- import 확인: `PYTHONPATH=/Users/player7571/storyvenue/server python -c "from app.main import app; print(app.title)"`
- `/health` 응답 확인: `{"status":"ok"}`

## Device test
- 현재 milestone 은 server 뼈대만 포함하므로 실기기 테스트는 아직 필요하지 않다.
- Voice Interview 화면과 음성 업로드를 시작하는 시점부터 실기기 테스트가 필요하다.

## Notes
- MVP는 텍스트 입력 대체가 아니라 음성 중심 인터뷰다.
- 음성 발화도 반드시 텍스트로 함께 저장한다.
- 안전 관련 발화는 자서전 인터뷰보다 우선한다.
- 음성 기능은 실기기에서 검증해야 한다.
