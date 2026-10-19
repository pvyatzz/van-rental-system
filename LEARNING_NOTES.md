# Learning Notes

A running log of everything built in this project: **what** each piece is, **why** it's done that way, and the **concept** to learn from it. New entries are added with every feature.

Paths below are relative to:
- Backend: `backend/backend/src/main/java/com/vanrental/backend/`
- Frontend: `frontend/src/app/`

---

## The big picture: how a request flows

```
Angular component ──► Angular service ──HTTP/JSON──► Controller ──► Service ──► Repository ──► PostgreSQL
 (shows data)        (HttpClient)                   (HTTP only)    (rules)     (SQL)
```

Each layer has one job:

| Layer | Job | Should NOT |
|---|---|---|
| Controller | Turn HTTP into method calls and back (status codes, JSON) | Contain business rules |
| Service | Business rules ("no double booking", price calculation) | Know about HTTP |
| Repository | Talk to the database | Contain rules |

Keeping these apart is why the booking rules could be unit-tested without starting a web server or a database (see Phase 3).

---

## Phase 1: Vehicle CRUD API

### `model/Vehicle.java`: the entity
- `@Entity` + `@Table(name = "vehicles")` maps the class to a table. With `spring.jpa.hibernate.ddl-auto=update`, Hibernate **creates and alters the table for you** at startup. That's handy in development, but real projects use migration tools (Flyway/Liquibase) because `update` never deletes or renames columns.
- `@Id @GeneratedValue(strategy = IDENTITY)` means Postgres assigns the id (an auto-increment column).
- `BigDecimal` for money, **never `double`**. A `double` can't store 0.1 exactly, so money math drifts.
- `@Enumerated(EnumType.STRING)` stores `"AVAILABLE"` instead of `0`. With the default (`ORDINAL`), reordering the enum would silently corrupt existing data.
- Lombok's `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` generate the boilerplate at compile time. JPA *requires* a no-args constructor.
- **Validation annotations** (`@NotBlank`, `@NotNull`, `@Min(1)`, `@DecimalMin("0.0")`) are just declarations. They only run when a controller parameter is marked `@Valid`.

### `repository/VehicleRepository.java`
- `extends JpaRepository<Vehicle, Long>` gives you `findAll`, `findById`, `save`, `deleteById`, `existsById`, `count`… with zero code. Spring generates the implementation at runtime.

### `service/VehicleService.java`
- **Constructor injection:** Spring sees the constructor needs a `VehicleRepository` and passes one in. The field is `final`, so it can never be swapped or left null. This is preferred over `@Autowired` on fields.
- `findById` returns `Optional<Vehicle>`. `.orElseThrow(...)` turns "not found" into an exception instead of returning `null`.
- `update` loads the existing row, copies fields onto it, then saves. It doesn't save the incoming object directly, because that could overwrite fields the client didn't send.
- `create` sets `id = null` so a client can't overwrite an existing vehicle by sending an id with a POST.

### `controller/VehicleController.java`
- `@RestController` makes return values become JSON automatically. `@RequestMapping("/api/vehicles")` is the base URL.
- REST conventions used:

| Method | URL | Success code |
|---|---|---|
| GET | `/api/vehicles` | 200 |
| GET | `/api/vehicles/{id}` | 200 (404 if missing) |
| POST | `/api/vehicles` | **201 Created** + `Location` header pointing to the new resource |
| PUT | `/api/vehicles/{id}` | 200 |
| DELETE | `/api/vehicles/{id}` | **204 No Content** |

### `exception/ResourceNotFoundException.java` + `exception/GlobalExceptionHandler.java`
- The service just `throw`s; it doesn't know about HTTP. `@RestControllerAdvice` is a global catcher: it maps exception types to HTTP responses for **every** controller, so no controller needs try/catch.
- Responses use `ProblemDetail` (the RFC 7807 standard error format): `{status, title, detail, instance}`. Validation errors add an `errors` map: `{"model": "must not be blank", ...}`.

### `config/DataSeeder.java`
- A `CommandLineRunner` runs once after the app starts. It inserts the fleet **only when the table is empty**, so restarting never duplicates vans, and changes you make through the API are kept.
- ⚠️ Because of that, editing the rates in this file does nothing once the vans exist. Change rates through `PUT /api/vehicles/{id}`.

---

## Phase 2: Angular frontend

### Project generation
- Created with `npx -p @angular/cli@19 ng new ...`. `npx -p` runs a specific CLI version without touching the global v22 install.
- `--skip-git` stops Angular from creating a second git repo inside `frontend/`.

### `config/WebConfig.java` (backend): CORS
- **CORS** is a *browser* security rule. A page from `http://localhost:4200` may not read responses from `http://localhost:8080` (a different port means a different "origin") unless the server says it's allowed.
- `addCorsMappings` allows only `localhost:4200` and only the methods we use. Other sites get a **403**.
- Postman/curl ignore CORS completely. That's why an API can "work in Postman but not in the browser".

### `environments/environment.ts`
- Keeps the API base URL in one place, so components never hard-code `localhost:8080`.

