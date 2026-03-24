# DB_SCHEMA.md

## Overview
MVP 기준 핵심 테이블:
- profiles
- sessions
- messages
- memory_items
- chapter_drafts
- autobiography_versions

---

## profiles
설명:
- 사용자 기본 프로필

Columns:
- id: uuid, primary key
- email: text
- display_name: text, nullable
- created_at: timestamptz

---

## sessions
설명:
- 인터뷰 단위 세션

Columns:
- id: uuid, primary key
- user_id: uuid, not null
- title: text, not null
- theme: text, nullable
- status: text, default 'active'
- created_at: timestamptz

관계:
- sessions.user_id -> profiles.id

---

## messages
설명:
- 채팅 메시지 저장

Columns:
- id: uuid, primary key
- session_id: uuid, not null
- user_id: uuid, not null
- role: text, not null
  - allowed: user, assistant, system
- content: text, not null
- created_at: timestamptz

관계:
- messages.session_id -> sessions.id
- messages.user_id -> profiles.id

---

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
- created_at: timestamptz

관계:
- memory_items.session_id -> sessions.id
- memory_items.message_id -> messages.id
- memory_items.user_id -> profiles.id

---

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
- version_no: integer, default 1
- created_at: timestamptz
- updated_at: timestamptz

관계:
- chapter_drafts.user_id -> profiles.id
- chapter_drafts.session_id -> sessions.id

---

## autobiography_versions
설명:
- 최종 자서전 버전 저장

Columns:
- id: uuid, primary key
- user_id: uuid, not null
- title: text, not null
- content: text, not null
- created_at: timestamptz

관계:
- autobiography_versions.user_id -> profiles.id

---

## Initial RLS direction
모든 사용자 데이터 테이블은
- 인증된 사용자만 접근 가능
- user_id = auth.uid() 인 데이터만 조회/수정 가능
원칙을 따른다.

대상:
- sessions
- messages
- memory_items
- chapter_drafts
- autobiography_versions