# Plan.md

## Planning notes
- 음성 MVP 는 push-to-talk 기반 반이중 흐름으로 제한하고, 실시간 양방향 speech-to-speech 는 다루지 않는다.
- Voice Interview 흐름에는 상태 표시, 인식 결과 텍스트, 다시 듣기, 다시 말하기, 마이크 권한 거부 처리를 기본 포함한다.
- 안전 대응은 마지막 polish 가 아니라 초기 음성 인터뷰 흐름부터 인터럽트 경로와 최소 logging 기준을 가진다.

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
- RLS 와 safety_mode logging 기준 정리
- 음성 파일 최소 보관 원칙 정리
- 기본 인증 흐름 준비

Acceptance criteria:
- docs/DB_SCHEMA.md 가 최신 상태다.
- 사용자 데이터와 safety 관련 로그 접근 기준이 정리된다.
- 음성 파일 보관/삭제 원칙이 문서에 반영된다.
- 서버 환경변수에 Supabase 연결 정보가 정리된다.
- 앱에 로그인 화면 뼈대가 존재한다.

Validation:
- 서버에서 DB 연결 확인
- 인증 흐름 및 storage 정책 문서 검토

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

## Milestone 4 - Voice input/output MVP
목표:
- 앱에서 음성 입력 받기
- 서버에 오디오 업로드
- 서버 응답을 TTS 음성으로 재생
- 현재 상태, 마지막 질문, 인식 결과를 화면에 표시
- 다시 듣기 / 다시 말하기 / 마이크 권한 거부 흐름 처리
- 위험 발화 시 일반 인터뷰 대신 짧은 안전 안내로 전환

Acceptance criteria:
- 사용자가 버튼을 눌러 음성을 녹음할 수 있다.
- 마이크 권한 허용/거부 상태가 명확히 처리된다.
- 음성이 텍스트로 변환되어 화면에 표시된다.
- 서버 응답이 텍스트와 음성으로 제공된다.
- 듣는 중 / 변환 중 / 답변하는 중 / 재생 중 상태가 보인다.
- 마지막 질문 다시 듣기와 다시 말하기 경로가 존재한다.
- 위험 발화 샘플 1건 이상에서 안전 응답으로 전환된다.

Validation:
- 실제 안드로이드 기기에서 테스트
- 3턴 이상 음성 인터뷰 가능
- STT 실패 시 재시도 흐름 확인
- 위험 발화 샘플 테스트

---

## Milestone 5 - Session and message storage
목표:
- 인터뷰 세션 생성
- 질문/답변 저장
- STT 결과를 텍스트 로그로 저장
- 저장된 메시지 불러오기
- safety_mode 진입 여부 기록

Acceptance criteria:
- 새 세션 생성 가능
- 사용자 발화와 AI 발화 저장 가능
- 사용자 STT 텍스트 로그가 저장된다.
- 기존 세션 메시지 조회 가능
- safety_mode 진입 메시지가 구분되어 기록된다.

Validation:
- 세션 생성 후 사용자/assistant 메시지 3개 이상 저장
- 앱 재실행 후 메시지 재조회
- safety_mode 진입 로그 확인

---

## Milestone 6 - Memory extraction
목표:
- 서버에서 답변 텍스트 기반 memory item 구조화 추출
- 추출 결과를 DB 저장

Acceptance criteria:
- 답변 1건 이상에 대해 memory item 저장 가능
- 추출 실패 시 안전한 fallback 처리 존재

Validation:
- 샘플 답변으로 구조화 결과 확인
- DB 저장 확인

---

## Milestone 7 - Chapter draft generation
목표:
- memory items 묶음 기반 chapter draft 생성
- 장 제목과 본문 생성

Acceptance criteria:
- 특정 세션/주제에 대한 chapter draft 생성 가능
- 생성 결과가 DB에 저장됨

Validation:
- 최소 1개 장 생성
- 생성 결과 저장 확인

---

## Milestone 8 - Draft editing and regeneration
목표:
- 초안 수정
- 수정 요청 / 재생성

Acceptance criteria:
- draft 수정 가능
- 재생성 가능
- 이전 버전 또는 최신 버전 구분 가능

Validation:
- draft 수정 테스트
- 재생성 후 결과 비교

---

## Milestone 9 - Book assembly
목표:
- 여러 chapter draft 를 합쳐 최종 자서전 버전 생성

Acceptance criteria:
- chapter ordering 지원
- 최종 버전 저장 가능
- 미리보기 가능

Validation:
- 2개 이상 chapter 를 합친 버전 생성
- 저장 후 다시 조회 가능

---

## Milestone 10 - Safety hardening and demo polish
목표:
- 위험 신호 감지 범위와 안전 문구 보강
- 오류 메시지, 빈 화면, 로딩 상태 개선
- 발표용 데모 시나리오 정리

Acceptance criteria:
- 주요 위험 발화 샘플에 대해 안전 대응 흐름이 점검된다.
- safety_mode logging 및 권한 제어 기준이 최신 상태다.
- 발표용 데모 경로가 명확하다.
- docs/API.md, docs/Status.md 가 최신 상태다.

Validation:
- end-to-end 데모 점검
- 위험 발화 샘플 테스트
