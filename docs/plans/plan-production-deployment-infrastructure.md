---
document_type: execution-plan
status: draft
scope: repository
---

# [Execution Plan] NalQ 첫 운영 배포 인프라

- 관련 PRD: [마이페이지 계정 관리](../prd/prd-mypage-account-management.md), [퀴즈 생성·풀이](../prd/prd-quiz-learning.md)
- 관련 UX: [공개 서비스 정보와 푸터](../ux/screen-public-service-information.md)
- 관련 Contract: [인증 API](../contracts/contract-api-authentication.md), [사용자·인증 데이터](../contracts/contract-data-authentication.md)
- 관련 TRD: [서버 브라우저 Refresh Cookie](../../server/docs/trd/trd-browser-refresh-cookie.md), [웹 인증](../../web/docs/trd/trd-authentication.md), [앱 WebView 셸](../../app/docs/trd/trd-webview-shell.md)
- 관련 배포 문서: [iOS 배포 준비](../../app/docs/ios-distribution.md)

## 목표와 완료 조건

개인 운영 MVP의 고정비와 운영 복잡도를 제한하면서 React 정적 웹과 상태를 가진 서버를 분리한다. 사용자가 확정한 첫 운영 토폴로지는 다음과 같다.

- React/Vite 정적 웹은 private Amazon S3 bucket을 origin으로 하는 Amazon CloudFront에서 제공한다.
- S3 website endpoint나 객체 URL을 사용자에게 직접 공개하지 않고 CloudFront Origin Access Control(OAC)만 읽기를 허용한다.
- Spring Boot, MySQL 8.4와 Redis 7.4는 AWS 서울 리전의 단일 Amazon EC2 Linux instance에서 Docker Compose로 운영한다.
- EC2는 `t3.small`(2 vCPU, 2 GiB), storage는 암호화한 단일 `gp3` 20 GiB root volume으로 시작한다.
- 웹과 API는 서로 다른 origin이되 같은 등록 가능 도메인과 HTTPS를 사용하는 same-site topology로 둔다.
- 정적 웹 bucket과 MySQL 운영 백업 bucket은 권한·수명 주기·공개 범위가 다른 별도 bucket으로 분리한다.

완료는 저장소 설정만 존재하는 상태가 아니라 아래 항목을 운영 전 검증했을 때 판단한다.

- CloudFront URL에서는 SPA가 열리고 S3 REST·website URL의 익명 객체 조회는 거절된다.
- WebView와 일반 브라우저에서 로그인, Cookie 발급, refresh 회전과 logout이 실제 운영 origin 사이에서 동작한다.
- MySQL·Redis·Spring Boot port는 공개 인터넷에서 접근할 수 없고 API HTTPS만 Nginx를 통해 노출된다.
- 매일 암호화한 MySQL 논리 backup이 웹 bucket과 다른 private S3 bucket에 생성되고 새 MySQL에 복원하는 훈련을 통과한다.
- 배포된 법률 문서, 회원 탈퇴 처리와 백업 수명 주기가 실제 운영 설정과 일치한다.

## 범위와 비범위

### 범위

- 정적 웹 배포 자동화와 CloudFront, OAC, DNS, TLS의 AWS Console 수동 구성 체크리스트
- EC2용 서버 image, 운영 Compose, Nginx·Certbot, VPC·EBS·IAM과 비밀 주입 계약
- 운영 도메인 분리에 따른 Cookie, CORS, CSRF와 WebView 검증
- MySQL 백업·복원, 로그·상태 확인과 애플리케이션 rollback 절차
- CI에서 검증된 commit의 web artifact와 server image만 운영 후보로 만드는 경계

### 비범위

- 이 계획 작성만으로 AWS 계정, VPC, subnet, security group, EC2, EBS, Elastic IP, IAM, bucket, distribution, DNS, certificate나 비밀을 실제 생성·변경하지 않는다.
- NAT Gateway, ALB, RDS, ElastiCache, 고가용성 DB, 다중 instance와 무중단 재해 복구는 첫 MVP 범위가 아니다.
- OpenAI와 SMTP 제공자 선택 및 개인정보처리방침의 법률 적정성은 별도 운영·법률 결정이다.
- 회원 탈퇴, 비밀번호 재설정과 OpenAI 문제 생성의 애플리케이션 구현을 이 인프라 계획이 대신하지 않는다.

## 확정 토폴로지

