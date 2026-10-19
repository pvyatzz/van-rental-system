# Quick start

## You need
- **Java 25** (JDK)
- **Node.js 20+** and npm
- **PostgreSQL** with a database named `vanrentaldb`

## 1. Database
Create the database once (the tables are created automatically on first start):
```sql
CREATE DATABASE vanrentaldb;
```
The connection settings are in `backend/backend/src/main/resources/application.properties` (user `postgres`). Put a different password in `secrets.properties` (below) as `spring.datasource.password=...`.

## 2. Private settings (optional, but needed for real emails)
```
backend/backend/src/main/resources/secrets.properties.example  →  copy to  secrets.properties
```
Fill in a Gmail address and a Gmail **App Password** (https://myaccount.google.com/apppasswords). This file is git-ignored. Without it, the app still runs and writes the emails to the log instead of sending them.

## 3. Backend
```powershell
cd backend\backend
.\mvnw.cmd spring-boot:run
```
API at http://localhost:8080/api. The 3 vans are added automatically on first start.

## 4. Frontend
```powershell
cd frontend
npm install
npx ng serve
```
Open http://localhost:4200.

## Logins
- **Customers:** "Continue with Google" on the Sign in page. The Google Client ID is in `frontend/src/environments/environment.ts` and `application.properties`.
- **Admin:** Sign in → "Admin sign-in". Local default: `admin` / `admin123`. Set `ADMIN_PASSWORD` (or `app.admin.password` in `secrets.properties`) before putting this online.

## Tests
```powershell
cd backend\backend
.\mvnw.cmd test
```
