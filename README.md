# AI-OJ Next

AI-OJ v2 is a production-oriented rebuild for campus online judge usage. It keeps
the lessons from the previous project, but starts in a clean directory with a new
API, service split, schema migration strategy, and frontend architecture.

## Stack

- JDK 17 target, Spring Boot 3.5.14, Spring Cloud 2025.0.2, Spring Cloud Alibaba 2025.0.0.0
- MySQL 8, Redis 7, RabbitMQ, Nacos 3.x, Sentinel, Actuator, Flyway
- Vue 3, Vite, TypeScript, Pinia, Arco Design
- Docker Compose first, with a layout that can later move to Kubernetes

## Layout

```text
backend/              Maven multi-module backend
apps/web-user/        Student-facing OJ application
apps/web-admin/       Teacher/admin console
packages/api-client/  Shared browser API client
packages/ui/          Shared Vue UI primitives
deploy/               Compose, Docker, Nginx, secrets examples
docs/                 Architecture and operations notes
```

## Local Development

Use JDK 17 or newer. If your default Java is still 1.8, set `JAVA_HOME` to a JDK
17+ installation before building the backend.

```powershell
cd D:\studyProject\ai-oj-next
copy .env.example .env
docker compose -f deploy/compose.yml --profile infra up -d
cd backend
mvn clean package
cd ..
npm install
npm run build
```

The browser talks to the gateway at `VITE_API_BASE_URL`, with public APIs under
`/api/v1/...`.

