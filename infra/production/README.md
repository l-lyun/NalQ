# Production infrastructure

This directory contains repository-only artifacts for the first manual AWS deployment.

- `compose.yml`: Spring, MySQL and Redis runtime on one EC2 instance
- `.env.example`: placeholder-only production configuration contract
- `nginx/`: Ubuntu host bootstrap and TLS virtual-host templates
- `certbot/`: certificate renewal deploy hook
- `cloudfront/`: SPA viewer-request rewrite
- `mysql/`, `redis/`: t3.small resource tuning
- `logrotate/`: host Nginx log retention
- `systemd/`: daily MySQL backup unit and timer

The web application intentionally has no Dockerfile. Its Vite `dist/` artifact is deployed directly to private S3 and served only through CloudFront OAC.

Start with the [production runbook](../../docs/operations/production-deployment-runbook.md) and [AWS Console checklist](../../docs/operations/aws-console-production-checklist.md). These files do not create or modify AWS resources.
