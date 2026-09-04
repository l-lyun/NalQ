---
document_type: operations-checklist
status: draft
scope: aws-production-manual
---

# [Operations Checklist] 첫 운영 AWS 수동 구성

Terraform을 사용하지 않는 첫 배포용 체크리스트다. 이 문서는 AWS 리소스를 생성하지 않는다. 모든 외부 적용은 계정·도메인·비용 책임자와 rollback 승인을 받은 뒤 AWS Console에서 수동 수행한다.

## 0. 승인과 원장

- [ ] AWS account ID, 운영 IAM principal, MFA와 billing 연락처를 확정한다.
- [ ] API·S3·backup 리전은 서울 `ap-northeast-2`로 고정한다.
- [ ] 월 Budget과 50/80/100% 알림을 만든다.
- [ ] `Project=nalq`, `Environment=production`, `Owner=<operator>` tag를 사용한다.
- [ ] Route 53 Domains에서 첫 운영용 등록 가능 도메인을 새로 구매한다.
- [ ] 등록 과정에서 자동 생성된 public hosted zone과 네 개의 authoritative name server를 기록한다. Domain 연간 등록료와 hosted zone 월 요금은 별도다.

## 1. 도메인, DNS와 인증서

- [ ] Route 53 public hosted zone에 `app.<domain>`과 `api.<domain>`을 만들 권한이 있는지 확인한다.
- [ ] CloudFront용 public ACM certificate를 반드시 `us-east-1`에 요청하고 DNS validation을 완료한다.
- [ ] `app.<domain>`이 이 certificate의 SAN에 포함되는지 확인한다.
- [ ] API는 Let's Encrypt를 사용하므로 ACM certificate를 EC2에 복사하지 않는다.
- [ ] CloudFront 생성 뒤 Route 53의 `app` A/AAAA Alias를 distribution으로 연결한다.
- [ ] Elastic IP 연결 뒤 `api` A record를 해당 주소로 연결한다.

도메인 구매는 이름의 사용권, DNS는 이름과 목적지의 연결, TLS certificate는 소유권 검증과 전송 암호화다. 어느 하나가 다른 둘을 대신하지 않는다.

## 2. private web S3와 CloudFront

- [ ] 서울 리전에 web 전용 general-purpose bucket을 만든다.
- [ ] Object Ownership은 bucket owner enforced, ACL은 끄고 Block Public Access 네 항목을 모두 유지한다.
- [ ] S3 website hosting을 켜지 않고 기본 암호화를 유지한다.
- [ ] CloudFront distribution의 origin은 S3 REST origin으로 선택한다.
- [ ] OAC를 `always sign`으로 만들고 지정 distribution ARN에 대해서만 `s3:GetObject`를 허용한다.
- [ ] viewer protocol은 HTTP to HTTPS redirect, allowed methods는 GET/HEAD/OPTIONS로 제한한다.
- [ ] [`spa-rewrite.js`](../../infra/production/cloudfront/spa-rewrite.js)를 `cloudfront-js-2.0` runtime의 viewer-request CloudFront Function으로 게시·연결한다.
- [ ] `index.html`은 no-cache, `assets/*`는 immutable 장기 cache를 사용한다.
- [ ] CloudFront access log는 첫 배포에서 생략한다. 활성화할 경우 별도 bucket, query-string 제외와 보유기간을 먼저 승인한다.

검증은 CloudFront GET 성공, `/learning` 같은 직접 route 성공, S3 object URL 익명 GET 거절, 존재하지 않는 `.js` asset의 404 유지다.

## 3. backup S3

- [ ] web bucket과 다른 서울 리전 bucket을 만든다.
- [ ] Block Public Access, Object Ownership bucket owner enforced와 기본 SSE-S3를 설정한다.
- [ ] `mysql/` prefix에 14일 lifecycle expiration을 제안값으로 설정한다.
- [ ] CloudFront 연결과 public policy를 만들지 않는다.
- [ ] EC2 role은 해당 prefix에 `PutObject`, multipart upload 처리와 필요한 최소 bucket 조회만 허용한다.
- [ ] 평상시 EC2 role에는 `GetObject`와 `DeleteObject`를 주지 않는다. 복원 principal을 별도로 둔다.

## 4. EC2, network와 storage

