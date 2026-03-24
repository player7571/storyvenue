# Status.md

## Current project status
초기 문서 세팅 완료.
아직 코드 구현 전 단계.

## Decisions made
- 플랫폼: Android native Kotlin
- backend: FastAPI
- database/auth/storage: Supabase
- llm 호출 위치: server only
- MVP 인터랙션: 앱 내 push-to-talk 음성 인터뷰
- 음성 파이프라인: STT -> text LLM -> TTS
- 텍스트 로그는 항상 저장
- 실시간 speech-to-speech 는 MVP에서 제외

## Done
- 프로젝트 문서 초안 작성
- MVP 범위 정의
- 마일스톤 계획 수립
- 노인 대상 음성 UX 원칙 정의
- 안전 대응 기본 원칙 정의

## In progress
- 없음

## Next
1. app Android 프로젝트 생성
2. server FastAPI 뼈대 생성
3. Supabase 스키마 초안 작성
4. 로그인 화면 및 /health 엔드포인트 구현
5. Voice Interview 화면 뼈대 추가

## Risks
- 범위가 커질 가능성
- 음성 UX를 너무 복잡하게 설계할 가능성
- 한국어 TTS 품질이 기대보다 떨어질 가능성
- 실기기 테스트가 늦어지면 음성 관련 문제를 늦게 발견할 수 있음

## Notes
- MVP는 텍스트 입력 대체가 아니라 음성 중심 인터뷰다.
- 음성 발화도 반드시 텍스트로 함께 저장한다.
- 안전 관련 발화는 자서전 인터뷰보다 우선한다.
- 음성 기능은 실기기에서 검증해야 한다.