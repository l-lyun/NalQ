---
document_type: operations-contract
status: draft
scope: production-infrastructure
---

# [Operations Contract] 운영 환경 변수 원장

실제 값은 저장소에 기록하지 않는다. 서버 원장은 EC2의 `/opt/nalq/production.env`에 `root:root`, mode `600`으로 두고 Session Manager로만 갱신한다. 서버 예시는 [`infra/production/.env.example`](../../infra/production/.env.example)이다.

웹 배포는 별도 principal과 [`infra/production/web.env.example`](../../infra/production/web.env.example)을 사용한다. 적용 파일의 기본 경로는 `/opt/nalq/web-deploy.env`지만 EC2에 둘 필요는 없으며, CI나 배포 workstation에서 소유자만 읽게 한다. 이 파일에는 공개 Vite 값과 web bucket/distribution 식별자만 두고 AWS credential이나 서버·DB·인증·메일·Notion 비밀을 넣지 않는다.

## 공개 배포 값

| 이름 | 필수 | 예시 의미 |
| --- | --- | --- |
| `AWS_REGION` | 예 | API·S3·backup 리전. 첫 배포는 `ap-northeast-2` |
| `WEB_DOMAIN` | 예 | protocol 없는 `app.<domain>` |
| `API_DOMAIN` | 예 | protocol 없는 `api.<domain>` |
| `WEB_ORIGIN` | 예 | `https://`를 포함한 정확한 웹 origin |
| `DB_BACKUP_S3_URI` | 예 | 별도 private backup bucket과 prefix |
| `BACKUP_RETENTION_DAYS` | 예 | bucket lifecycle과 맞출 보유일. 초기 제안 14일 |
| `SERVER_IMAGE` | 예 | `repository@sha256:<64 hex>` 형태의 불변 image |
| `SERVER_HOST_PORT` | 예 | Nginx가 접근할 loopback port. 이 최소 구성에서는 `8080` 고정이며 다른 값은 거부 |

`VITE_*`와 `EXPO_PUBLIC_*` 값은 클라이언트 bundle에 포함되는 공개 설정이다. 비밀을 넣지 않는다. Expo production build의 `EXPO_PUBLIC_WEB_URL`은 별도 EAS 환경에서 `https://app.<domain>`으로 설정한다.

## 웹 배포 전용 값

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `AWS_REGION` | 예 | web S3 region, `ap-northeast-2` |
| `WEB_DOMAIN` | 예 | `app.<domain>` |
| `API_DOMAIN` | 예 | `api.<domain>` |
| `VITE_API_BASE_URL` | 예 | bundle에 포함되는 공개 API URL, 정확히 `https://API_DOMAIN` |
| `WEB_S3_BUCKET` | 예 | private web origin bucket 이름 |
| `CLOUDFRONT_DISTRIBUTION_ID` | 예 | invalidation 대상 distribution |

웹 build subprocess는 기존 shell environment를 상속하지 않고 현재 Node 실행 파일을 우선하는 `PATH`, 임시 디렉터리와 `VITE_API_BASE_URL`, 고정 runtime mode, release version만 전달받는다. 사용자 home, npm 설정이나 AWS credential은 build에 전달하지 않는다. S3/CloudFront 작업은 build가 끝난 뒤 웹 배포 principal로 실행한다.

## 데이터 저장소

| 이름 | 비밀 | 설명 |
| --- | --- | --- |
| `MYSQL_DATABASE` | 아니요 | 애플리케이션 DB 이름 |
| `MYSQL_USER` | 아니요 | 애플리케이션 DB 사용자 |
| `MYSQL_PASSWORD` | 예 | 애플리케이션 DB 비밀번호 |
| `MYSQL_ROOT_PASSWORD` | 예 | 초기화·backup·restore 전용 root 비밀번호 |

MySQL과 Redis host는 운영 Compose가 각각 `mysql`, `redis`로 고정한다. Redis는 외부 port가 없고 internal Docker network에만 연결된다. 첫 구성은 Redis 비밀번호 대신 이 network 경계를 사용한다.

## 서버 보안·origin

