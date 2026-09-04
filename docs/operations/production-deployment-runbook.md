---
document_type: operations-runbook
status: draft
scope: production-infrastructure
---

# [Operations Runbook] 첫 운영 배포와 복구

## 구성과 의도

```text
https://app.<domain> -> CloudFront -> OAC -> private web S3
https://api.<domain> -> Elastic IP -> host Nginx TLS -> 127.0.0.1:8080 -> Spring container
Spring container -> internal Docker network -> MySQL / Redis
host backup timer -> MySQL logical dump -> private backup S3
```

운영 Compose에는 Spring, MySQL, Redis만 둔다. Nginx와 Certbot은 Ubuntu host package로 운영해 host port와 certificate renewal을 한 곳에서 관리한다. MySQL과 Redis는 host port를 publish하지 않고 Spring만 `127.0.0.1:8080`에 publish한다.

웹 Dockerfile은 만들지 않는다. Vite 결과는 실행 server가 필요 없는 정적 `web/dist`이며, 검증된 Node/pnpm 환경에서 build한 뒤 private S3에 올리는 것이 선택한 CloudFront topology와 일치한다. 웹 container를 EC2에 추가하면 정적 웹 장애와 API·DB 장애가 다시 결합하고 2 GiB memory를 소비한다.

Terraform은 첫 수동 배포에서 제외한다. 대신 [AWS Console 체크리스트](aws-console-production-checklist.md)와 resource inventory를 완료한다.

## 외부 적용 전 입력

- Route 53에서 새로 구매할 등록 가능 domain과 `app`, `api` hostname
- AWS account, 운영 principal, 서울 AZ
- web bucket, backup bucket과 distribution ID
- ECR 또는 GHCR repository와 검증된 server image digest
- SMTP, OpenAI, 선택 시 Notion credential
- backup 14일 제안, 목표 RPO 최대 24시간과 수동 RTO 승인
- 실제 AWS 생성, DNS 변경, production secret 주입과 배포에 대한 명시적 승인

## server image

이미지는 repository root가 아니라 `server/`를 context로 build한다.

```bash
docker build --pull -t <registry>/openmd-server:<git-sha> server
docker push <registry>/openmd-server:<git-sha>
docker inspect --format '{{index .RepoDigests 0}}' <registry>/openmd-server:<git-sha>
```

운영 `SERVER_IMAGE`에는 마지막 명령이 반환한 `@sha256:` digest만 기록한다. EC2에서 Gradle build를 실행하지 않는다. Dockerfile의 base image tag는 첫 release 전에 승인한 digest build argument로 고정한다.

## Ubuntu host와 Nginx

1. AWS Console 체크리스트대로 host를 만들고 Session Manager로 접속한다.
2. Docker Engine·Compose plugin, Nginx, Certbot, `python3-certbot-nginx`, AWS CLI를 설치한다.
3. repository와 root-only env 파일을 배치한다.
4. bootstrap config를 설치해 ACME path만 열고 나머지는 503으로 막는다.

```bash
sudo mkdir -p /var/www/certbot
sudo ./scripts/production/render-nginx-config.sh \
  --env-file /opt/nalq/production.env \
  --mode bootstrap \
  --output /etc/nginx/sites-available/nalq-api \
  --apply
sudo ln -s /etc/nginx/sites-available/nalq-api /etc/nginx/sites-enabled/nalq-api
sudo nginx -t
sudo systemctl reload nginx
```

5. `api` DNS가 Elastic IP를 반환한 뒤 certificate를 발급한다.

```bash
sudo certbot certonly --webroot -w /var/www/certbot -d api.<domain>
sudo ./scripts/production/render-nginx-config.sh \
  --env-file /opt/nalq/production.env \
  --mode tls \
  --output /etc/nginx/sites-available/nalq-api \
  --apply
sudo nginx -t
sudo systemctl reload nginx
sudo certbot renew --dry-run
```

[`reload-nginx.sh`](../../infra/production/certbot/reload-nginx.sh)를 `/etc/letsencrypt/renewal-hooks/deploy/reload-nginx`에 mode `755`로 설치한다. Ubuntu의 Certbot timer를 확인하고 갱신 성공 뒤 이 hook이 Nginx config 검사와 reload를 수행하는지 검증한다. repository template은 query string, Cookie, Authorization와 body를 access log에서 제외한다. Notion callback의 `code`와 `state`를 로그에 남기지 않는다.

## health 계약

기존 SecurityFilterChain은 인증 API와 callback 외 모든 endpoint를 보호한다. Actuator health를 무인 container check에 공개하려면 인증 matcher를 변경해야 하므로 이번 금지 범위에서는 추가하지 않는다.

대신 다음 세 신호를 결합한다.

- server: loopback의 기존 auth route에 지원하지 않는 GET을 보내 예상한 `405 Method Not Allowed`를 반환함
- MySQL: root ping healthcheck
- Redis: `PING` healthcheck

