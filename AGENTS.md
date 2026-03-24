# AGENTS.md

## Project
이 프로젝트는 안드로이드 앱에서 사용자가 챗봇과 대화하며 자신의 기억을 기록하고,
그 대화 내용을 기반으로 장별 자서전 초안을 생성하는 서비스다.

## Primary stack
- app: Android native Kotlin
- backend: FastAPI
- database/auth/storage: Supabase
- llm orchestration: server-side prompt flow
- llm provider: OpenAI API

## Product goal
MVP 목표는 아래 6가지를 동작시키는 것이다.
1. 회원가입 / 로그인
2. 인터뷰 세션 생성
3. 질문/답변 저장
4. 답변에서 memory item 추출
5. 장별 자서전 초안 생성
6. 초안 수정 / 재생성

## Non-goals for MVP
다음 기능은 MVP 범위에서 제외한다.
- 음성 통화 인터뷰
- 실시간 음성 STT/TTS
- 가족 공동 편집
- 푸시 알림
- PDF export
- 감정 분석 점수화
- 벡터 검색 고도화
- 관리자 페이지

## Working style
- 항상 먼저 현재 상태를 파악하고, 필요한 경우 계획부터 제시한다.
- 큰 작업은 작은 마일스톤으로 나눈다.
- 한 번에 하나의 마일스톤만 구현한다.
- 관련 없는 리팩터링은 하지 않는다.
- 새 라이브러리를 추가할 때는 이유를 먼저 설명한다.
- 임시 코드보다 실행 가능한 최소 구현을 우선한다.
- 동작을 확인할 수 있는 최소 UI와 최소 API를 먼저 만든다.
- 서버 비밀값(API key, service role key)은 절대 앱 코드에 넣지 않는다.
- OpenAI API 호출은 반드시 server 쪽에서만 처리한다.

## Repository conventions
- app/ 는 Android Kotlin 프로젝트만 둔다.
- server/ 는 FastAPI 프로젝트만 둔다.
- docs/ 는 기획, 설계, 상태 기록 문서만 둔다.
- 새로운 상위 디렉터리를 임의로 만들지 않는다.
- 파일 이름은 의미가 분명해야 한다.
- 주석은 “왜 필요한지” 중심으로 짧게 쓴다.

## App conventions
- Kotlin 우선
- UI는 단순하고 명확하게
- MVP 단계에서는 복잡한 디자인 시스템을 만들지 않는다.
- 화면은 Login, Home, Chat, Draft 정도만 우선 구현한다.
- 네트워크 호출 실패, 로딩 상태, 빈 상태를 처리한다.

## Server conventions
- FastAPI + Pydantic 사용
- 엔드포인트는 명확한 request/response schema를 가진다.
- 환경변수는 .env 로 관리한다.
- DB 접근은 서비스 계층 또는 repository 계층으로 분리한다.
- OpenAI 응답은 가능한 한 구조화된 JSON 형태로 받는다.

## Database conventions
- Supabase Postgres 사용
- 모든 사용자 데이터 테이블은 user_id 와 created_at 을 가진다.
- RLS를 기본으로 고려한다.
- 테이블 이름은 snake_case 사용
- 스키마 변경 시 docs/DB_SCHEMA.md 도 갱신한다.

## Required validation
작업 후 가능한 한 아래 검증을 실행한다.

### app 변경 후
- Gradle sync 확인
- 프로젝트 빌드 가능 여부 확인
- 최소한 컴파일 오류가 없어야 한다

### server 변경 후
- 서버 실행 가능 여부 확인
- import 오류가 없어야 한다
- 가능한 경우 최소 API smoke test 실행

### docs 변경 후
- 문서 간 모순이 없는지 확인

## Status logging
각 마일스톤이 끝나면 docs/Status.md 에 아래를 기록한다.
- 완료한 내용
- 남은 문제
- 다음 작업
- 실행/검증 방법

## Implementation priorities
항상 아래 순서를 우선한다.
1. 프로젝트 뼈대
2. DB 스키마
3. 인증
4. 세션/메시지 저장
5. memory item 추출
6. chapter draft 생성
7. 초안 수정

## If requirements are ambiguous
- 일단 MVP에 유리한 방향으로 보수적으로 결정한다.
- 가정한 내용을 docs/Status.md 에 남긴다.

## Definition of done
어떤 기능이 끝났다고 판단하려면 아래를 만족해야 한다.
- 코드가 저장소 구조와 규칙을 따른다.
- 최소한의 실행 경로가 있다.
- 필요한 문서가 함께 업데이트되었다.
- 다음 사람이 이어서 볼 수 있게 상태가 기록되었다.