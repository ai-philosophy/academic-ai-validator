# AI Academy Frontend

React frontend tach rieng khoi Spring Boot backend trong `../AIAcademy`.

## Chay local

Backend:

```bash
cd ../AIAcademy
./mvnw spring-boot:run
```

Frontend:

```bash
npm install
npm run dev
```

Mac dinh Vite chay o `http://localhost:5173` va proxy cac request `/api` ve Spring Boot `http://localhost:8080`.

Neu backend chay URL khac:

```bash
VITE_API_BASE=http://localhost:8080 npm run dev
```