`404`를 포함한 임의의 4xx는 정상으로 인정하지 않는다. 이 검사는 Spring이 DB query와 Redis command를 끝까지 수행할 수 있다는 단일 통합 readiness는 아니다. 배포는 세 container가 모두 healthy인 것과 Nginx HTTPS smoke를 함께 확인한다. 추후 인증 정책 소유자의 승인을 받으면 별도 management port의 Actuator readiness를 추가한다.

## 서버 배포

모든 production script는 기본 dry-run이다.

```bash
./scripts/production/deploy-server.sh \
  --env-file /opt/nalq/production.env \
  --image <registry>/openmd-server@sha256:<digest>
```

실제 적용은 외부 승인 후에만 다음처럼 수행한다. 빈 첫 배포만 `--skip-backup`을 허용한다.

```bash
sudo ./scripts/production/deploy-server.sh \
  --env-file /opt/nalq/production.env \
  --image <registry>/openmd-server@sha256:<digest> \
  --apply --confirm DEPLOY_SERVER \
  --skip-backup --confirm-empty-db FIRST_EMPTY_DATABASE
```

스크립트는 environment 계약을 검증하고, MySQL·Redis를 기다린 뒤 기존 DB가 있으면 backup하고, digest image를 pull·교체한 다음 HTTPS를 확인한다. Flyway는 기존 시작 동작을 유지하고 JPA `validate`를 바꾸지 않는다.

## 서버 rollback

rollback 전에 현재 schema와 이전 image의 forward compatibility를 사람이 확인한다. Flyway down migration은 실행하지 않는다.

```bash
sudo ./scripts/production/rollback-server.sh \
  --env-file /opt/nalq/production.env \
  --apply --confirm ROLLBACK_SERVER
```

스크립트는 rollback 직전에도 DB backup을 만들고 이전 digest로 교체한다. schema 비호환이면 application rollback을 중단하고 새 instance와 backup restore 절차를 사용한다.

## 웹 배포와 rollback

웹 배포는 clean Git commit에서만 실행한다. artifact를 `releases/<commit>/`에 보존하고 root asset을 올린 뒤 `index.html`을 마지막에 전환한다.

```bash
./scripts/production/deploy-web.sh --env-file /opt/nalq/production.env

./scripts/production/deploy-web.sh \
  --env-file /opt/nalq/production.env \
  --apply --confirm DEPLOY_WEB
```

rollback 대상은 보존된 전체 commit SHA다.

```bash
./scripts/production/rollback-web.sh \
  --env-file /opt/nalq/production.env \
  --release <40-character-commit-sha>

./scripts/production/rollback-web.sh \
  --env-file /opt/nalq/production.env \
  --release <40-character-commit-sha> \
  --apply --confirm ROLLBACK_WEB
```

S3 release retention은 DB backup 보유정책과 별개다. 개인정보나 secret을 web artifact에 포함하지 않는다.

## backup과 restore

수동 dry-run과 실제 실행 형식은 다음과 같다.

```bash
sudo ./scripts/production/backup-mysql.sh --env-file /opt/nalq/production.env
sudo ./scripts/production/backup-mysql.sh --env-file /opt/nalq/production.env --apply
```

backup은 single-transaction dump를 gzip으로 압축하고 checksum과 함께 SSE-S3 object로 올린 뒤 local 임시본을 삭제한다. systemd timer는 매일 03:20 KST에 실행한다.

restore는 설정한 backup prefix의 `.sql.gz`만 허용하며 비어 있지 않은 DB를 거부한다.

```bash
sudo ./scripts/production/restore-mysql.sh \
  --env-file /opt/nalq/production.env \
  --source s3://<backup-bucket>/mysql/<object>.sql.gz

sudo ./scripts/production/restore-mysql.sh \
  --env-file /opt/nalq/production.env \
  --source s3://<backup-bucket>/mysql/<object>.sql.gz \
  --apply --confirm RESTORE_EMPTY_DATABASE
```

복원 훈련은 새 MySQL volume에서 수행하고 server 시작, Flyway 적용, JPA validation, 보호 데이터 접근 차단과 주요 smoke를 확인한다.

## 검증과 운영 기준

저장소에서 실행한다.

```bash
./scripts/production/validate-config.sh
docker build -t nalq-server:local server
SERVER_IMAGE=nalq-server:local docker compose \
  --env-file infra/production/.env.example \
  -f infra/production/compose.yml config --quiet
./scripts/verify.sh fast
```

실제 production-like 검증에는 유효한 secret을 임시 안전 파일로 주입해 `docker compose up --wait`를 수행하고 종료 시 volume까지 정리한다. 운영 데이터 volume에는 이 검증을 실행하지 않는다.

외부 적용 후에는 S3 직접 접근 거절, CloudFront route, HTTP→HTTPS, certificate hostname, 외부 `8080/3306/6379` 차단, backup/restore를 확인한다. 실제 AWS와 DNS 검증은 승인 전까지 `BLOCKED`로 분류한다.
