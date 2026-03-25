# DB_SCHEMA.md

## Overview
MVP 기준 핵심 테이블:
- profiles
- sessions
- messages
- memory_items
- chapter_drafts
- autobiography_versions
- feed_posts
- feed_comments
- feed_read_events
- chat_rooms
- chat_messages

실제 적용용 SQL 은 [server/sql/mvp_schema.sql](/Users/player7571/storyvenue/server/sql/mvp_schema.sql) 에 있다.
소셜 피드/채팅 확장 SQL 은 [server/sql/social_feed_schema.sql](/Users/player7571/storyvenue/server/sql/social_feed_schema.sql) 에 있다.
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

## feed_posts
설명:
- 저장된 자서전 버전을 공개 피드에 게시한 글
- 추천 알고리즘이 사용할 AI 분석 결과를 함께 저장

Columns:
- id: uuid, primary key
- book_id: uuid, not null, unique
- user_id: uuid, not null
- author_name: text, not null
- title: text, not null
- excerpt: text, not null
- content: text, not null
- summary: text, nullable
- topics: jsonb, not null, default `[]`
- emotions: jsonb, not null, default `[]`
- experiences: jsonb, not null, default `[]`
- visibility: text, default `public`
- created_at: timestamptz, not null
- updated_at: timestamptz, not null

## feed_comments
설명:
- 피드 글에 달린 댓글

Columns:
- id: uuid, primary key
- post_id: uuid, not null
- user_id: uuid, not null
- author_name: text, not null
- content: text, not null
- created_at: timestamptz, not null

## feed_read_events
설명:
- 추천 알고리즘용 읽기 행동 로그
- 체류 시간, 완독 여부, 검색어를 저장

Columns:
- id: uuid, primary key
- post_id: uuid, not null
- user_id: uuid, not null
- dwell_seconds: integer, not null, default `0`
- completed: boolean, not null, default `false`
- query_text: text, nullable
- created_at: timestamptz, not null

## chat_rooms
설명:
- 사용자 간 1:1 채팅방

Columns:
- id: uuid, primary key
- room_key: text, unique
- member_a_id: uuid, not null
- member_b_id: uuid, not null
- created_at: timestamptz, not null

## chat_messages
설명:
- 채팅방 메시지

Columns:
- id: uuid, primary key
- room_id: uuid, not null
- sender_id: uuid, not null
- sender_name: text, not null
- content: text, not null
- created_at: timestamptz, not null

## RLS direction
모든 사용자 데이터 테이블은 기본적으로 아래 정책을 따른다.
- 인증된 사용자만 접근 가능
- `user_id = auth.uid()` 인 데이터만 조회/수정 가능
- `profiles` 는 `id = auth.uid()` 기준으로 조회/수정 가능
- `feed_posts` 는 인증 사용자에게 공개 조회 가능하지만 작성자는 본인만 insert/update 가능
- `feed_comments` 는 공개 피드 글에 대해 인증 사용자라면 조회 가능하고, 작성은 본인 댓글만 가능
- `feed_read_events` 는 본인 로그만 조회/작성 가능
- `chat_rooms`, `chat_messages` 는 해당 멤버만 조회/작성 가능

## Audio handling direction
- 음성 원본 파일은 장기 보관하지 않는다.
- TTS 생성 파일은 서버 로컬 `.generated-audio` 에 저장한다.
- 기본 정리 정책은 `OPENAI_TTS_RETENTION_HOURS=24` 이다.
- STT transcript 와 생성 결과는 DB 에 저장한다.
