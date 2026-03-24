# API.md

## Base URL
- local: `http://localhost:8000`
- Android Emulator: `http://10.0.2.2:8000`
- production: TBD

## Auth strategy
- 앱은 서버의 `/auth/*` 엔드포인트로 이메일 회원가입/로그인을 수행한다.
- 서버는 Supabase Auth 로 실제 인증을 처리한다.
- 이후 앱은 `Authorization: Bearer <access_token>` 으로 요청한다.
- 로컬 개발에서만 `X-User-Id` fallback 을 허용할 수 있다.

## GET /health
응답:
```json
{"status":"ok"}
```

## POST /auth/sign-up
설명:
- 이메일 회원가입
- 이메일 확인이 켜져 있으면 session 없이 `requires_email_confirmation=true` 를 반환할 수 있다.

응답 예시:
```json
{
  "user": {
    "id": "user_uuid",
    "email": "user@example.com",
    "email_confirmed": false
  },
  "access_token": null,
  "refresh_token": null,
  "requires_email_confirmation": true,
  "message": "가입이 완료되었습니다. 이메일 확인이 켜져 있으면 메일을 확인한 뒤 로그인해 주세요."
}
```

## POST /auth/sign-in
설명:
- 이메일 로그인

응답 예시:
```json
{
  "user": {
    "id": "user_uuid",
    "email": "user@example.com",
    "email_confirmed": true
  },
  "access_token": "supabase_access_token",
  "refresh_token": "supabase_refresh_token",
  "token_type": "bearer",
  "expires_at": 1710000000,
  "expires_in": 3600,
  "requires_email_confirmation": false,
  "message": null
}
```

## POST /auth/refresh
설명:
- refresh token 으로 새 세션 발급

## GET /auth/me
설명:
- 현재 Bearer token 기준 사용자 확인

## GET /sessions
설명:
- 현재 사용자 세션 목록 조회

## POST /sessions
설명:
- 새 인터뷰 세션 생성

요청 예시:
```json
{
  "title": "어린 시절 인터뷰",
  "theme": "childhood"
}
```

## GET /messages/{session_id}
설명:
- 세션의 메시지 목록 조회
- `safety_mode` 포함

## POST /voice/turn
설명:
- multipart 업로드
- 서버가 `STT -> safety 검사 -> user message 저장 -> memory 자동 추출 -> assistant 질문 생성 -> TTS 생성` 을 수행
- 고위험 발화는 일반 질문 대신 안전 안내 응답으로 전환

응답 예시:
```json
{
  "user_message": {
    "id": "msg_user_uuid",
    "role": "user",
    "content": "초등학교 때 여름마다 할머니 댁에 갔어요.",
    "safety_mode": false,
    "created_at": "2026-03-25T09:00:00Z"
  },
  "assistant_message": {
    "id": "msg_assistant_uuid",
    "role": "assistant",
    "content": "말씀 감사합니다. 그때 가장 먼저 떠오르는 장소가 있나요?",
    "safety_mode": false,
    "created_at": "2026-03-25T09:00:01Z"
  },
  "transcript": "초등학교 때 여름마다 할머니 댁에 갔어요.",
  "audio_reply_url": "/generated-audio/session_uuid/msg_assistant_uuid.mp3",
  "memory_items_created": 1,
  "safety_mode": false
}
```

## POST /voice/repeat-last
설명:
- 마지막 assistant 발화를 다시 음성으로 재생할 수 있게 TTS URL 반환

## POST /memory/extract
설명:
- 특정 메시지 또는 텍스트에서 memory item 추출
- 현재는 디버그/재처리 용도

## GET /chapters
설명:
- 현재 사용자 chapter draft 목록 조회
- `session_id` query 로 세션별 필터 가능

## POST /chapters/generate
설명:
- 세션 또는 memory item 묶음에서 장 초안 생성

## GET /chapters/{chapter_id}
설명:
- 단일 chapter draft 조회

## PATCH /chapters/{chapter_id}
설명:
- `instruction` 기반 수정 또는 `regenerate=true` 재생성
- 기존 row 를 갱신하면서 `version_no` 를 증가시킨다

## POST /book/compile
설명:
- `chapter_ids` 순서를 따라 최종 자서전 버전을 저장
- 또는 `session_id` 기준으로 세션 전체 chapter draft 를 묶을 수 있다

요청 예시:
```json
{
  "title": "나의 이야기",
  "chapter_ids": ["chapter_uuid_1", "chapter_uuid_2"]
}
```

응답 예시:
```json
{
  "book_id": "book_uuid",
  "title": "나의 이야기",
  "content": "1. 어린 시절\n\n...\n\n2. 청년기\n\n...",
  "chapter_ids": ["chapter_uuid_1", "chapter_uuid_2"],
  "created_at": "2026-03-25T10:20:00Z"
}
```

## GET /book/versions
설명:
- 저장된 최종 자서전 버전 목록 조회

## GET /book/versions/{book_id}
설명:
- 저장된 최종 자서전 버전 단건 조회
