# Operations

## Health

Every backend service exposes:

- `/actuator/health`
- `/actuator/prometheus`
- `/actuator/metrics`

Prometheus is configured in `deploy/prometheus.yml`.

## Backup

Use MySQL logical backups with a single transaction:

```powershell
docker exec ai-oj-next-mysql-1 sh -c "mysqldump -uroot -p$MYSQL_ROOT_PASSWORD --single-transaction --routines --triggers ai_oj_next" > backup.sql
```

Practice restore in staging before trusting a backup policy.

## Rollout

1. Start infra.
2. Run one backend instance per service with Flyway enabled.
3. Verify `/actuator/health` and gateway routes.
4. Scale gateway, auth, problem, and AI services.
5. Scale judge workers based on queue depth.

