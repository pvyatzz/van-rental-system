# Van Rental Booking System — Project Plan

A learning project (Java Spring Boot + Angular + PostgreSQL + Git) that is also a real booking system for a van rental business: 2 Mitsubishi L300 FB (15-17 pax, family/business use) and 1 Hyundai H100 (cargo van).

## Tech Stack
- **Backend:** Java 25, Spring Boot (Maven, using the Maven wrapper `mvnw`/`mvnw.cmd`)
- **Frontend:** Angular 19 (note: globally installed Angular CLI is v22 — pin to v19 when generating the project, see below)
- **Database:** PostgreSQL (`vanrentaldb`)
- **Version Control:** Git + GitHub — commit after every completed feature

## Project Location
```
C:\Workspace\Claude_Project\van-rental-system
├── backend/   → Spring Boot project
└── frontend/  → Angular project
```

## GitHub Setup (do this first)
Repo is currently local only. Connect it to GitHub:
```powershell
git remote add origin https://github.com/YOUR_USERNAME/van-rental-system.git
git add .
git commit -m "Initial commit: backend setup with Vehicle entity and CRUD API"
git branch -M main
git push -u origin main
```

**Commit convention going forward** — one commit per completed, working feature, never commit broken/half-done code to `main`:
- `feat: add Vehicle entity and CRUD endpoints`
- `feat: add Angular vehicle list component`
- `feat: add Booking entity with availability check`
- `fix: correct date overlap validation logic`

## Angular Version Note
Generate the frontend project with Angular CLI 19 specifically (without downgrading the global CLI):
```powershell
cd frontend
npx -p @angular/cli@19 ng new . --routing --style=scss --skip-tests
```

---

## Phase 1 — Backend Foundation (done)
- [x] Spring Boot project setup (Maven, Java 25)
- [x] PostgreSQL connection (`vanrentaldb`) configured in `application.properties`
- [x] `Vehicle` entity — fields: `id`, `model`, `capacity`, `ratePerDay` (BigDecimal), `status` (enum: AVAILABLE/BOOKED/UNDER_MAINTENANCE), `photoUrl`
- [x] `VehicleStatus` enum
- [x] `VehicleRepository` (extends `JpaRepository`)
- [x] `VehicleController` — full CRUD on `/api/vehicles` (GET all, GET by id, POST, PUT, DELETE)
- [x] Verify all endpoints work via Postman/browser (`http://localhost:8080/api/vehicles`)
- [x] **Commit:** `feat: initial backend with Vehicle CRUD API`

## Phase 2 — Angular Frontend Foundation (done)
- [x] Generate Angular 19 project in `frontend/` (see Angular Version Note above — avoid creating a nested `frontend/frontend/` folder, use `ng new .`)
- [x] Set up basic routing structure
- [x] Create `VehicleService` — Angular service using `HttpClient` to call `/api/vehicles`
- [x] Create `VehicleListComponent` — fetches and displays all vehicles in a table/card layout
- [x] Handle CORS on the backend (Spring Boot needs to allow requests from `http://localhost:4200`)
- [x] **Commit:** `feat: Angular frontend displaying vehicle list from backend API`

## Phase 3 — Customer & Booking Entities (done)
- [x] `Customer` entity (name, contact number, email — email optional)
- [x] `Booking` entity (vehicle, customer, start date, end date, purpose, status, total price snapshot)
- [x] Relationships: one `Vehicle` → many `Booking`; one `Customer` → many `Booking` (unidirectional `@ManyToOne`)
- [x] `BookingRepository` + `BookingController` (CRUD + `PATCH /{id}/status` + `GET /availability`), `CustomerController` (CRUD)
- [x] **Availability check logic** — no overlapping non-cancelled bookings for the same vehicle (inclusive dates), vehicle row lock against simultaneous double-booking
- [x] Unit tests for booking rules (`BookingServiceTest`)
- [x] **Commit:** `feat: add Customer and Booking entities with availability validation`

## Phase 4 — Booking Flow (Frontend) (done)
- [x] `BookingService` (Angular)
- [x] Booking form — pick vehicle, select date range, enter customer info
- [x] Show real-time availability feedback (disable/warn if dates conflict)
- [x] Booking confirmation view
- [x] **Commit:** `feat: add booking form and availability UI`

## Phase 5 — Admin View (done)
- [x] Simple admin dashboard — list all bookings, confirm/cancel, manage vehicle info (edit rate, status, photo)
- [x] Basic table with filters (by vehicle, by date range, by status)
- [x] **Commit:** `feat: add admin dashboard for managing bookings and vehicles`

## Phase 6 — Polish & Extras (mostly done)
- [x] Authentication (admin login — Spring Security + JWT) - admin acc username: admin pass: admin123 (local dev defaults only — set ADMIN_PASSWORD before going online)
- [x] Booking reference codes (VR-XXXXXXXX) instead of ids on the customer page
- [x] Email notification on booking confirmation (logs the email until SMTP is configured — see LEARNING_NOTES 6.2)
- [ ] SMS notification (same event, a second listener — needs an SMS provider account)
- [x] Pickup location map (OpenStreetMap + Leaflet instead of Google Maps — no API key; Google Maps links for the driver)
- [x] Reports (most-booked vehicle, monthly revenue, per-van table)
- [ ] Deployment (e.g. Render, Railway, or a VPS) — see "Before going online" in LEARNING_NOTES.md

## Phase 7 — Customer accounts & down payment
- [x] Customers sign in with Google before booking (name + email from Google; "My bookings" page)
- [ ] Paste the Google OAuth Client ID into `environment.ts` and `GOOGLE_CLIENT_ID` (steps in LEARNING_NOTES 7.1)
- [x] ₱500 down payment: GCash/InstaPay QR in the booking form (`bank-acc` section), proof screenshot upload
- [x] Admin views the proof, verifies or rejects it; Confirm requires a verified payment; customer can re-upload
- [x] Emails: booking request received, payment not confirmed, booking confirmed
- [ ] Configure a real mail server (Gmail App Password — LEARNING_NOTES 6.2) so emails are actually sent

---

## Working Agreement
- Explain each step as it's built — the goal is to actually learn Spring Boot and Angular, not just get a working app from copy-pasted code.
- Every feature gets an entry in `LEARNING_NOTES.md` (what / why / concept), committed together with the code.
- Van photos live in `van-img/`; copies served by the frontend are in `frontend/public/images/`.
- One commit per completed, working feature.
- The project owner may return to a separate planning conversation (Claude.ai chat) to discuss schema decisions or debug concepts before handing more work back here — treat that as normal, not a sign something is wrong.
