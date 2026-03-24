# Status.md

## Current project status
초기 문서 세팅 완료.
아직 코드 구현 전 단계.

## Decisions made
- 플랫폼: Android native Kotlin
- backend: FastAPI
- database/auth/storage: Supabase
- llm 호출 위치: server only
- MVP는 텍스트 기반 인터뷰형 챗봇으로 제한

## Done
- 프로젝트 문서 초안 작성
- MVP 범위 정의
- 마일스톤 계획 수립

## In progress
- 없음

## Next
1. app Android 프로젝트 생성
2. server FastAPI 뼈대 생성
3. Supabase 스키마 초안 작성
4. 로그인 화면 및 /health 엔드포인트 구현

## Risks
- 기능 욕심으로 범위가 커질 가능성
- 안드로이드 UI와 서버를 동시에 시작하다가 일정이 꼬일 가능성
- OpenAI 응답 포맷 고정 실패 시 memory extraction 품질 저하 가능성

## Notes
- MVP는 로그인 + 세션 + 메시지 저장 + memory 추출 + chapter 생성까지다.
- 음성 기능은 절대 초기에 넣지 않는다.