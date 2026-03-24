# Status.md

## Current project status
Milestone 1 범위의 server 뼈대 구현 완료.
현재는 FastAPI 앱과 `/health` 확인 경로만 있는 최소 실행 단계다.

## Decisions made
- 플랫폼: Android native Kotlin
- backend: FastAPI
- database/auth/storage: Supabase
- llm 호출 위치: server only
- MVP는 텍스트 기반 인터뷰형 챗봇으로 제한
- Milestone 1 server 범위는 실행 가능한 FastAPI 뼈대와 `/health` 까지만 구현한다.

## Done
- 프로젝트 문서 초안 작성
- MVP 범위 정의
- 마일스톤 계획 수립
- `server/` FastAPI 최소 구조 생성
- `GET /health` 엔드포인트 추가
- `server/requirements.txt` 정리
- 서버 import 확인 및 `/health` smoke test 통과

## In progress
- 없음

## Remaining issues
- app Android 프로젝트는 아직 생성되지 않았다.
- Supabase 스키마와 인증은 아직 시작하지 않았다.
- 세션, 메시지, memory, chapter 관련 API는 아직 없다.

## Next
1. app Android 프로젝트 생성
2. Supabase 스키마 초안 작성
3. 인증 흐름 및 로그인 화면 범위 정리
4. session/message API 구현 준비

## Risks
- 기능 욕심으로 범위가 커질 가능성
- 안드로이드 UI와 서버를 동시에 시작하다가 일정이 꼬일 가능성
- OpenAI 응답 포맷 고정 실패 시 memory extraction 품질 저하 가능성

## Run / validation
실행:
1. `cd server`
2. `python3 -m venv .venv`
3. `source .venv/bin/activate`
4. `pip install -r requirements.txt`
5. `uvicorn app.main:app --reload`
6. `curl http://127.0.0.1:8000/health`

검증:
- 로컬 import 확인: `from app.main import app`
- `/health` 응답 확인: `{"status":"ok"}`

## Notes
- 현재 우선순위는 AGENTS.md 기준으로 로그인 + 세션 + 메시지 저장 + memory 추출 + chapter 생성 + draft 수정까지다.
- `autobiography_version` 저장은 후반 milestone 에서 최소 범위로 다룬다.
- 음성 기능은 절대 초기에 넣지 않는다.
