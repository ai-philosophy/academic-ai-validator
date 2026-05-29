# AI Academy Production Deploy

Production setup uses:

- `frontend`: nginx serves the React build and proxies `/api` to the backend.
- `backend`: Spring Boot app on internal port `8080`.
- `.env`: production secrets and domain config.

## 1. Prepare secrets

The GitHub Models token must not be stored in `application.properties`.

Copy the env template on the server:

```bash
cp .env.example .env
```

Edit `.env`:

```bash
GITHUB_MODELS_TOKEN=your-real-token
APP_CORS_ALLOWED_ORIGINS=https://your-domain.com
FRONTEND_PORT=80
```

If you previously committed or pasted a token, revoke/rotate it before production.

## 2. Build and run

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

Open:

```text
http://your-server-ip
```

or your configured domain.

## 3. Check logs

```bash
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f frontend
```

## 4. Update deployment

```bash
git pull
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

## 5. Stop

```bash
docker compose -f docker-compose.prod.yml down
```

## Notes

- In this Docker setup, the browser calls `/api/...` on the same domain as the React app, and nginx forwards it to Spring Boot.
- If you deploy frontend and backend on separate domains, set `VITE_API_BASE` at frontend build time and put that frontend domain in `APP_CORS_ALLOWED_ORIGINS`.