```text
Web browser / Expo WebView
  ├─ https://app.<service-domain>  (대안: www)
  │    └─ CloudFront
  │         └─ OAC signed request
  │              └─ private S3 web bucket
  └─ https://api.<service-domain>
       └─ EC2 Elastic IP :443
            └─ API security group :80/:443
                 └─ VPC public subnet / single EC2
                      ├─ Nginx TLS reverse proxy
                      │    └─ Spring Boot :8080
                      │         ├─ OpenAI API
                      │         └─ SMTP
                      └─ encrypted gp3 20 GiB root volume
                           ├─ OS / Docker images and logs
                           ├─ MySQL :3306 / Redis :6379
                           └─ Nginx / Certbot state

EC2 backup job
  └─ encrypted MySQL logical dump
       └─ private S3 database-backup bucket (web bucket과 별도)
```

- 기준 웹 host는 `app`을 권장한다. `www`를 선택하면 그것을 유일한 기준 origin으로 정하고 다른 host는 redirect만 제공한다.
- CloudFront가 API를 경로 기반으로 proxy하지 않는다. 웹과 API는 배포·장애·cache 경계를 분리한 두 host로 운영한다.
- 두 host는 반드시 `https://app.example.com`과 `https://api.example.com`처럼 scheme과 등록 가능 도메인이 같아야 한다. 서로 다른 최상위 site를 쓰는 토폴로지는 이번 승인에 포함되지 않는다.
- CloudFront/S3가 살아 있어도 EC2가 중단되면 보호 기능은 사용할 수 없다. 웹은 API 장애를 로그인 해제나 빈 성공 상태로 오인하지 않고 복구 가능한 오류로 표시한다.

## 정적 웹: private S3와 CloudFront

### 접근 경계

- web bucket은 S3 website hosting을 켜지 않고 일반 S3 REST origin으로 연결한다.
- web bucket은 서울 리전(`ap-northeast-2`)에 두고 CloudFront edge를 통해 제공한다.
- S3 Block Public Access 네 항목을 유지하고 ACL은 사용하지 않는다.
- CloudFront OAC는 요청에 항상 서명하며, bucket policy는 해당 distribution ARN의 `cloudfront.amazonaws.com` principal에 필요한 `s3:GetObject`만 허용한다.
- 사용자의 S3 object URL 직접 요청, bucket 목록 조회와 public ACL·public bucket policy를 허용하지 않는다.
- viewer는 HTTPS로 redirect하고 운영 certificate와 기준 web host를 사용한다. CloudFront custom domain certificate는 ACM 미국 동부(버지니아 북부, `us-east-1`)에 발급하고, API certificate는 EC2의 Certbot/Let's Encrypt가 별도로 발급·갱신한다.
- 배포 주체는 web bucket의 artifact prefix에만 쓸 수 있어야 하며 backup bucket이나 서버 비밀을 읽을 수 없다.

AWS의 현재 권장 OAC 경계는 [S3 origin 접근 제한](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html)과 [S3 Block Public Access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html)를 구현 시 다시 확인한다.

### SPA와 cache

- `BrowserRouter` 직접 접근을 위해 확장자가 없는 앱 route는 `index.html`로 해석한다. 이를 CloudFront Function viewer-request rewrite 또는 동등한 명시적 규칙으로 구현하고 asset 404까지 성공 HTML로 바꾸지 않는다.
- hash가 붙은 정적 asset은 장기 immutable cache, `index.html`과 배포 manifest는 짧은 cache 또는 revalidation을 사용한다.
- 배포는 새 artifact를 먼저 올린 뒤 기준 `index.html`을 전환한다. rollback에 필요한 직전 release artifact를 현재 개인정보·비밀 보존 정책과 충돌하지 않는 짧은 기간 유지한다.
- S3와 CloudFront에는 API 응답, Refresh Cookie, Access Token, 학습자료나 사용자 답안을 저장하지 않는다.
- CloudFront access log를 사용하면 IP, User-Agent와 URL이 운영 로그가 되므로 query string을 불필요하게 전달하지 않고 보유기간·접근권한을 개인정보처리방침과 맞춘다.

## API와 데이터: 단일 EC2와 Nginx

### 최소 VPC와 공개 경계

