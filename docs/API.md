# API.md

## Base URL
- local: http://localhost:8000
- production: TBD

## Auth strategy
- 앱은 Supabase Auth 로 로그인한다.
- 서버는 인증된 사용자 기준으로 요청을 처리한다.
- OpenAI API key 는 서버 환경변수에만 저장한다.
- 현재 `/sessions`, `/messages`, `/voice/turn`, `/voice/repeat-last`, `/memory/extract`, `/chapters/generate` 구현은 인증 미들웨어 대신 임시 `X-User-Id` header 로 사용자 문맥을 받는다.

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
- 각 메시지에는 `safety_mode` 가 포함된다.

Response example:
[
  {
    "id": "message_1",
    "role": "assistant",
    "content": "안녕하세요. 오늘은 편하게 옛날 이야기를 나눠보겠습니다.",
    "safety_mode": false,
    "created_at": "2026-03-24T12:01:00Z"
  },
  {
    "id": "message_2",
    "role": "user",
    "content": "어릴 때는 시골에서 살았어요.",
    "safety_mode": false,
    "created_at": "2026-03-24T12:02:00Z"
  }
]

---

## POST /voice/turn
설명:
- 음성 입력 한 턴을 처리
- 서버가 STT -> safety 검사 -> 사용자 메시지 저장 -> assistant 응답 생성 또는 안전 안내 -> assistant 저장 -> TTS 생성까지 수행
- 현재 구현은 `X-User-Id` header 가 필요하다.
- 현재 STT 는 OpenAI speech-to-text service 를 호출한다.
- safety check 는 OpenAI Structured Outputs 기반 판단을 우선 사용하고, 실패 시 키워드 fallback 을 사용한다.
- 현재 assistant 텍스트 응답 생성은 mock service 기반이다.
- 현재 TTS 는 OpenAI text-to-speech service 를 호출하고 mp3 파일을 서버 로컬에 저장한 뒤 `/generated-audio/...` 경로를 반환한다.
- 고위험 발화가 감지되면 일반 인터뷰 질문 대신 짧은 안전 안내 응답을 반환한다.
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
  "memory_items_created": 0,
  "safety_mode": false
}

---

## POST /voice/repeat-last
설명:
- 마지막 assistant 발화를 다시 음성으로 재생할 수 있게 TTS 결과를 반환
- 현재 구현은 `X-User-Id` header 가 필요하다.
- 같은 assistant message 에 대해 기존 mp3 파일이 있으면 재사용하고, 없으면 다시 TTS 를 생성한다.
- 마지막 assistant 텍스트와 재생 가능한 오디오 경로를 함께 반환한다.

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
- 현재 구현은 `X-User-Id` header 가 필요하다.
- 요청 본문은 `message_id` 또는 `text` 중 하나만 포함해야 한다.
- 현재 extraction 은 OpenAI Structured Outputs 기반 Pydantic schema 파싱을 사용한다.
- OpenAI 호출 또는 구조화 파싱이 실패하면 `raw_text` 만 보존한 fallback memory item 1건을 저장한다.

Request example:
{
  "session_id": "session_uuid",
  "message_id": "message_uuid"
}

또는
{
  "session_id": "session_uuid",
  "text": "초등학교 때 여름마다 할머니 댁에 갔어요."
}

Response example:
{
  "items": [
    {
      "id": "memory_uuid",
      "session_id": "session_uuid",
      "message_id": "message_uuid",
      "period": "초등학교 시절",
      "place": "할머니 댁",
      "person": "할머니",
      "event": "여름방학 방문",
      "emotions": ["행복", "그리움"],
      "meaning": "어린 시절 가장 따뜻했던 기억",
      "raw_text": "초등학교 때 여름마다 할머니 댁에 갔어요.",
      "created_at": "2026-03-25T09:10:00Z"
    }
  ],
  "fallback_used": false
}

---

## POST /chapters/generate
설명:
- 특정 세션 또는 memory item 묶음을 바탕으로 chapter draft 생성
- 현재 구현은 `X-User-Id` header 가 필요하다.
- 요청 본문은 `session_id` 또는 `memory_item_ids` 중 하나만 포함해야 한다.
- 현재 generation 은 OpenAI Structured Outputs 기반 Pydantic schema 파싱을 사용한다.
- chapter title 1개와 자연스러운 회고형 본문을 생성한 뒤 `chapter_drafts` 에 저장한다.

Request example:
{
  "chapter_type": "childhood",
  "session_id": "session_uuid"
}

또는
{
  "chapter_type": "childhood",
  "memory_item_ids": ["memory_uuid_1", "memory_uuid_2"]
}

Response example:
{
  "chapter_id": "chapter_uuid",
  "session_id": "session_uuid",
  "chapter_type": "childhood",
  "title": "어린 시절",
  "content": "초등학교 시절의 여름은 할머니 댁의 풍경과 함께 가장 먼저 떠오른다...\n\n그 시절의 기억은 과장되지 않은 따뜻함으로 남아 있다...",
  "version_no": 1,
  "created_at": "2026-03-25T10:20:00Z"
}

---

## PATCH /chapters/{chapter_id}
설명:
- 장 초안 수정 또는 재생성 요청
- 현재 구현은 `X-User-Id` header 가 필요하다.
- 요청은 두 모드 중 하나만 사용한다.
- `instruction` 이 있으면 기존 chapter draft 를 기준으로 수정한다.
- `regenerate=true` 이면 기존 chapter 의 `session_id` 와 `chapter_type` 를 기준으로 다시 생성한다.
- 현재 재생성은 `session_id` 가 저장된 chapter draft 에서만 지원한다.
- 현재 PATCH 는 새 row 를 만들지 않고 기존 `chapter_drafts` row 를 갱신하며 `version_no` 를 1 증가시킨다.

Request example:
{
  "instruction": "조금 더 담백하고 차분한 문체로 고쳐줘"
}

또는
{
  "regenerate": true
}

Response example:
{
  "chapter_id": "chapter_uuid",
  "session_id": "session_uuid",
  "chapter_type": "childhood",
  "title": "어린 시절",
  "content": "수정되거나 재생성된 본문...",
  "version_no": 2,
  "created_at": "2026-03-25T10:20:00Z"
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
- 현재 구현은 OpenAI Structured Outputs 기반 판단을 우선 사용하고, 실패 시 키워드 fallback 을 사용한다.

Request example:
{
  "text": "숨이 너무 차고 가슴이 아파요."
}

Response example:
{
  "safety_mode": true,
  "severity": "high",
  "reason": "응급 또는 고위험 신호가 감지되었습니다.",
  "recommended_action": "응급 도움 요청 안내"
}
