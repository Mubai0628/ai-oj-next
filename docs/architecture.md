# Architecture

AI-OJ Next is split around deployment and ownership boundaries:

- `gateway-service`: public `/api/v1` entry, CORS, trace id, route-level Sentinel protection.
- `auth-service`: login, registration, refresh token issue, role identity.
- `problem-service`: problem catalog, submissions, judge task publishing.
- `judge-worker`: RabbitMQ-only judge consumer, horizontally scalable.
- `ai-service`: teaching assistant, AI draft generation, quota and approval workflow.

The browser never calls services directly. It calls `gateway-service`, which
routes `/api/v1/auth/**`, `/api/v1/problems/**`, `/api/v1/submissions/**`, and
`/api/v1/ai/**`.

State is externalized to MySQL, Redis, RabbitMQ, and JWT. Service instances are
safe to scale horizontally. Judge workers are the only components that execute
untrusted-code-related workflows, and they should run on separate hosts from the
gateway and business services.

## AI Governance

The AI module is designed for teaching:

- Responses should guide, diagnose, and hint before producing final code.
- Generated problems are drafts until an admin approves them.
- Usage records and quotas are first-class domain objects.
- The provider boundary is OpenAI-compatible so Kimi/Moonshot and other campus
  approved providers can be switched by configuration.

