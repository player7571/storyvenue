# API.md

## Base URL
- local: http://localhost:8000
- production: TBD

## Auth strategy
- 앱은 Supabase Auth 로 로그인한다.
- 서버는 인증된 사용자 기준으로 요청을 처리한다.
- OpenAI API key 는 서버 환경변수에만 저장한다.
- 현재 `/sessions`, `/messages`, `/voice/turn` 구현은 인증 미들웨어 대신 임시 `X-User-Id` header 로 사용자 문맥을 받는다.

---

## GET /health
설명:
- 서버 상태 확인

Response example:
{
  "status": "ok"
}

---

## POST /sessions
설명:
- 새 인터뷰 세션 생성
- 현재 구현은 `X-User-Id` header 가 필요하다.

Request example:
{
  "title": "어린 시절 인터뷰",
  "theme": "childhood"
}

Response example:
{
  "id": "session_uuid",
  "title": "어린 시절 인터뷰",
  "theme": "childhood",
  "status": "active",
  "created_at": "2026-03-24T12:00:00Z"
}

---

## GET /sessions/{session_id}
설명:
- 특정 인터뷰 세션 조회
- 현재 구현은 `X-User-Id` header 가 필요하다.

Response example:
{
  "id": "session_uuid",
  "title": "어린 시절 인터뷰",
  "theme": "childhood",
  "status": "active",
  "created_at": "2026-03-24T12:00:00Z"
}

---

## GET /messages/{session_id}
설명:
- 특정 세션의 메시지 목록 조회
- 사용자와 assistant 발화를 시간순으로 반환
- 현재 구현은 `X-User-Id` header 가 필요하다.

Response example:
[
  {
    "id": "message_1",
    "role": "assistant",
    "content": "안녕하세요. 오늘은 편하게 옛날 이야기를 나눠보겠습니다.",
    "created_at": "2026-03-24T12:01:00Z"
  },
  {
    "id": "message_2",
    "role": "user",
    "content": "어릴 때는 시골에서 살았어요.",
    "created_at": "2026-03-24T12:02:00Z"
  }
]

---

## POST /voice/turn
설명:
- 음성 입력 한 턴을 처리
- 서버가 STT -> 사용자 메시지 저장 -> assistant 응답 생성 -> assistant 저장 -> TTS 생성까지 수행
- 현재 구현은 `X-User-Id` header 가 필요하다.
- 현재 STT 는 OpenAI speech-to-text service 를 호출한다.
- 현재 assistant 텍스트 응답 생성은 mock service 기반이다.
- 현재 TTS 는 OpenAI text-to-speech service 를 호출하고 mp3 파일을 서버 로컬에 저장한 뒤 `/generated-audio/...` 경로를 반환한다.
- 현재 `memory_items_created` 는 항상 `0` 이다.

Request:
- multipart/form-data
- fields:
  - session_id: string
  - audio_file: binary
  - mime_type: string (optional)
  - language_hint: string (optional, 예: ko)

Response example:
{
  "user_message": {
    "id": "msg_user_uuid",
    "role": "user",
    "content": "초등학교 때 여름마다 할머니 댁에 갔어요.",
    "created_at": "2026-03-25T09:00:00Z"
  },
  "assistant_message": {
    "id": "msg_assistant_uuid",
    "role": "assistant",
    "content": "말씀 감사합니다. 그때 가장 먼저 떠오르는 장소가 있나요?",
    "created_at": "2026-03-25T09:00:01Z"
  },
  "transcript": "초등학교 때 여름마다 할머니 댁에 갔어요.",
  "audio_reply_url": "/generated-audio/session_uuid/msg_assistant_uuid.mp3",
  "memory_items_created": 0,
  "safety_mode": false
}

---

## POST /voice/repeat-last
설명:
- 마지막 assistant 발화를 다시 음성으로 재생할 수 있게 TTS 결과를 반환

Request example:
{
  "session_id": "session_uuid"
}

Response example:
{
  "assistant_message_id": "msg_assistant_uuid",
  "content": "그때 가장 먼저 떠오르는 장소가 있나요?",
  "audio_reply_url": "/generated-audio/session_uuid/msg_assistant_uuid.mp3"
}

---

## POST /memory/extract
설명:
- 특정 메시지 또는 텍스트에서 memory item 추출
- 디버그 또는 재처리 용도

Request example:
{
  "session_id": "session_uuid",
  "message_id": "message_uuid"
}

Response example:
{
  "items": [
    {
      "period": "초등학교 시절",
      "place": "할머니 댁",
      "person": "할머니",
      "emotions": ["행복", "그리움"],
      "event": "여름방학 방문",
      "meaning": "어린 시절 가장 따뜻했던 기억"
    }
  ]
}

---

## POST /chapters/generate
설명:
- memory item 묶음을 바탕으로 chapter draft 생성

Request example:
{
  "session_id": "session_uuid",
  "chapter_type": "childhood"
}

Response example:
{
  "chapter_id": "chapter_uuid",
  "title": "어린 시절",
  "content": "어린 시절의 여름은 언제나 할머니 댁과 함께 떠오른다..."
}

---

## PATCH /chapters/{chapter_id}
설명:
- 장 초안 수정 또는 재생성 요청

Request example:
{
  "instruction": "조금 더 따뜻한 문체로 다시 써줘"
}

Response example:
{
  "chapter_id": "chapter_uuid",
  "title": "어린 시절",
  "content": "수정된 본문..."
}

---

## POST /book/compile
설명:
- 장 초안을 모아 최종 자서전 버전 생성

Request example:
{
  "chapter_ids": ["chapter_1", "chapter_2"]
}

Response example:
{
  "book_id": "book_uuid",
  "title": "나의 이야기",
  "content": "전체 자서전 본문..."
}

---

## POST /safety/check
설명:
- 위험 신호 발화를 별도로 검사
- 내부적으로는 /voice/turn 내에서 자동 호출될 수 있음

Request example:
{
  "text": "숨이 너무 차고 가슴이 아파요."
}

Response example:
{
  "safety_mode": true,
  "severity": "high",
  "recommended_action": "응급 도움 요청 안내"
}