- 서울 리전(`ap-northeast-2`)에 NalQ 전용 VPC 하나와 가용 영역 하나의 public subnet 하나를 둔다. CIDR은 충돌하지 않는 사설 대역을 선택하고, main route table을 암묵적으로 쓰지 않고 public route table을 subnet에 명시적으로 연결한다.
- Internet Gateway 하나를 VPC에 연결하고 public route table의 `0.0.0.0/0`을 해당 gateway로 보낸다. 이 route와 public IPv4가 있는 subnet이라는 조건을 첫 배포 AWS Console 체크리스트에서 함께 확인한다.
- EC2 network interface에 Elastic IP 하나를 연결하고 `api.<service-domain>` A record가 이 주소를 가리키게 한다. instance 교체 시 동일 주소를 새 instance로 재연결하는 것을 복구 절차에 포함한다.
- API security group의 인터넷 inbound는 `80/tcp`, `443/tcp`만 허용한다. Nginx의 `80` server는 Certbot HTTP-01 challenge를 제공하고 나머지 요청을 `443` HTTPS로 영구 redirect한다.
- 기본 관리 경로는 AWS Systems Manager Session Manager로 두고 `22/tcp`를 열지 않는다. 긴급 SSH가 별도로 승인된 경우에만 운영자 고정 IP `/32`를 한시적으로 허용하고 사용 후 제거한다.
- MySQL `3306`, Redis `6379`와 Spring Boot `8080`은 security group과 host port에 공개하지 않는다. Nginx만 Compose 내부 network의 Spring Boot `8080`으로 reverse proxy하며 인터넷 클라이언트는 `8080`에 직접 접근할 수 없다.
- instance는 OpenAI, SMTP, image registry, 패키지 저장소와 SSM에 필요한 outbound만 사용한다. security group은 domain 기반 제한을 제공하지 않으므로 실제 port와 외부 endpoint 목록을 운영 환경 변수 계약과 함께 관리한다.
- 첫 구성에는 private subnet과 NAT Gateway를 만들지 않는다. 외부 API 호출이 필요한 단일 EC2를 public subnet에 두되 inbound를 security group으로 제한하며, ALB·private app subnet 전환은 고가용성·확장 요구가 생길 때 별도 승인한다.
- 서울 리전 S3 Gateway VPC Endpoint를 public route table에 연결하고 backup bucket prefix로 endpoint policy를 제한하는 방식을 권장한다. 초기 구현에서 제외하면 backup 전송이 Internet Gateway 경로를 사용한다는 점을 명시한다.

