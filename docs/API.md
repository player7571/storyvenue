# API.md

## Base URL
- local: http://localhost:8000
- production: TBD

## Auth strategy
- 앱은 Supabase Auth 로 로그인한다.
- 서버는 인증된 사용자 기준으로 요청을 처리한다.
- OpenAI API key 는 서버 환경변수에만 저장한다.

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
  "created_at": "2026-03-24T12:00:00Z"
}

---

## GET /sessions/{session_id}
설명:
- 특정 인터뷰 세션 조회

Response example:
{
  "id": "session_uuid",
  "title": "어린 시절 인터뷰",
  "theme": "childhood"
}

---

## POST /messages
설명:
- 사용자/챗봇 메시지 저장

Request example:
{
  "session_id": "session_uuid",
  "role": "user",
  "content": "초등학교 때 여름마다 할머니 댁에 갔어요."
}

Response example:
{
  "id": "message_uuid",
  "session_id": "session_uuid",
  "role": "user",
  "content": "초등학교 때 여름마다 할머니 댁에 갔어요.",
  "created_at": "2026-03-24T12:03:00Z"
}

---

## GET /messages/{session_id}
설명:
- 특정 세션의 메시지 목록 조회

Response example:
[
  {
    "id": "message_1",
    "role": "assistant",
    "content": "어린 시절 가장 기억에 남는 장소는 어디인가요?"
  },
  {
    "id": "message_2",
    "role": "user",
    "content": "초등학교 때 여름마다 할머니 댁에 갔어요."
  }
]

---

## POST /memory/extract
설명:
- 특정 메시지 또는 텍스트에서 memory item 추출

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
      "emotion": ["행복", "그리움"],
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