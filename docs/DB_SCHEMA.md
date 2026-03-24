# DB_SCHEMA.md

## Overview
MVP 기준 핵심 테이블:
- profiles
- sessions
- messages
- memory_items
- chapter_drafts
- autobiography_versions

실제 적용용 SQL 은 [server/sql/mvp_schema.sql](/Users/player7571/storyvenue/server/sql/mvp_schema.sql) 에 있다.
현재 앱/서버 구현은 아래 컬럼을 전제로 동작한다.

## profiles
설명:
- Supabase Auth 사용자와 1:1 대응되는 기본 프로필

Columns:
- id: uuid, primary key, `auth.users.id` 참조
- email: text, nullable
- display_name: text, nullable
- created_at: timestamptz, not null

## sessions
설명:
- 인터뷰 단위 세션

Columns:
- id: uuid, primary key
- user_id: uuid, not null
- title: text, not null
- theme: text, nullable
- status: text, default `active`
- created_at: timestamptz, not null

## messages
설명:
- 사용자와 assistant 의 텍스트 발화 저장

Columns:
- id: uuid, primary key
- session_id: uuid, not null
- user_id: uuid, not null
- role: text, not null
  allowed: `user`, `assistant`, `system`
- content: text, not null
- source_type: text, not null, default `text`
  allowed: `text`, `stt`, `generated`
- stt_confidence: numeric, nullable
- safety_mode: boolean, not null, default `false`
- created_at: timestamptz, not null

## memory_items
설명:
- 사용자 답변에서 추출한 구조화된 기억 단위

Columns:
- id: uuid, primary key
- session_id: uuid, not null
- message_id: uuid, nullable
- user_id: uuid, not null
- period: text, nullable
- place: text, nullable
- person: text, nullable
- event: text, nullable
- emotions: jsonb, nullable
- meaning: text, nullable
- raw_text: text, nullable
- created_at: timestamptz, not null

## chapter_drafts
설명:
- 장별 자서전 초안

Columns:
- id: uuid, primary key
- user_id: uuid, not null
- session_id: uuid, nullable
- chapter_type: text, nullable
- title: text, not null
- content: text, not null
- version_no: integer, not null, default `1`
- created_at: timestamptz, not null
- updated_at: timestamptz, not null

## autobiography_versions
설명:
- 최종 자서전 저장 버전

Columns:
- id: uuid, primary key
- user_id: uuid, not null
- title: text, not null
- content: text, not null
- chapter_ids: jsonb, not null, default `[]`
- created_at: timestamptz, not null

## RLS direction
모든 사용자 데이터 테이블은 기본적으로 아래 정책을 따른다.
- 인증된 사용자만 접근 가능
- `user_id = auth.uid()` 인 데이터만 조회/수정 가능
- `profiles` 는 `id = auth.uid()` 기준으로 조회/수정 가능

## Audio handling direction
- 음성 원본 파일은 장기 보관하지 않는다.
- TTS 생성 파일은 서버 로컬 `.generated-audio` 에 저장한다.
- 기본 정리 정책은 `OPENAI_TTS_RETENTION_HOURS=24` 이다.
- STT transcript 와 생성 결과는 DB 에 저장한다.