| 이름 | 비밀 | 운영 기준 |
| --- | --- | --- |
| `OPENMD_CORS_ALLOWED_ORIGINS` | 아니요 | `WEB_ORIGIN`과 정확히 같음 |
| `OPENMD_AUTH_BROWSER_ALLOWED_ORIGINS` | 아니요 | `WEB_ORIGIN`과 정확히 같음 |
| `OPENMD_AUTH_BROWSER_COOKIE_NAME` | 아니요 | `__Host-openmd_refresh` |
| `OPENMD_AUTH_REFRESH_TOKEN_LIFETIME` | 아니요 | 기존 계약 기본값 `30d` |
| `OPENMD_AUTH_ACCESS_TOKEN_SECRET` | 예 | 독립 생성한 32 bytes 이상 Base64 |
| `OPENMD_AUTH_EMAIL_CODE_HMAC_SECRET` | 예 | 위 값과 다른 32 bytes 이상 Base64 |

Cookie의 Secure, SameSite, Path와 OpenAPI/Swagger 비활성화는 운영 Compose에서 안전값으로 고정한다. 이 원장은 인증 동작을 변경하지 않는다.

## SMTP

| 이름 | 비밀 | 설명 |
| --- | --- | --- |
| `OPENMD_MAIL_FROM` | 아니요 | 검증된 발신 주소 |
| `SPRING_MAIL_HOST` | 아니요 | SMTP endpoint |
| `SPRING_MAIL_PORT` | 아니요 | 일반적으로 `587` |
| `SPRING_MAIL_USERNAME` | 예 | SMTP 사용자 |
| `SPRING_MAIL_PASSWORD` | 예 | SMTP 비밀번호 |
| `SPRING_MAIL_SMTP_AUTH` | 아니요 | 운영 기본 `true` |
| `SPRING_MAIL_STARTTLS_ENABLE` | 아니요 | 운영 기본 `true` |

## Notion

`OPENMD_NOTION_ENABLED=false`이면 credential은 비워 둔다. 활성화 전에는 `OPENMD_NOTION_CLIENT_ID`, `OPENMD_NOTION_CLIENT_SECRET`, `OPENMD_NOTION_CALLBACK_URI`, `OPENMD_NOTION_ALLOWED_RETURN_URIS`, `OPENMD_NOTION_FAILURE_RETURN_URI`, `OPENMD_NOTION_TOKEN_KEYS`, `OPENMD_NOTION_WRITE_KEY_VERSION`을 현재 Notion 계약과 맞춰 별도 승인한다. callback, 허용 return URI와 failure return URI는 절대 `https` URI여야 하고 failure URI는 허용 목록의 정확한 원소여야 한다.

## OpenAI와 생성 worker

| 이름 | 비밀 | 초기값 |
| --- | --- | --- |
| `OPENAI_API_KEY` | 예 | 비활성 상태는 `no-key-configured` |
| `OPENMD_QUIZ_GENERATION_ENABLED` | 아니요 | key 승인 전 `false` |
| `OPENMD_QUIZ_GENERATION_MODEL` | 아니요 | 실제 계정 접근 가능 model 확인 |
| `OPENMD_QUIZ_GENERATION_REASONING_EFFORT` | 아니요 | `low` 후보 |
| `OPENMD_QUIZ_GENERATION_TIMEOUT` | 아니요 | `60s` |
| `OPENMD_QUIZ_GENERATION_NETWORK_RETRY` | 아니요 | `1` |
| `OPENMD_QUIZ_GENERATION_WORKER_COUNT` | 아니요 | t3.small에서는 `1`부터 시작 |
| `OPENMD_QUIZ_GENERATION_QUEUE_CAPACITY` | 아니요 | t3.small에서는 `4`부터 시작 |

## 변경 절차

1. Session Manager로 접속한다.
2. 새 파일을 별도 경로에 mode `600`으로 작성한다. 값을 터미널 history나 로그에 출력하지 않는다.
3. 서버 원장은 `scripts/production/validate-env.sh`, 웹 원장은 `scripts/production/validate-web-env.sh`로 각각 검증한다.
4. 기존 파일을 권한이 제한된 backup으로 보관하고 원자적으로 교체한다.
5. 영향받는 container만 재배포하고 HTTPS smoke를 수행한다.
6. credential 변경이면 이전 credential을 provider에서 폐기한다.

저장소의 두 example 파일은 이름과 형식의 계약일 뿐 secret 저장소가 아니다. 웹 배포 principal에는 server env, backup bucket과 EC2 접근 권한을 주지 않고 web bucket object/release write와 지정 CloudFront invalidation만 허용한다.
