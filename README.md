# Van Rental System

An online booking system for a small van rental business: **2× Mitsubishi L300 FB** (16 seats, family and company trips) and **1× Hyundai H100** (cargo). Built as a learning project with **Spring Boot**, **Angular** and **PostgreSQL**.

## Features

**Customers**
- Browse the vans with photos, seats and daily rates
- Sign in with **Google**, then book: pick a van and dates with **live availability**, add a pickup pin on a map
- Pay a **₱500 down payment** by scanning a GCash / InstaPay QR code and upload the screenshot
- Get **emails** when the booking is received, if the payment needs checking, and when it's confirmed
- **My bookings** page; each booking has a private reference code (e.g. `VR-7K3P9QXA`)

**Admin**
- Username/password login (same sign-in page as customers)
- Bookings table with filters; view the payment screenshot, **verify or reject** it, then confirm, complete or cancel
- Edit vans (rate, status, photo)
- **Reports**: revenue per month, most-booked van, per-van totals

## Tech stack

| Part | Technology |
|---|---|
| Backend | Java 25, Spring Boot 4 (Web MVC, Data JPA, Security + JWT, Validation, Mail), Maven wrapper |
| Frontend | Angular 19 (standalone components, signals, reactive forms), Leaflet + OpenStreetMap |
| Database | PostgreSQL |
| Auth | Google Identity Services (customers), username/password + JWT (admin) |

## Project layout

```
backend/backend/   Spring Boot API (http://localhost:8080/api)
frontend/          Angular app   (http://localhost:4200)
van-img/           Original van photos and payment QR code
LEARNING_NOTES.md  What was built in each phase, why, and the concepts behind it
PROJECT_PLAN.md    The phase-by-phase plan and progress
```

## Running it locally

See **[QUICK_START.md](QUICK_START.md)**.

## Learning notes

[LEARNING_NOTES.md](LEARNING_NOTES.md) explains every part of the system: layers, JPA relationships, the double-booking check, JWT security, Google sign-in, file uploads, events and emails, reports, and the UI design system.
