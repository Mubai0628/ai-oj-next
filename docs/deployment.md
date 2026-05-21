# Deployment

## Local Compose

```powershell
cd D:\studyProject\ai-oj-next
copy .env.example .env
docker compose -f deploy/compose.yml --profile infra up -d
docker compose -f deploy/compose.yml --profile app --profile judge up -d --build
```

## Ports

- Gateway: `8101`
- Auth service: `8201`
- Problem service: `8202`
- Judge worker: `8203`
- AI service: `8204`
- Nacos: `8848`, `9848`, `9849`, `7848`, console `8080`
- Sentinel dashboard: `8858`
- RabbitMQ management: `15672`
- Prometheus: `9090`
- Grafana: `3000`

## Production Notes

- Keep Nacos, MySQL, Redis, RabbitMQ, and Sentinel on an internal network.
- Use Docker secrets or host-mounted secret files for JWT private keys, AI keys,
  database passwords, and sandbox tokens.
- Scale stateless services with additional replicas. Scale judge workers
  independently according to RabbitMQ queue depth.
- Enable Flyway in exactly one deployment wave before increasing replicas, or
  let the first app wave run migrations while the old system remains online.