AWS의 public subnet 경계는 [Internet Gateway](https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html)와 [subnet route table](https://docs.aws.amazon.com/vpc/latest/userguide/subnet-route-tables.html)을 구현 시 다시 확인한다.

### EC2, 단일 EBS와 운영 접근

- **확정 사양은 x86_64 `t3.small`(2 vCPU, 2 GiB) 한 대다.** Spring Boot, MySQL과 Redis를 함께 실행하는 실전 최소 구성으로 취급하며 여유 있는 권장 용량으로 표현하지 않는다.
- EBS encryption을 계정 기본값으로 켜고 암호화한 `gp3` 20 GiB root volume 하나만 사용한다. 별도 50 GiB data volume은 만들지 않는다.
- MySQL·Redis Docker volume, Nginx·Certbot certificate 상태, OS, image와 log가 모두 이 root volume에 있으므로 디스크 고갈·filesystem 손상·instance 종료가 전체 서비스와 데이터에 동시에 영향을 준다. 70% 사용 경보, Docker log rotation, 미사용 image 정리와 85% 도달 전 EBS 확장 절차를 둔다.
- build는 EC2 밖에서 수행하고 instance에는 digest로 고정한 실행 image만 pull한다. Gradle, pnpm dependency와 build cache를 운영 instance에 만들지 않는다.
- root volume은 사고 복구를 위해 `DeleteOnTermination=false`를 권장하고 EC2 termination protection을 활성화한다. instance-initiated shutdown behavior는 `stop`으로 두며 수동 종료 전에 보호 해제, 최신 S3 backup 확인과 명시적 승인을 요구한다.
- `DeleteOnTermination=false`와 termination protection은 가용성이나 backup이 아니다. 종료 뒤 남은 root volume은 같은 가용 영역의 교체 instance에 연결할 수 있지만, volume·AZ 장애에는 도움이 되지 않고 개인정보가 든 orphan volume을 남길 수 있다. 복구 완료 뒤에는 최대 보유기간과 승인 절차에 따라 추적·파기한다.
- EBS snapshot은 암호화하고 명시적 lifecycle을 적용하지만 crash-consistent system 복구 보조 수단으로만 사용한다. MySQL 논리 backup과 restore rehearsal을 대신하지 않는다.
- instance metadata는 IMDSv2를 필수로 하고, host에는 자동 보안 업데이트 또는 승인된 patch 주기를 둔다.

EBS 암호화 범위는 [Amazon EBS encryption](https://docs.aws.amazon.com/ebs/latest/userguide/ebs-encryption.html)을 기준으로 한다.

### IAM role과 SSM

- EC2에 instance profile을 연결하고 장기 AWS access key를 환경 파일이나 디스크에 저장하지 않는다.
- 기본 role은 Session Manager에 필요한 `AmazonSSMManagedInstanceCore` 또는 동등한 최소 custom policy를 가진다. Session Manager를 사용하면 관리용 inbound port와 SSH key 없이 접속할 수 있다.
- backup 쓰기 권한은 database-backup bucket의 지정 prefix에 대한 최소 `s3:PutObject`, multipart 처리와 필요한 bucket 조회로 한정한다. 복원용 `s3:GetObject`와 삭제 권한은 평시 instance role에서 분리한 운영자·복구 role에 둔다.
- backup이 customer-managed KMS key를 쓰면 instance role에는 해당 backup path 암호화에 필요한 최소 `kms:Encrypt`·data key 권한만 주고, 복호화는 복구 role로 분리한다.
- private ECR을 사용하면 해당 repository image pull 권한만 추가한다. web bucket 쓰기, CloudFront 변경, IAM 관리와 다른 S3 bucket 읽기 권한은 EC2 role에 주지 않는다.
- CloudWatch agent를 도입하면 지정 log group·metric namespace 쓰기만 허용한다. Session Manager session log를 저장할 경우 별도 log group 또는 bucket의 암호화·보유기간·관리자 열람 권한을 함께 정한다.

Session Manager의 instance profile 기준은 [Session Manager 권한](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-getting-started-instance-profile.html)을 따른다.

### Nginx, 인증서와 애플리케이션 실행

- Nginx는 `api` host의 `80→443` redirect, HTTPS termination과 Spring Boot `8080` reverse proxy를 담당한다. proxy에는 원래 `Host`, 실제 client IP와 HTTPS scheme을 전달하는 승인된 `X-Forwarded-*` header만 설정하고 외부가 보낸 forwarded header를 그대로 신뢰하지 않는다.
- Certbot은 Let's Encrypt certificate를 발급하고 systemd timer 또는 동등한 자동 작업으로 만료 전에 `renew`를 실행한다. 갱신 성공 뒤에만 Nginx를 reload하며 `certbot renew --dry-run`, 갱신 실패 경보와 certificate 만료 감시를 운영 검증에 포함한다.
- Nginx는 요청 본문, Cookie, Authorization, 이메일 인증코드와 OpenAI 원문을 access/error log에 남기지 않는다.
- 운영 Compose는 개발용 기본 password를 허용하지 않는다. DB password, JWT/HMAC secret, SMTP credential과 OpenAI API key는 저장소 밖의 root-readable 환경 파일 또는 동등한 secret 주입 경계에서 제공한다.
- 2 GiB host의 초기 memory budget은 Spring Boot container 640 MiB(`-Xms128m -Xmx384m`), MySQL 640 MiB(`innodb_buffer_pool_size` 약 256 MiB, `max_connections` 30 이하), Redis 192 MiB(`maxmemory` 약 96 MiB, `noeviction`), Nginx 64 MiB를 상한 후보로 둔다. 실제 image 시작·부하 검증 후 낮추거나 상향하되 합계가 OS·Docker와 일시 작업의 여유를 침범하지 않게 한다.
- Spring은 Hikari pool 8 이하, Tomcat worker thread 32 이하와 LLM 생성 동시성 제한을 초기값으로 검토한다. MySQL connection·temporary table, Redis AOF rewrite와 Certbot 실행이 동시에 memory peak를 만들지 않게 작업 시간을 분산한다.
- 각 container에 hard memory limit, healthcheck와 `restart` 정책을 두되 OOM 재시작을 정상 복구로 간주하지 않는다. Redis AOF를 유지하며 Redis 유실·`noeviction` write 실패 시 이메일 인증·가입 계속 자격·Refresh Session이 사라지거나 생성되지 않아 재로그인이 필요할 수 있음을 명시한다.
- 암호화된 root EBS에 작은 swap file을 둘 수 있지만 swap은 순간 peak에서 OOM을 늦추는 비상 완충재일 뿐 정상 RAM이나 `t3.medium` 상향을 대신하지 않는다. 낮은 `swappiness`를 사용하고 지속적인 swap-in/out을 성능 저하와 용량 부족 신호로 경보한다.
- 첫 배포에서는 EC2 기본 status/CPU/`CPUCreditBalance`, AWS Budget과 운영자의 `free -m`, `df -h`, `docker stats --no-stream`, `docker compose ps` 점검을 필수로 한다. CloudWatch Agent와 custom memory/disk metric은 첫 배포 필수가 아니며 수동 점검으로 부족할 때 후속 승인한다. 한 번의 OOM kill, 정상 트래픽에서 15분 이상 memory 80% 초과, 15분 이상 swap 사용·thrashing 또는 가용 memory 200 MiB 미만이 발생하면 `t3.medium`(4 GiB) 상향을 우선한다.
- 단일 EC2, 단일 가용 영역과 단일 root volume은 명시적인 장애 단일 지점이다. 첫 MVP에서 자동 failover나 무중단을 보장하지 않고, 장애 시 보존된 root EBS 또는 daily S3 논리 backup을 새 EC2에 복원하는 수동 RTO와 최대 24시간 RPO를 수용한다.

## 분리 origin의 인증 기준

`app`과 `api`는 **cross-origin이지만 HTTPS 기준 same-site**다. 따라서 기존 브라우저 Cookie surface를 유지하되 다음 값을 하나의 묶음으로 검증한다.

| 항목 | 운영 권장안 | 이유 |
| --- | --- | --- |
| Refresh Cookie 이름 | `__Host-openmd_refresh` | 브라우저가 Secure, host-only, `Path=/` 제약을 강제한다. |
| `Secure` | `true` | API HTTPS에서만 전송한다. |
| `HttpOnly` | `true` | 웹 JavaScript와 native bridge가 Refresh Token을 읽지 않는다. |
| `Domain` | 생략 | Cookie는 `api.<service-domain>` 전용이다. web host와 공유하지 않는다. |
| `Path` | `/` | `__Host-` prefix 조건이며 발급·회전·삭제가 동일해야 한다. |
| `SameSite` | `Lax` | HTTPS의 `app`과 `api`가 same-site이므로 `None`이 필요하지 않다. |
| 웹 요청 | `credentials: include` | cross-origin fetch에서 API host Cookie 수신·전송에 필요하다. |
| CORS origin | 기준 web origin 하나를 정확히 허용 | wildcard와 동적 suffix 허용을 금지한다. |
| CSRF | 정확한 `Origin` + `X-OpenMD-CSRF: 1` | custom header preflight와 서버 guard를 기존 계약대로 유지한다. |

- 운영 `OPENMD_CORS_ALLOWED_ORIGINS`와 `OPENMD_AUTH_BROWSER_ALLOWED_ORIGINS`에는 `https://app.<service-domain>` 또는 확정한 `https://www.<service-domain>`의 정확한 origin만 넣는다.
- Nginx는 임의의 CORS header를 합성하거나 요청 `Origin`을 반사하지 않는다. Spring Boot 서버만 승인된 web origin에 `Access-Control-Allow-Origin`과 `Access-Control-Allow-Credentials: true`를 반환한다.
- browser session의 `POST`·`DELETE` preflight는 승인 method/header만 허용하고, 서버는 실제 요청의 `Origin`과 `X-OpenMD-CSRF: 1`을 Redis 접근 전에 검사한다.
- `__Host-` Cookie를 web host에서도 읽기 위해 `Domain=.example.com`으로 넓히지 않는다. 이는 prefix 조건과 최소 권한을 모두 깨뜨린다.
- 웹과 API를 서로 다른 등록 가능 도메인으로 옮기면 `SameSite=None; Secure`, WebView third-party Cookie 동작과 더 강한 CSRF 설계를 다시 승인해야 한다. 이번 계획에서는 금지한다.

### WebView 영향

- production `EXPO_PUBLIC_WEB_URL`은 CloudFront 기준 HTTPS URL인 `https://app.<service-domain>`으로 설정한다.
- WebView 안의 웹 JavaScript가 `https://api.<service-domain>`의 browser session endpoint를 호출하며 WebView cookie jar가 API host 전용 Cookie를 관리한다.
- Refresh Token을 JavaScript나 React Native bridge로 전달하지 않고, 네이티브 body endpoint로 자동 fallback하지 않는다.
- 두 host가 same-site이므로 third-party Cookie 허용에 기대지 않는다. 다만 iOS·Android WebView의 cross-origin `Set-Cookie`, 앱 종료·재실행, 회전과 logout 삭제는 실제 production-like HTTPS 환경에서 각각 검증한다.
- CloudFront origin domain이나 S3 URL을 `EXPO_PUBLIC_WEB_URL`에 직접 넣지 않고 사용자용 custom domain을 사용한다.

## Bucket 분리와 백업

| 구분 | 정적 web bucket | database-backup bucket |
| --- | --- | --- |
| 목적 | 공개 가능한 빌드 artifact의 private origin | 개인정보를 포함할 수 있는 암호화된 MySQL 복구본 |
| 읽기 | 지정 CloudFront OAC만 | 복구 담당 최소 권한 principal만 |
| 쓰기 | CI web deploy principal | EC2 backup role |
| CloudFront 연결 | 있음 | 없음 |
| public access | 전면 차단 | 전면 차단 |
| 수명 주기 | release/cache·rollback 정책 | 탈퇴 정책을 넘지 않는 명시적 보유기간 |

- database-backup bucket도 서울 리전(`ap-northeast-2`)에 두되 web bucket과 합치지 않는다.
- bucket, KMS key, IAM policy와 lifecycle을 공유하지 않는다. 한 credential이 두 bucket을 모두 읽고 쓰지 못하게 한다.
- MySQL은 일관된 논리 dump를 생성한 뒤 전송 전에 암호화한다. 성공 업로드와 checksum이 확인된 뒤에만 로컬 임시본을 정리한다.
- 논리 backup은 매일 실행하고 성공·실패와 마지막 성공 시각을 경보한다. root volume 복구 가능 여부와 관계없이 빈 MySQL에 이 backup을 복원하는 훈련을 수행한다.
- 운영 백업은 가능한 한 신속히 정리하고 탈퇴일로부터 최대 30일 안에 삭제·비식별화를 완료한다는 제품 정책을 넘기지 않는다. 초기 백업 보유기간은 14일을 제안하되 운영자 승인 전 확정값으로 취급하지 않는다.
- 오래된 백업 복원으로 탈퇴 계정이 되살아나지 않도록 백업 시점 이후의 탈퇴·비식별화 작업을 재적용하는 삭제 journal과 복구 절차를 구현한다.
- EBS snapshot은 OS·volume 복구 보조 수단일 뿐 MySQL 논리 backup을 대신하지 않으며, snapshot에도 같은 최대 보유기한을 적용한다.

## 배포와 rollback

### 웹

1. 고정된 pnpm과 Node 버전으로 `pnpm -C web verify`를 통과한다.
2. commit SHA에 대응하는 artifact prefix에 업로드하고 파일 checksum을 확인한다.
3. smoke test 후 기준 `index.html`을 전환하고 필요한 최소 path만 invalidation한다.
4. 실패 시 직전 manifest/index로 되돌린다. 임시로 S3를 public 전환하지 않는다.

### 서버

1. `./scripts/verify.sh all`을 통과한 동일 commit SHA로 Java 21 server image를 만든다.
2. image digest를 고정해 EC2가 pull하도록 하고, migration 전 암호화 backup을 확인한다.
3. Nginx를 통한 HTTPS healthcheck가 통과하고 외부에서 `8080`이 차단됨을 확인한 뒤 새 container로 전환한다.
4. 애플리케이션 실패는 직전 image digest로 rollback한다. Flyway migration은 자동 down migration하지 않으며 이전 image와 호환되지 않는 파괴적 schema 변경은 별도 승인과 복구 rehearsal 없이는 배포하지 않는다.

웹과 서버의 API 계약이 함께 바뀌면 호환 window를 둔다. 배포 순서 때문에 한쪽이 먼저 노출돼도 로그인·학습 주요 흐름이 깨지지 않아야 한다.

## 작업

- [ ] T001 Route 53 Domains에서 새 등록 가능 도메인을 구매하고 `app`/`api` host의 환경별 origin 표를 작성한다.
- [x] T002 private web bucket, OAC, CloudFront, DNS와 certificate의 AWS Console 수동 생성·검증 체크리스트를 작성한다. Terraform은 이번 첫 배포에서 제외한다.
- [x] T003 web build/upload, cache metadata, SPA rewrite와 이전 release rollback 자동화를 작성한다.
- [x] T004 VPC, public subnet, Internet Gateway, route table, security group, Elastic IP, `t3.small`, 암호화 `gp3` 20 GiB root EBS와 SSM/IAM의 AWS Console 수동 체크리스트를 작성한다.
- [x] T005 non-root server image, EC2 운영 Compose, Nginx·Certbot 자동 갱신과 내부 network/health/resource limit을 작성한다.
- [x] T006 운영 secret·환경 변수 계약과 최소 권한 IAM을 문서화하되 실제 값을 저장소에 넣지 않는다.
- [ ] T007 분리 origin에 맞는 Cookie/CORS/CSRF 구성과 계약 테스트를 갱신한다.
- [x] T008 database-backup 전용 bucket, 암호화 dump, lifecycle, 삭제 journal과 restore runbook을 작성한다.
- [x] T009 로그 redaction·rotation, uptime, resource와 backup failure 관측 기준을 작성한다.
- [ ] T010 2 GiB memory budget, container limit, JVM/MySQL/Redis tuning, swap와 `t3.medium` 상향 경보를 부하 검증한다.
- [ ] T011 production-like HTTPS 브라우저와 iOS·Android WebView smoke test를 수행한다.
- [ ] T012 개인정보처리방침과 스토어 URL이 실제 AWS·OpenAI·SMTP 처리 조건과 일치하는지 출시 직전에 확인한다.

## 검증

- 저장소 정적 검증: `./scripts/verify.sh fast`
- Docker와 Testcontainers 포함: `./scripts/verify.sh all`
- Compose 문법: `docker compose -f <production-compose> config --quiet`
- AWS 설정: Terraform은 이번 단계에서 제외한다. 수동 체크리스트와 resource inventory를 두 사람이 검토하며, 외부 생성·변경과 실제 배포는 별도 명시적 승인 후에만 수행한다.
- 보안 smoke: S3 직접 GET 거절, CloudFront GET 성공, HTTP→HTTPS, API 외 port 차단
- TLS smoke: Let's Encrypt certificate chain·host 확인, `certbot renew --dry-run`, 갱신 성공 후 Nginx reload와 만료 경보 확인
- 인증 smoke: 허용 origin의 preflight/login/refresh/logout 성공, 미허용 Origin과 CSRF header 누락 `403`, Cookie 속성 확인
- WebView: 실제 iPhone과 Android에서 로그인 유지, 앱 재실행, refresh 회전, logout과 외부 링크 확인
- 복구: 빈 MySQL에 암호화 backup 복원, migration 적용, 탈퇴 삭제 재적용과 보호 데이터 접근 차단 확인

## 진행 기록

| 날짜 | 상태 | 결과 또는 차단 사유 |
| --- | --- | --- |
| 2026-09-03 | 계획 | private S3 + CloudFront 웹, 단일 EC2 API·데이터와 same-site 별도 host를 사용자 결정으로 기록. 외부 리소스는 변경하지 않음. |
| 2026-09-03 | 계획 | 실전 최소 사양을 `t3.small`, 암호화 `gp3` 20 GiB 단일 root volume과 daily S3 MySQL 논리 backup으로 확정. |
| 2026-09-04 | 구현 | Terraform 없이 server image, 운영 Compose, host Nginx·Certbot, web/server 배포·rollback, MySQL backup·restore와 AWS Console 수동 체크리스트를 저장소에 추가. 실제 AWS·DNS·secret 적용은 하지 않음. |

## 열린 질문과 외부 적용 전 필요한 결정

- 실제 등록 도메인과 기준 web host를 `app`으로 할지 `www`로 할지
- AWS 계정·서울 리전, DNS zone과 비용 알림을 소유할 운영 계정
- 사용할 가용 영역과 `DeleteOnTermination=false` 권장안의 최종 승인
- 초기 백업 보유기간 14일 제안, 목표 RPO/RTO와 허용 가능한 복구 중단 시간
- 운영 SMTP 제공자, OpenAI 실제 모델과 개인정보처리방침에 표시할 처리 국가·재위탁 세부
- CloudFront access log 사용 여부와 운영 로그의 정확한 보유기간
- 외부 apply, DNS 변경, 운영 secret 주입, production build와 실제 배포에 대한 명시적 승인