### `app.config.ts`
- Angular 19 apps have no `AppModule`. Global services are registered here. `provideHttpClient()` makes `HttpClient` injectable everywhere.

### `models/vehicle.ts`
- A TypeScript `interface` that mirrors the Java entity. It only exists at compile time and gives autocomplete and type checks. `VehicleStatus` is a *union type* (`'AVAILABLE' | 'BOOKED' | ...`) matching the Java enum's JSON strings.

### `services/vehicle.service.ts`
- `@Injectable({ providedIn: 'root' })` means one shared instance for the whole app.
- `inject(HttpClient)` is the modern alternative to constructor injection in Angular.
- Methods return `Observable`s. **Nothing is sent until someone calls `.subscribe()`.**

### `vehicles/vehicle-list/`: the first component
- **Standalone component:** it lists what it uses in `imports: [CurrencyPipe]`, with no NgModule.
- **Signals** (`signal()`) hold the state: `vehicles`, `loading`, `error`. Read with `vehicles()`, write with `.set()`. Angular re-renders whatever reads a signal when it changes.
- **New control flow:** `@if / @else if / @for (...; track vehicle.id)`. `track` tells Angular how to identify each item so it re-renders only what changed.
- Every data screen has 3 states to handle: **loading, error, data** (and "empty").
- `currency: 'PHP'` pipe formats `3500` as `₱3,500`.

### `app.routes.ts`
- `''` redirects to `vehicles`. `loadComponent: () => import(...)` **lazy-loads** the component: its code is a separate file downloaded only when you visit the page (you can see `vehicle-list-component` as a separate chunk in `ng build` output).

### Van photos
- The original photos live in **`van-img/`** at the repo root. They're copied into `frontend/public/images/` (`l300-fb.png`, `h100.png`) because Angular serves everything in `public/` from the site root. So the `photoUrl` value `/images/l300-fb.png` stored in the database works directly in `<img [src]>`.
- To add or change a photo: put the file in `van-img/`, copy it to `frontend/public/images/`, then update that vehicle's `photoUrl` through the API.

---

## Phase 3: Customers, Bookings and the availability rule

### `model/Customer.java`
- `fullName` and `contactNumber` are required; **`email` is optional** (`@Email` only checks the format *if* a value is given). Many customers book by phone/text, so the contact number is the reliable field.
- `@Size(max = ...)` matches the column `length` so validation fails with a clear 400 *before* the database rejects a too-long value with a 500.

### `model/BookingStatus.java`
```
PENDING ──► CONFIRMED ──► COMPLETED
   │            │
   └────────────┴──► CANCELLED        (CANCELLED and COMPLETED are final)
```
- `canChangeTo(next)` holds these allowed moves using a `switch` expression. Keeping the rule **inside the enum** puts it in one place that's easy to test.
- Why can't a CANCELLED booking be re-confirmed? Its dates were released, so someone else may have booked them since. Make a new booking instead (which runs the overlap check).

### `model/Booking.java`
- **`@ManyToOne`**: many bookings point to one vehicle and one customer. In the database this is a `vehicle_id` / `customer_id` **foreign key** column (`@JoinColumn`).
- **Unidirectional on purpose:** `Vehicle` does *not* have a `List<Booking>`. A two-way link makes JSON loop forever (vehicle → bookings → vehicle → …) and makes every vehicle load drag its bookings along. When we need a van's bookings, we ask the repository.
- **`fetch = LAZY`:** the vehicle/customer isn't loaded until you touch it. Default for `@ManyToOne` is EAGER, which quietly adds joins everywhere.
- **Dates are `LocalDate`** (no time, no timezone) and **inclusive**: 10 to 12 Nov is 3 rental days. A same-day rental counts as 1 day.
- **`totalPrice` is a snapshot** (`ratePerDay × days` at booking time). If you raise the rate later, old bookings keep the price the customer agreed to.
- `@PrePersist` sets `createdAt` automatically right before the first INSERT.
- `@Index` on `(vehicle_id, start_date, end_date)` speeds up the availability check, which filters on exactly those columns.

### `repository/BookingRepository.java`: the overlap query
Two date ranges overlap when **each one starts on or before the other one ends**:

```
existing:        |----10----11----12----|
new (12–14):                       |----12----13----14----|   overlaps (shares the 12th)
new (13–15):                             |----13----14----15----|   no overlap
```
```sql
b.startDate <= :endDate AND b.endDate >= :startDate   -- and status <> CANCELLED
```
That one condition covers every case: partial overlap at either end, the new booking inside the old one, and the old one inside the new one. **Cancelled bookings are ignored**, so cancelling frees the dates.

- `@Query` with JPQL uses **entity and field names** (`Booking`, `b.startDate`), not table/column names.
- `@EntityGraph(attributePaths = {"vehicle", "customer"})` loads the related rows in the **same** query. Without it, listing 50 bookings would run 1 query + 50 for vehicles + 50 for customers (the "N+1 problem").

