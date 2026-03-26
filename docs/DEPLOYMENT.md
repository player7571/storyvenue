# DEPLOYMENT.md

## Goal
다른 사람이 APK 만 받아도 앱을 사용할 수 있게 하려면:
- 공개 인터넷에서 접근 가능한 FastAPI 서버가 있어야 한다.
- 앱 기본 서버 주소가 그 공개 URL 을 가리켜야 한다.

## Current deployment shape
- backend: Docker single container
- database/auth: Supabase
- AI: OpenAI API
- generated TTS audio: Docker volume 에 저장

## 1. Server prerequisites
- 공개 서버 1대 준비
  - 예: Ubuntu VPS, EC2, Lightsail, GCP VM
- 서버에 Docker 와 Docker Compose plugin 설치
- `server/.env` 에 실제 운영 값 입력
- Supabase SQL 적용
  - [server/sql/mvp_schema.sql](/Users/player7571/storyvenue/server/sql/mvp_schema.sql)
  - [server/sql/social_feed_schema.sql](/Users/player7571/storyvenue/server/sql/social_feed_schema.sql)

운영 권장값:
- `APP_ENV=production`
- `ALLOW_DEV_USER_HEADER=false`

## 2. Run backend with Docker
프로젝트 루트에서 실행:

```bash
docker compose up -d --build
```

확인:

```bash
curl http://SERVER_IP:8000/health
```

정상 응답:

```json
{"status":"ok"}
```

## 3. Make the server public
최소 조건:
- 서버 8000 포트를 외부에서 접근 가능하게 열기

권장 조건:
- `https://api.your-domain.com` 같은 도메인 사용
- Nginx, Caddy, Cloudflare Tunnel, 또는 클라우드 로드밸런서로 HTTPS 적용

주의:
- 현재 앱은 서버 주소를 직접 입력할 수도 있지만, APK 만 배포하려면 기본 서버 주소를 운영 도메인으로 넣어 빌드하는 편이 맞다.

## 4. Build APK with public server URL
앱 기본 URL 은 Gradle property `storyvenueBaseUrl` 로 주입할 수 있다.

release APK 예시:

```bash
cd app
./gradlew :app:assembleRelease -PstoryvenueBaseUrl=https://api.your-domain.com
```

debug APK 예시:

```bash
cd app
./gradlew :app:assembleDebug -PstoryvenueBaseUrl=https://api.your-domain.com
```

기본값을 따로 주지 않으면 로컬 테스트용 `http://10.0.2.2:8000` 이 사용된다.

## 5. What users need
최종 사용자는 아래만 있으면 된다.
- APK 설치
- 인터넷 연결

운영 URL 이 APK 기본값으로 들어 있으면 서버 주소를 따로 입력할 필요가 없다.

## 6. Operational notes
- 현재 TTS 파일은 Docker volume 에 저장된다.
- 단일 서버 운영에는 충분하다.
- 서버를 여러 대로 늘리거나 Kubernetes 로 옮길 때는 S3 또는 Supabase Storage 같은 외부 오브젝트 스토리지로 옮기는 편이 안전하다.

## 7. Recommended first production path
1. VPS 1대 준비
2. Docker Compose 로 서버 실행
3. 도메인 + HTTPS 연결
4. `storyvenueBaseUrl=https://api.your-domain.com` 로 release APK 빌드
5. APK 배포 후 실제 회원가입, 음성 인터뷰, 피드, 채팅 확인
