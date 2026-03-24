# Plan.md

## Planning notes
- 앱 관련 milestone 은 각 화면/기능에 대해 최소한 loading, error, empty state 를 함께 포함한다.
- 서버 관련 milestone 은 구현과 함께 request/response schema 와 Pydantic 모델을 같이 확정한다.
- 핵심 MVP 흐름은 Milestone 1~7 우선이며, autobiography_version 저장은 그 이후 최소 범위로 마무리한다.

## Milestone 1 - Repository scaffold
목표:
- app/, server/, docs/ 구조 정리
- Android 프로젝트 생성
- FastAPI 프로젝트 생성
- 기본 실행 확인

Acceptance criteria:
- app 디렉터리에 안드로이드 프로젝트가 존재한다.
- server 디렉터리에 FastAPI 프로젝트가 존재한다.
- /health 엔드포인트가 동작한다.
- docs/Status.md 에 초기 상태가 기록된다.

Validation:
- 서버 실행 후 /health 응답 확인
- 앱 프로젝트가 열리고 빌드 가능한 상태인지 확인

---

## Milestone 2 - Supabase schema and auth foundation
목표:
- Supabase 프로젝트 연결 준비
- profiles, sessions, messages, memory_items, chapter_drafts, autobiography_versions 테이블 설계
- user_id, created_at, RLS 기준 확정
- 기본 인증 흐름에 필요한 데이터 경계 정리

Acceptance criteria:
- docs/DB_SCHEMA.md 가 최신 상태다.
- 사용자 데이터 테이블 규칙(user_id, created_at)이 문서에 반영된다.
- RLS 적용 방향이 정리된다.
- 서버 환경변수에 Supabase 연결 정보가 정리된다.

Validation:
- 서버에서 DB 연결 확인
- 스키마 및 RLS 설계 문서 검토

---

## Milestone 3 - Authentication
목표:
- 이메일 회원가입 / 로그인 연결
- 로그인 세션 유지 기본 구현

Acceptance criteria:
- 사용자가 이메일로 회원가입 가능
- 로그인 가능
- 로그인 상태에 따라 화면 분기 가능

Validation:
- 새 계정 생성 테스트
- 로그인/로그아웃 테스트

---

## Milestone 4 - Session and message storage
목표:
- 인터뷰 세션 생성
- 챗봇 질문 표시를 위한 최소 질문 공급(고정 질문 또는 서버 템플릿)
- 질문/답변 저장
- 저장된 메시지 불러오기

Acceptance criteria:
- 새 세션 생성 가능
- 세션 시작 시 첫 질문이 표시된다.
- 메시지 저장 가능
- assistant / user 메시지 역할이 구분되어 저장된다.
- 기존 세션 메시지 조회 가능

Validation:
- 세션 생성 후 assistant / user 메시지 3개 이상 저장
- 앱 재실행 후 메시지 재조회

---

## Milestone 5 - Memory extraction
목표:
- 서버에서 OpenAI API 호출
- 답변 텍스트에서 memory item 구조화 추출
- 구조화 응답 schema 검증

Acceptance criteria:
- 답변 1건 이상에 대해 memory item 저장 가능
- OpenAI 응답이 Pydantic schema 로 검증된다.
- 추출 실패 시 안전한 fallback 처리 존재

Validation:
- 샘플 답변으로 구조화 결과 확인
- DB 저장 확인

---

## Milestone 6 - Chapter draft generation
목표:
- memory items 묶음 기반 chapter draft 생성
- 장 제목과 본문 생성

Acceptance criteria:
- 특정 세션/주제에 대한 chapter draft 생성 가능
- 생성 결과가 DB에 저장됨

Validation:
- 최소 1개 장 생성
- 생성 결과 조회 및 DB 저장 확인

---

## Milestone 7 - Draft editing and regeneration
목표:
- 초안 수정
- 수정 요청 기반 재생성

Acceptance criteria:
- draft 수정 가능
- 재생성 가능
- 이전 버전 또는 최신 버전 구분 가능

Validation:
- draft 수정 테스트
- 재생성 후 결과 비교

---

## Milestone 8 - Autobiography version save
목표:
- 여러 chapter draft 를 합쳐 최종 자서전 버전 생성
- 저장된 최종 버전 다시 조회

Acceptance criteria:
- chapter ordering 지원
- 최종 버전 저장 가능
- 최신 저장 버전 조회 가능

Validation:
- 2개 이상 chapter 를 합친 버전 생성
- 저장 후 다시 조회 가능

---

## Milestone 9 - Cleanup for demo
목표:
- 데모 시나리오 정리
- 오류 메시지 개선
- 남은 빈 상태 / 실패 상태 누락 보완

Acceptance criteria:
- 발표용 데모 경로가 명확하다.
- 실패 상태가 최소한 처리된다.
- docs/Status.md 와 docs/API.md 가 최신이다.

Validation:
- 처음 로그인부터 최종 버전 저장까지 end-to-end 점검