- [ ] Ubuntu 24.04 LTS x86_64, `t3.small`, 서울 리전 한 AZ를 선택한다.
- [ ] account-level EBS encryption을 확인하고 encrypted gp3 20 GiB root volume을 사용한다.
- [ ] termination protection을 켜고 root EBS `DeleteOnTermination=false` 권장안을 승인한다.
- [ ] IMDSv2 required와 instance initiated shutdown behavior `stop`을 설정한다.
- [ ] Elastic IP 하나를 ENI에 연결한다.
- [ ] 전용 security group inbound는 인터넷 `80/tcp`, `443/tcp`만 허용한다.
- [ ] `22`, `8080`, `3306`, `6379` inbound rule이 없음을 확인한다.
- [ ] 첫 배포는 existing/default VPC의 public subnet을 사용하며 새 VPC, Internet Gateway와 route table을 만들지 않는다. 해당 subnet에 인터넷 경로와 public IPv4 할당이 있는지 확인한다.
- [ ] NAT Gateway, ALB, private subnet과 S3 VPC Endpoint는 만들지 않는다.
- [ ] T3 CPU credit mode와 `CPUCreditBalance`, surplus charge 정책을 기록한다.

## 5. IAM과 운영 접근

- [ ] EC2 instance profile에 Session Manager 최소 권한을 연결하고 SSH key와 port 22를 사용하지 않는다.
- [ ] 선택한 ECR repository pull 권한만 추가한다. GHCR을 사용하면 credential 보관 경계를 별도로 승인한다.
- [ ] backup bucket의 지정 prefix write 외 다른 S3·IAM·CloudFront 관리 권한을 EC2 role에 주지 않는다.
- [ ] web 배포 principal은 web bucket release/object write와 지정 distribution invalidation만 가진다.
- [ ] restore principal은 backup read를 가지되 평상시 instance role과 분리한다.

## 6. instance bootstrap

- [ ] Docker Engine, Compose plugin, Nginx, Certbot Nginx plugin과 AWS CLI를 설치한다.
- [ ] 1~2 GiB swap을 encrypted root EBS에 만들고 `vm.swappiness=10`으로 시작한다.
- [ ] repository는 `/opt/nalq/repository`, 서버 env는 `/opt/nalq/production.env` mode `600`으로 둔다.
- [ ] 웹 배포 env는 `/opt/nalq/web-deploy.env`로 분리하고 server/DB/auth 비밀이 없음을 검증한다. 웹 배포 principal에는 web bucket과 지정 distribution 외 권한을 주지 않는다.
- [ ] [`nalq-nginx`](../../infra/production/logrotate/nalq-nginx)를 `/etc/logrotate.d/`에 설치한다.
- [ ] backup service/timer를 `/etc/systemd/system/`에 설치하고 daemon-reload 후 timer를 활성화한다.
- [ ] Docker daemon과 container의 동시 log 정책이 충돌하지 않는지 확인한다. Compose는 container당 10 MiB × 3 files로 제한한다.

## 7. 관측과 상향 기준

- [ ] EC2 기본 status check, CPU와 `CPUCreditBalance`를 확인하고 Budget 알림 수신을 검증한다.
- [ ] 운영 점검 때 `free -m`, `df -h`, `docker stats --no-stream`, `docker compose ps`를 기록한다.
- [ ] disk 70%부터 정리·증설을 검토하고 85% 전 EBS 확장 runbook을 실행한다.
- [ ] backup 실패와 마지막 성공 시각을 알린다.
- [ ] OOM kill 1회, 정상 트래픽 memory 80% 지속, available memory 200 MiB 미만 또는 지속 swap이면 t3.medium 상향을 우선한다.
- [ ] 중앙 application log 전송은 첫 배포에서 생략할 수 있다. 장애 분석에 필요한 보유기간을 확정하면 최소 log group만 추가한다.
- [ ] CloudWatch Agent와 custom memory/disk metric은 첫 배포 필수가 아니다. 수동 점검으로 부족하다고 판단할 때 별도 승인한다.

## 8. 수동 resource inventory

다음 값은 secret 없이 운영 원장에 기록한다.

- account ID, region, AZ, 생성일과 담당자
- domain registrar, hosted zone ID와 record 목록
- web/backup bucket 이름과 lifecycle
- distribution ID, OAC ID, Function ARN, ACM certificate ARN
- EC2 instance ID, AMI ID, EIP allocation ID, volume ID, security group ID
- instance profile/role 이름과 policy ARN
- image repository와 승인한 server image digest
- Budget·alarm 이름
- 각 리소스의 rollback·삭제 순서

Console 화면 캡처만 원장으로 사용하지 않는다. 변경할 때 기존 설정을 JSON으로 export하거나 필드별 값을 기록하고 두 번째 환경이나 재구축 전에는 IaC 전환을 승인한다.
