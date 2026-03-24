# AGENTS.md

## Project
이 프로젝트는 노인 사용자가 음성으로 AI와 대화하며 자신의 삶을 기록하고,
그 대화 내용을 바탕으로 장별 자서전 초안을 생성하는 안드로이드 앱이다.

## Primary stack
- app: Android native Kotlin
- backend: FastAPI
- database/auth/storage: Supabase
- llm: OpenAI API
- voice pipeline: speech-to-text -> text LLM -> text-to-speech

## Product goal
MVP 목표는 아래 기능을 실제로 동작시키는 것이다.
1. 이메일 회원가입 / 로그인
2. 음성 인터뷰 세션 생성
3. 음성 입력 업로드
4. STT 결과를 텍스트 로그로 저장
5. AI가 다음 질문 또는 짧은 반응 생성
6. 응답을 TTS로 음성 재생
7. 답변에서 memory item 추출
8. 장별 자서전 초안 생성
9. 초안 수정 / 재생성
10. 최종 자서전 버전 저장

## Non-goals for MVP
다음 기능은 MVP 범위에서 제외한다.
- 전화망 연동
- 실시간 양방향 speech-to-speech
- 가족 공동 편집
- PDF export
- 푸시 알림
- 감정 분석 점수화
- 고도화된 벡터 검색
- 관리자 페이지
- 완전 무중단 백그라운드 녹음

## Voice-first product rules
- 이 앱의 기본 인터랙션은 음성이다.
- 모든 핵심 흐름은 "듣기 쉬움, 답하기 쉬움, 실수 복구 쉬움"을 우선한다.
- 한 번에 질문은 하나만 한다.
- 음성 응답은 짧고 분명해야 한다.
- 사용자가 말을 마칠 시간을 충분히 준다.
- 이름, 장소, 날짜, 관계 정보는 필요 시 다시 확인한다.
- 잘못 들었을 가능성이 있으면 단정하지 말고 확인 질문을 한다.
- 사용자가 답하기 어려워하면 더 쉬운 표현으로 다시 묻는다.
- 텍스트 자막 또는 인식 결과를 항상 함께 보여준다.

## Accessibility rules
- 버튼은 크고 명확해야 한다.
- 주요 액션은 텍스트와 아이콘을 함께 제공한다.
- 현재 상태를 항상 보여준다.
  - 듣는 중
  - 변환 중
  - 답변 생성 중
  - 음성 재생 중
- "다시 듣기", "다시 말하기", "텍스트 보기" 경로를 제공한다.
- 화면의 핵심 정보는 한 번에 너무 많이 보여주지 않는다.

## Safety and escalation
- 사용자가 응급상황, 쓰러짐, 호흡곤란, 심한 통증, 자해/자살 의사, 극심한 불안 등을 말하면 자서전 인터뷰를 멈추고 안전 대응을 우선한다.
- 위험 신호가 있으면 일반 인터뷰 톤을 중단하고 짧고 분명한 안전 확인 문장으로 전환한다.
- 진단하지 않는다.
- 필요 시 보호자, 주변 사람, 응급 도움 요청을 우선 안내한다.
- 인식 실패 또는 의미 파악 실패가 2~3회 연속 발생하면 더 쉬운 질문으로 바꾸거나 텍스트 확인을 유도한다.

## Working style
- 항상 먼저 현재 상태를 파악한다.
- 큰 작업은 작은 마일스톤으로 나눈다.
- 한 번에 하나의 마일스톤만 구현한다.
- 관련 없는 리팩터링은 하지 않는다.
- 새 라이브러리를 추가할 때는 이유를 먼저 설명한다.
- 임시 코드보다 실행 가능한 최소 구현을 우선한다.
- 시연 가능한 최소 경로를 먼저 만든다.
- OpenAI API key 는 절대 앱 코드에 넣지 않는다.
- OpenAI 호출은 반드시 server 에서만 수행한다.

## Repository conventions
- app/ 는 Android Kotlin 프로젝트만 둔다.
- server/ 는 FastAPI 프로젝트만 둔다.
- docs/ 는 설계 및 상태 기록 문서만 둔다.
- 새로운 상위 디렉터리를 임의로 만들지 않는다.
- 파일 이름은 의미가 분명해야 한다.
- 주석은 왜 필요한지 중심으로 짧게 쓴다.

## App conventions
- Kotlin 우선
- UI는 단순하고 크게
- MVP 단계에서는 화려한 디자인보다 접근성을 우선한다.
- 화면은 Login, Home, Voice Interview, Draft, Book Preview 를 우선 구현한다.
- 네트워크 실패, 로딩, 빈 상태를 처리한다.
- 마이크 권한 거부 상태를 명확하게 처리한다.
- 실기기 테스트를 우선한다.

## Server conventions
- FastAPI + Pydantic 사용
- 엔드포인트는 명확한 request/response schema 를 가진다.
- 환경변수는 .env 로 관리한다.
- DB 접근은 service 또는 repository 계층으로 분리한다.
- STT, text generation, TTS 호출은 분리된 service 로 둔다.
- memory extraction 은 구조화된 JSON 형태를 우선한다.

## Database conventions
- Supabase Postgres 사용
- 모든 사용자 데이터 테이블은 user_id 와 created_at 을 가진다.
- RLS 를 기본 전제로 설계한다.
- 테이블 이름은 snake_case 사용
- 스키마 변경 시 docs/DB_SCHEMA.md 도 갱신한다.

## Required validation
### app 변경 후
- Gradle sync 확인
- 컴파일 오류가 없어야 한다
- 마이크 권한 흐름 점검
- 가능한 경우 실기기에서 음성 입력 동작 확인

### server 변경 후
- 서버 실행 가능 여부 확인
- import 오류가 없어야 한다
- 최소 API smoke test 실행
- 음성 업로드 요청이 실패 없이 처리되는지 확인

### docs 변경 후
- 문서 간 모순이 없는지 확인

## Status logging
각 마일스톤 종료 후 docs/Status.md 에 아래를 기록한다.
- 완료한 내용
- 남은 문제
- 다음 작업
- 검증 방법
- 실기기 테스트 필요 여부

## Implementation priorities
항상 아래 순서를 우선한다.
1. 프로젝트 뼈대
2. DB 스키마
3. 인증
4. 음성 인터뷰 기본 흐름
5. STT 저장
6. 텍스트 응답 생성
7. TTS 재생
8. memory item 추출
9. chapter draft 생성
10. 초안 수정

## If requirements are ambiguous
- MVP에 유리한 방향으로 보수적으로 결정한다.
- 가정한 내용을 docs/Status.md 에 남긴다.

## Definition of done
어떤 기능이 끝났다고 판단하려면 아래를 만족해야 한다.
- 코드가 저장소 구조와 규칙을 따른다.
- 최소 실행 경로가 있다.
- 필요한 문서가 함께 업데이트되었다.
- 다음 사람이 이어서 볼 수 있게 상태가 기록되었다.