### `repository/VehicleRepository.java`: row locking
- Problem: two people book the same van for the same dates at the same moment. Both run the overlap check, both see "free", and both save. That's a double booking.
- Fix: `@Lock(PESSIMISTIC_WRITE)` turns the vehicle lookup into `SELECT … FOR UPDATE`. The second request **waits** until the first transaction commits, then its overlap check sees the first booking and gets a 409.
- Verified: 5 identical simultaneous requests gave exactly **one 201 and four 409s**.
- Only works inside a transaction, which is why the booking service methods are `@Transactional`.

### `dto/`: request/response records (first use of DTOs)
For Vehicle and Customer the API sends and receives the entity itself. For bookings that breaks down:
- **`BookingRequest`**: the client sends `vehicleId` + `customerId` (not whole nested objects), dates, and purpose. The client *can't* set `status`, `totalPrice` or `createdAt`; the server decides those.
- **`BookingResponse`**: a flat record with `vehicleModel`, `customerName`, etc. It's built inside the transaction, so it never contains lazy Hibernate proxies (which Jackson can't serialize).
- `record` = an immutable data class: the constructor, getters (`request.vehicleId()`), `equals` and `toString` are all generated.
- `@FutureOrPresent` on `startDate` rejects bookings in the past.

### `service/BookingService.java`: the business rules
`create()` runs these steps in order:
1. End date must not be before start date, otherwise **400**.
2. Lock the vehicle row (see above). A missing vehicle gives **404**.
3. Load the customer. A missing customer gives **404**.
4. Vehicle is `UNDER_MAINTENANCE`, so **409**.
5. Any non-cancelled booking overlaps, so **409** with a message naming the clashing booking.
6. Save with status `PENDING` and the calculated `totalPrice`.

- `update()` runs the same checks but **excludes the booking itself** from the overlap check. Otherwise moving a booking by one day would "conflict" with its own old dates.
- `update()` doesn't call `save()`: the booking was loaded inside the transaction, so it's a *managed entity*, and Hibernate writes the changes automatically on commit ("dirty checking").
- `@Transactional(readOnly = true)` on reads is a small performance hint and documents intent.
- `checkAvailability()` powers the "is this van free?" feedback the booking form will show in Phase 4.

### New exceptions → HTTP codes (`exception/`)
| Exception | Code | Example |
|---|---|---|
| `ResourceNotFoundException` | 404 | Vehicle 999 doesn't exist |
| `InvalidRequestException` | 400 | End date before start date; reconfirming a cancelled booking |
| `BookingConflictException` | 409 | Dates already booked; van under maintenance |
| `DataIntegrityViolationException` (Spring's) | 409 | Deleting a van or customer that still has bookings |

- **400 vs 409:** 400 means *your request is wrong*. 409 means *your request is fine, but it clashes with the current state*. Try different dates and it would work.
- The delete methods call `flush()` inside a transaction. That sends the DELETE immediately, so the database's foreign-key error happens *inside* the service and the 409 handler catches it.

### `controller/CustomerController.java` + `controller/BookingController.java`
| Method | URL | What |
|---|---|---|
| GET/POST | `/api/customers` | list / create |
| GET/PUT/DELETE | `/api/customers/{id}` | read / update / delete (409 if they have bookings) |
| GET | `/api/bookings` | list, newest start date first |
| POST | `/api/bookings` | create (status PENDING) |
| GET/PUT/DELETE | `/api/bookings/{id}` | read / change dates, van, purpose / delete |
| PATCH | `/api/bookings/{id}/status` | `{"status":"CONFIRMED"}`: confirm / cancel / complete |
| GET | `/api/bookings/availability?vehicleId=1&startDate=2026-11-10&endDate=2026-11-12` | `{"available": true/false}` |

- **PUT vs PATCH:** PUT replaces the editable fields; PATCH changes just one thing (the status). PATCH had to be added to the CORS allowed methods.
- `@DateTimeFormat(iso = DATE)` tells Spring to parse `2026-11-10` from the query string into a `LocalDate`.

### `test/.../service/BookingServiceTest.java`: unit tests with Mockito
- `@Mock` creates fake repositories. `when(...).thenReturn(...)` scripts what the fake "database" returns. `@InjectMocks` builds a real `BookingService` with the fakes.
- No Spring, no Postgres: the 7 tests run in about 0.25 s and test **only the rules**: price math, overlap → 409, maintenance → 409, bad dates → 400, a booking not conflicting with itself, and a cancelled booking being final.
- `verify(bookingRepository, never()).save(any())` checks that nothing was saved when the booking was rejected.
- What these tests *can't* check: the real SQL query. That was verified by running the app and calling the API (overlap on a shared day, adjacent dates allowed, other van allowed, cancelled booking freeing its dates).

### Known limitations and ideas for later
- `Vehicle.status = BOOKED` isn't set automatically. Availability now comes from booking **dates**, so `BOOKED` may become unnecessary. Decide in Phase 5 whether to drop it or set it on the day of pickup.
- `@FutureOrPresent` also applies when *editing*, so a booking that has already started can't have its dates edited. Usually that's what you want.
- Postgres could enforce "no overlaps" itself with an *exclusion constraint* (`EXCLUDE USING gist`). That's a stronger guarantee worth learning once migrations (Flyway) are added.
