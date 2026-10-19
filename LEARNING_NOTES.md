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

---

## Phase 4: Booking flow (frontend)

User journey: **Vans page → "Book this van" → booking form (live availability + price estimate) → confirmation page**.

### `models/booking.ts`, `models/customer.ts`
- TypeScript mirrors of the backend DTOs. `BookingRequest` is what we **send**, `Booking` is what we **get back**. They're separate types because the server adds fields (`id`, `status`, `totalPrice`, `createdAt`) that the client must not set.
- `NewCustomer = Omit<Customer, 'id'>` is a *utility type*: "a Customer without the id". Handy for create requests.
- Dates are plain `'YYYY-MM-DD'` strings everywhere. That's the same format as `<input type="date">` and Java's `LocalDate`, so no conversion is needed.

### `services/booking.service.ts`, `services/customer.service.ts`
- Same pattern as `VehicleService`: one method per endpoint, each returning an `Observable`.
- `http.get(url, { params: { vehicleId, startDate, endDate } })` lets HttpClient build and URL-encode the `?a=1&b=2` query string. Don't glue strings together yourself.

### `shared/dates.ts`: timezone gotcha
- `new Date().toISOString()` gives the **UTC** date. In the Philippines (UTC+8) that's still *yesterday* until 8 AM, so "today" would be wrong every morning. `todayIso()` builds the date from local `getFullYear()/getMonth()/getDate()` instead.
- `rentalDays()` uses the **same inclusive rule as the backend** (10–12 Nov = 3 days), so the estimate matches the real price.
- `'YYYY-MM-DD'` strings can be compared with `<` and `>` directly, because text order equals date order in that format.

### `shared/problem.ts`: one place to turn errors into messages
- Every HTTP error arrives as an `HttpErrorResponse`. Its `.error` is the backend's ProblemDetail JSON from Phase 1, so we show `detail`, or the per-field `errors`.
- `status === 0` means the request never reached the server (backend down, or CORS blocked).

### `app.config.ts`: `withComponentInputBinding()`
- With this enabled, the router passes **route params and query params straight into component inputs**:
  - `/bookings/:id` fills `id = input.required<string>()` in the confirmation component.
  - `/book?vehicleId=2` fills `vehicleId = input<string>()` in the form.
- There's no need to inject `ActivatedRoute` and subscribe. Values always arrive as **strings**, hence `Number(this.id())`.

### `app.routes.ts`
- `/book` (optional `?vehicleId=`) → form; `/bookings/:id` → confirmation; `**` (any unknown URL) → redirect to `/vehicles`.
- Each page is lazy-loaded, so the form's code (the biggest chunk, because of the forms library) is only downloaded when someone opens it.

### `vehicles/vehicle-list/`: "Book this van" button
- `routerLink="/book" [queryParams]="{ vehicleId: vehicle.id }"` builds the link `/book?vehicleId=2`.
- Vans `UNDER_MAINTENANCE` show a disabled "Not bookable right now" instead.
- `routerLinkActive="active"` in the header adds the `active` class to the link of the current page (the white underline).

### `bookings/booking-form/`: reactive forms
**Reactive forms** define the form in TypeScript and connect the HTML to it, as opposed to template-driven forms that live in the HTML.

```ts
form = this.fb.nonNullable.group({
  vehicleId: [0, [Validators.required, Validators.min(1)]],
  dates:    this.fb.nonNullable.group({ startDate: [''], endDate: [''] }, { validators: dateRangeValidator }),
  customer: this.fb.nonNullable.group({ fullName: [...], contactNumber: [...], email: [...] }),
  purpose: ['', Validators.maxLength(255)]
});
```
- In the HTML: `[formGroup]="form"` on the `<form>`, `formGroupName="dates"` on a fieldset, `formControlName="startDate"` on the input.
- `nonNullable` means `reset()` goes back to the initial value instead of `null`, and the types are `string`, not `string | null`.
- **Nested groups** (`dates`, `customer`) mirror the screen sections and let one validator see two fields together.
- **Custom group validator** `dateRangeValidator` returns `{ endBeforeStart: true }` or `{ startInPast: true }` (or `null` if OK). The template checks it with `form.controls.dates.hasError('endBeforeStart')`.
- Validators **mirror the backend** (`maxLength(100)` = `@Size(max = 100)`) so the user sees the problem instantly. The backend still validates everything, because the frontend can always be bypassed (curl, Postman).
- **Errors only after `touched`:** the user isn't yelled at before typing. On submit, `markAllAsTouched()` reveals every remaining error at once.
- `<option [ngValue]="vehicle.id">` keeps the value a **number**. Plain `value="..."` would turn it into a string.

### Real-time availability: an RxJS pipeline
```ts
form.valueChanges.pipe(
  startWith(null),                       // also run once on load (for a ?vehicleId= preselect)
  map(() => ({ vehicleId, startDate, endDate })),
  distinctUntilChanged(sameVanAndDates), // typing your name doesn't trigger a check
  tap(() => availability.set('idle')),
  debounceTime(300),                     // wait until the user pauses for 300 ms
  switchMap(q => checkAvailability(q)),  // cancels the previous request if a new one starts
  takeUntilDestroyed()                   // auto-unsubscribe when leaving the page
).subscribe(state => availability.set(state));
```
- **Why `switchMap`?** The user picks the 10th, then the 11th. If the answer for the 10th arrives *after* the answer for the 11th, without `switchMap` the screen would show the wrong (old) result. `switchMap` drops the old request.
- **Why `debounceTime`?** It avoids firing a request for every click in the date picker.
- The state is one signal with 5 values: `'idle' | 'checking' | 'available' | 'unavailable' | 'error'`. A single "state" signal is easier to reason about than several booleans.
- The submit button is disabled unless the state is `'available'`.

### Signals + forms: `toSignal` and `computed`
- `toSignal(form.valueChanges...)` turns the form's value stream into a signal.
- `computed()` values (`selectedVehicle`, `days`, `estimatedTotal`) then **recalculate automatically** when the form changes. There's no manual "update the total" code.
- The total is labelled *(estimate)*. The **server** calculates the real `totalPrice`, and the confirmation page shows the server's number.

### Submitting: two requests in a row
```ts
getOrCreateCustomer(customer).pipe(
  switchMap(saved => bookingService.create({ ...dates, customerId: saved.id }))
).subscribe({ next: b => router.navigate(['/bookings', b.id]), error: ... });
```
- The backend needs a `customerId`, so the customer is created first, then the booking. `switchMap` chains them: the second request starts with the result of the first.
- **409 handling:** if someone else books the same dates *between* the availability check and the click (tested on purpose), the server's message is shown and the status flips to "Not available".
- **No duplicate customer on retry:** the created customer is remembered (`savedCustomer`). If the user picks new dates and submits again with the same details, it's reused instead of created twice.

### `bookings/booking-confirmation/`
- `id = input.required<string>()` comes from the URL. The component loads `GET /api/bookings/{id}` in `ngOnInit`.
- `DatePipe` (`date: 'EEE, MMM d, y'` → "Fri, Nov 20, 2026") treats a date-only string as a **local** date, so there's no timezone shift.
- **Template gotcha (hit while building):** `@if (booking(); as b)` only works on the **first** `@if` of a chain. `@else if (x; as y)` is a compile error, so the block with `as` must come first.

### `styles.scss`: global `.btn`
- Component styles are **scoped** (they only affect their own component). The button is used on 3 pages, so it lives in the global stylesheet instead.
- **Whitespace gotcha:** Angular removes whitespace-only text between tags, so `</strong> <span>` renders with **no** space. The gap is added with CSS `margin-left` instead.

### How Phase 4 was tested
A Node script drove a real (headless) Chrome through the Chrome DevTools Protocol. It clicked buttons, filled fields and read the page, and **16 checks passed**:
- Book button preselects the van; submit stays disabled until dates are available
- End-before-start error; "Available" with a correct estimate (3 × ₱3,500 = ₱10,500)
- Empty details show field errors without sending a request
- A successful submit opens the confirmation with the server's dates, status and total
- Overlapping dates show "Not available"
- Someone booking first → 409 message; a retry with new dates works without a duplicate customer
- An unknown booking id shows an error; no horizontal scroll at phone width (390 px)

### Known limitations and ideas for later
- **Privacy:** anyone who guesses `/bookings/5` can see that booking's name and phone number. Fine for learning, but before going public use a random reference code instead of the id, or require login (Phase 6).
- **Repeat customers** get a new customer row each time they book. Later: look up an existing customer by contact number.
- Creating the customer and the booking are two separate requests. If the second fails for good, a customer without bookings is left behind. A single backend endpoint (`POST /api/bookings` with customer details inside) would make it one transaction.

---

## Phase 5: Admin dashboard

Two tabs under **`/admin`**:
- **Bookings:** filter by van, status and date range; Confirm, Mark completed or Cancel.
- **Vans:** edit model, seats, rate, status and photo.

### Backend: `GET /api/bookings` with optional filters
```
GET /api/bookings?vehicleId=1&status=PENDING&from=2026-11-01&to=2026-11-30   (every part optional)
```
- `@RequestParam(required = false)`: a missing parameter becomes `null` instead of a 400.
- **Date window filter:** `from`/`to` keep bookings that **overlap** the window, using the same idea as the availability check. A booking from 10–12 Nov shows up for `from=2026-11-11`, because it's still running on the 11th.
- `to` before `from` gives a 400 with a clear message.

### `repository/BookingSpecifications.java`: JPA Specifications
- **Problem:** 4 optional filters make 16 combinations. Writing a `@Query` for each is silly. One query with `(:status IS NULL OR b.status = :status)` looks clever, but Postgres often fails on it ("could not determine data type of parameter") because it can't guess the type of a `null` parameter.
- **Solution:** a `Specification` is one reusable WHERE condition written in Java:
  ```java
  static Specification<Booking> hasStatus(BookingStatus s) {
      return (root, query, cb) -> cb.equal(root.get("status"), s);
  }
  ```
  The service adds only the conditions the user actually filled in, then combines them with `Specification.allOf(list)`. That means AND, and an empty list means "no filter".
- `root` is the `Booking` being queried, `cb` (CriteriaBuilder) makes the conditions (`equal`, `lessThanOrEqualTo`, ...), and `root.get("vehicle").get("id")` follows the relationship.
- To use them, the repository `extends JpaSpecificationExecutor<Booking>`, which adds `findAll(spec, sort)`.
- We **override** that `findAll` only to put `@EntityGraph` on it, so the vehicle and customer still load in the same query.
- `Sort.by(DESC, "startDate")` puts the newest bookings first.

### `GlobalExceptionHandler`: `MethodArgumentTypeMismatchException`
- `?status=FOO` or `?vehicleId=abc` can't be converted to the Java type. Spring's default answer is an ugly or empty 400. Now it's a ProblemDetail: *"Invalid value 'FOO' for parameter 'status'"*.

### `application.properties` cleanup
- Removed `spring.jpa.database-platform=...PostgreSQLDialect`. Hibernate warned that it's unnecessary, because it detects the database from the connection.

### Frontend: nested routes (`app.routes.ts`, `admin/admin.component.ts`)
```ts
{ path: 'admin', loadComponent: AdminComponent, children: [
    { path: '', redirectTo: 'bookings', pathMatch: 'full' },
    { path: 'bookings', loadComponent: AdminBookingsComponent },
    { path: 'vehicles', loadComponent: AdminVehiclesComponent } ] }
```
- `AdminComponent` is a **shell**: the title, the tab links and its **own `<router-outlet>`**. The child page appears inside it, while the app's header stays outside. That's two outlets nested.
- `routerLink="bookings"` (no leading `/`) is **relative**: it resolves to `/admin/bookings` because the link lives inside `/admin`.
- `AdminComponent` uses inline `template:` and `styles:` instead of separate files. That's fine for small components; bigger ones read better as separate `.html` and `.scss` files.

### `services/booking.service.ts`: `search()` with `HttpParams`
- Loops over the filters and **skips empty ones**, so the URL is `?status=PENDING`, not `?vehicleId=&status=PENDING&from=&to=`.
- `HttpParams` is **immutable**: `params.set(...)` returns a *new* object, so you must write `params = params.set(...)`. Forgetting the `params =` is a classic bug where no parameters get sent at all.

### `admin/admin-bookings/`: filters + table + actions
**Filters → reload** uses the same pattern as the Phase 4 availability check:
```ts
filters.valueChanges.pipe(startWith(null), map(...), tap(showLoading), debounceTime(250),
  switchMap(f => search(f).pipe(catchError(err => { showError(err); return of([]); }))),
  takeUntilDestroyed())
```
- **`catchError` goes *inside* `switchMap`.** If an error reached the outer stream, RxJS would end the whole subscription, and the filters would silently stop working after the first failed request. Inside, only that one request fails and the stream keeps listening.
- `filters.reset()` ("Clear filters") also fires `valueChanges`, so the table reloads by itself.
- `vehicleId: 0` means "All vans" in the dropdown; `f.vehicleId || undefined` turns 0 into "don't send it".

**Action buttons**
- `ACTIONS: Record<BookingStatus, StatusAction[]>` lists which buttons each status gets. It **mirrors the backend rule** `BookingStatus.canChangeTo()`, so the UI never offers a move the server would refuse. The server still checks: the UI is a convenience, the backend is the rule.
- Cancel asks `confirm(...)` first, because it can't be undone. Confirm doesn't ask, since it's harmless and reversible by cancelling.
- `busyId` disables only that row's buttons while its request runs, which prevents double-clicks.
- **Updating a signal holding an array:** `bookings.update(list => list.map(b => b.id === updated.id ? updated : b))` builds a **new** array. Signals compare by reference, so pushing into or changing the *same* array wouldn't trigger a re-render.
- `@for ... @empty { — }`: the `@empty` block shows when there's nothing to loop over (finished or cancelled bookings get a dash).
- `<a [href]="'tel:' + phone">` makes the number tappable to call on a phone.
- `.table-wrap { overflow-x: auto }`: on a phone the **table** scrolls sideways inside its box, while the page itself never scrolls sideways.

### `admin/admin-vehicles/`: inline edit
- **One edit form for all vans** plus an `editingId` signal. Only the van being edited shows the form, and the other Edit buttons are disabled. That's simpler than one form per van.
- `editForm.reset({...vehicle})` **loads** a van into the form and also clears the touched/dirty flags from the previous edit.
- **Photo field:** `<input list="known-photos">` + `<datalist>` gives suggestions (the photos already in `public/images/`) while still allowing any text.
- **Live preview:** `toSignal(editForm.controls.photoUrl.valueChanges)` makes the image update while you type the path.
- After saving: the list row is replaced (same immutable-update pattern) and a "✓ Saved" note shows on that card.
- Setting a van to **Under maintenance** immediately makes it unbookable on the public page ("Not bookable right now") and in the API (409).

### Gotcha: a leftover dev server (hit while testing)
- `ng serve` was started again, but **port 4200 was still used by an older `ng serve`**. The new one only printed "Port 4200 is already in use" and quit, and the old one kept serving.
- The old server had recompiled at the exact moment between two file edits (the service already used `BookingFilters`, the model didn't have it yet) and **kept showing that stale error overlay**, even though `ng build` was fine.
- **Lesson:** if the browser shows an error that doesn't match your code, check what's actually running on the port. In PowerShell:
  ```powershell
  Get-NetTCPConnection -LocalPort 4200 -State Listen | Select-Object OwningProcess
  Stop-Process -Id <that number>
  ```
  Or in cmd: `netstat -ano | findstr :4200` then `taskkill /PID <pid> /F`. The same works for 8080 if the backend says the port is in use.

### How Phase 5 was tested
- **API:** each filter alone and combined, plus bad input (`status=FOO`, `to` before `from`).
- **Browser (headless Chrome), 21 checks:**
  - `/admin` → bookings tab; Confirm; Cancel (accepted, and dismissed → nothing changes)
  - status + date filters, the "to before from" message, Clear filters
  - Vans tab: edit form prefilled, other Edit buttons locked, empty rate blocked; saving rate + maintenance updates the database; the public page shows the van as not bookable
  - no page-level sideways scroll at phone width
- Test data was deleted and the edited van restored afterwards.

### Known limitations and ideas for later
- **No login yet:** anyone who opens `/admin` can confirm or cancel bookings. Phase 6 adds Spring Security. Until then, only run it on your own computer.
- `Vehicle.status = BOOKED` is still only set by hand. Availability already comes from booking dates, so consider removing `BOOKED` or setting it automatically.
- Setting a van to *Under maintenance* doesn't touch its **existing** bookings. Filter the Bookings tab by that van to see who needs a call.
- No "add van" or "delete van" buttons (the API supports both). Add them if the fleet changes.
- No pagination. Fine for a 3-van business, but at hundreds of bookings, add `Pageable` to the search.

---

## Phase 6.1: Admin login (Spring Security + JWT) and booking references

### How login works
```
1. POST /api/auth/login {username, password}
2.   → AuthenticationManager checks the password (BCrypt)
3.   ← {token, username, expiresAt}               (a signed JWT, valid 8 hours)
4. Every admin request: Authorization: Bearer <token>
5.   → Spring Security checks the signature + expiry, then the role → allowed, or 401/403
```
- **JWT (JSON Web Token)** = `header.payload.signature`, each part base64. The payload (`sub: admin`, `roles: ["ADMIN"]`, `exp: ...`) is **readable by anyone**: paste a token into jwt.io and see. Never put secrets in it.
- The **signature** is an HMAC-SHA256 of header + payload made with the server's secret key (`JWT_SECRET`). Change one character and the signature no longer matches, so you get a 401. `SecurityTest` checks exactly that.
- **Stateless:** the server stores no sessions. The token itself proves who you are. The downside is that a token can't be "taken back" before it expires; logging out only deletes it in the browser. That's why tokens are short-lived (8 h).

### `config/SecurityConfig.java`
- `SecurityFilterChain` is the list of rules, checked **top to bottom; the first match wins**:
  - **Public:** `GET /api/vehicles/**`, `GET /api/bookings/availability`, `GET /api/bookings/ref/*`, `POST /api/customers`, `POST /api/bookings`, `POST /api/auth/login`
  - **Everything else:** `.anyRequest().hasRole("ADMIN")`. A "deny by default" list is safer than listing what to protect: a new endpoint is protected automatically.
- `csrf.disable()`: CSRF attacks trick the browser into sending its **cookies** to your site. We don't use cookies for login (the token goes in a header the attacker's page can't add), so CSRF protection isn't needed. With cookie sessions it **must** stay on.
- `SessionCreationPolicy.STATELESS`: don't create HTTP sessions.
- `oauth2ResourceServer().jwt()` is Spring's built-in "read and verify the Bearer token" filter. `JwtGrantedAuthoritiesConverter` maps our `roles` claim to `ROLE_ADMIN`, which `hasRole("ADMIN")` checks.
- **CORS moved here** (`corsConfigurationSource` bean) and `WebConfig` was deleted. Spring Security runs *before* Spring MVC, so the preflight `OPTIONS` request must be allowed at the security level, and `Authorization` must be an allowed header.
- **Admin account:** one user, from settings (`ADMIN_USERNAME` / `ADMIN_PASSWORD`). The password is hashed with **BCrypt** at startup: slow on purpose and salted, so even a stolen hash is hard to crack.

### `application.properties`: settings from environment variables
```properties
app.admin.password=${ADMIN_PASSWORD:admin123}
```
`${NAME:default}` reads the environment variable `NAME`, or uses the default. **The defaults are for your computer only.** Before going online, set real values for `ADMIN_PASSWORD` and `JWT_SECRET` (32+ random characters). Anyone who knows the secret can create their own admin tokens. In PowerShell:
```powershell
$env:ADMIN_PASSWORD = "a-long-password"; $env:JWT_SECRET = "at-least-32-random-characters......"; .\mvnw.cmd spring-boot:run
```

### `security/TokenService.java`, `controller/AuthController.java`
- `authenticationManager.authenticate(...)` either returns the logged-in `Authentication` or throws `BadCredentialsException`. `GlobalExceptionHandler` turns that into **401 "Invalid username or password"**: the same message for a wrong username or a wrong password, so attackers can't discover valid usernames.
- `JwsHeader.with(MacAlgorithm.HS256)` must be set explicitly. The encoder otherwise assumes RS256 (public/private key pairs).

### Booking references (`model/BookingReference.java`)
- The Phase 4 privacy problem: `/bookings/5` showed booking 5's name and phone to anyone who guessed the number.
- Now every booking gets a random code like **`VR-7K3P9QXA`** (`@PrePersist`). The customer page is `/bookings/VR-7K3P9QXA`, backed by the public `GET /api/bookings/ref/{reference}`. The plain-id endpoints are admin-only.
- `SecureRandom`, not `Random`: `Random` is predictable if someone sees a few outputs; `SecureRandom` isn't.
- The alphabet leaves out `0/O` and `1/I/L`, so codes are easy to read over the phone. 31⁸ ≈ 850 billion combinations, so guessing isn't practical.
- **Existing rows:** `ddl-auto=update` adds the new column, but old bookings would have it empty. `DataSeeder.backfillBookingReferences()` fills them in at startup. A migration tool (Flyway) would do this as a versioned script.

### Frontend: `auth/`
- **`auth.service.ts`** keeps the session in a signal **and** `localStorage`, so a refresh doesn't log you out. Trade-off: JavaScript on the page can read `localStorage`, which is why we never insert untrusted HTML (Angular escapes `{{ }}` text automatically). `token()` also logs out when `expiresAt` has passed.
- **`auth.interceptor.ts`** (`HttpInterceptorFn`) wraps **every** HttpClient request:
  - adds `Authorization: Bearer ...`, but only for our own API URL, never sending the token to other sites;
  - on a **401** from the API (expired or forged token), logs out and goes to `/login?returnUrl=...`;
  - requests are immutable, so `req.clone({ setHeaders })` creates a modified copy;
  - registered with `provideHttpClient(withInterceptors([authInterceptor]))`.
- **`admin.guard.ts`** (`CanActivateFn`) runs before `/admin` opens. Not logged in → `createUrlTree(['/login'], { queryParams: { returnUrl } })`. **The guard only hides pages; the backend is the real lock.** Anyone can bypass frontend code, but nobody can bypass the server's 401.
- **`login/`** returns to `returnUrl` after logging in, but only if it starts with `/` (an "open redirect" check, so `?returnUrl=https://evil.example` can't send people to another site).
- Header: **Admin** and **Log out** only appear when logged in. Customers see a small "Admin login" link in the footer.

### How it was tested
- **`SecurityTest` (MockMvc, 4 tests):** public endpoints open; admin endpoints 401; wrong password 401; a real token works; a token with a changed signature is refused. In Boot 4, `@AutoConfigureMockMvc` lives in `org.springframework.boot.webmvc.test.autoconfigure`.
- **Browser (16 checks):** redirect to login with returnUrl, wrong password, return after login, reference URL after booking, confirm with token, session survives reload, `/bookings/1` shows nothing, forged token → logged out, Log out.

### Known limitations
- One admin account, set via environment variables. For staff with their own logins, store users in a database table (`UserDetailsService` backed by JPA).
- No limit on login attempts. Add rate limiting (or lock after N failures) before going public.
- Logging out doesn't invalidate an already-copied token until it expires (8 h).

---

## Phase 6.2: Email when a booking is confirmed

When the admin clicks **Confirm**, the customer gets an email with the reference, van, dates and total, if they gave an email address.

### The flow: events, not direct calls
```
BookingService.changeStatus(CONFIRMED)
   └─ publishEvent(new BookingConfirmedEvent(id))      ← "something happened", no idea who listens
         ... transaction commits ...
   NotificationService.onBookingConfirmed(event)       ← @TransactionalEventListener(AFTER_COMMIT) + @Async
         └─ build email → send (or log)
```
- **`ApplicationEventPublisher` / events** decouple the two services: `BookingService` doesn't depend on email at all. Adding an SMS later means one more listener, with no change to the booking code.
- **`@TransactionalEventListener(phase = AFTER_COMMIT)`**: the listener runs only **after the database change is committed**. If saving failed and rolled back, no "your booking is confirmed" email goes out for a booking that isn't confirmed. A plain `@EventListener` would run immediately, *inside* the transaction.
- **`@Async`** (enabled by `@EnableAsync` in `config/AsyncConfig.java`) runs the listener on a background thread (`task-1` in the log). The admin's click returns immediately (measured: 0.03 s) even if the mail server is slow. Exceptions in async code don't reach the caller, so `NotificationService` catches `MailException` and logs it. The booking stays confirmed; only the email failed.

### `notification/NotificationService.java`
- **`Optional<JavaMailSender>`**: Spring Boot only creates a `JavaMailSender` when `spring.mail.host` is set. Asking for an `Optional` means "give it to me if it exists". No mail server → the whole email is **written to the log**, so you can develop without an email account.
- `SimpleMailMessage` is a plain-text email. HTML emails would use `MimeMessageHelper`.
- No email address on the customer → skipped (logged).

### Configuration: environment variables, not code
Spring maps environment variables to properties automatically ("relaxed binding"): `SPRING_MAIL_HOST` becomes `spring.mail.host`. To send real email with **Gmail**:
1. In your Google account, turn on 2-Step Verification, then create an **App Password** (Security → App passwords). Your normal password won't work.
2. Start the backend with:
```powershell
$env:SPRING_MAIL_HOST="smtp.gmail.com"; $env:SPRING_MAIL_PORT="587"
$env:SPRING_MAIL_USERNAME="you@gmail.com"; $env:SPRING_MAIL_PASSWORD="your16charapppassword"
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH="true"; $env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE="true"
$env:MAIL_FROM="you@gmail.com"
.\mvnw.cmd spring-boot:run
```
- ⚠️ **Never commit passwords** to `application.properties`. Git history keeps them forever, even if deleted later. (The database password `crud` in that file is fine for a local-only database, but must become an environment variable before deploying.)
- ⚠️ Don't put `spring.mail.host=${MAIL_HOST:}` in the properties file. An **empty** host still counts as "set", so Spring creates a mail sender that fails on every email.

### How it was tested
- **Unit tests:**
  - `BookingServiceTest`: confirming publishes the event, cancelling doesn't.
  - `NotificationServiceTest`: recipient, sender, subject and body (dates, "3 days", "PHP 10,500.00"); no email address → nothing sent; no mail server → no crash; mail server error → no crash. `ArgumentCaptor` grabs the message passed to the mock so its fields can be checked.
- **Live, without mail configured:** the email text appeared in the backend log after Confirm.
- **Live, with a fake SMTP server** (a small Node script on port 2525, run with `SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=2525`): the real `JavaMailSender` delivered a proper email.

### Ideas for later
- Emails for other moments: "request received" (PENDING) and "cancelled".
- An HTML version with the van photo.
- SMS: same event, a second listener using an SMS provider's API (e.g. Semaphore in the Philippines).
- If emails must never be lost (server restarts mid-send), save them in an "outbox" table and send from there.

---

## Phase 6.3: Reports

**Admin → Reports**: pick a year and see:
- 4 tiles: revenue, bookings, pending, most booked van
- a revenue-per-month column chart, which can switch to a table
- a per-van table

### What counts as what (`dto/ReportResponse.java`)
| Number | Means |
|---|---|
| **Revenue** | total of `CONFIRMED` + `COMPLETED` bookings: money you can count on |
| **Pending** | total of `PENDING` bookings: possible, not yet confirmed |
| **Bookings** | count of bookings that aren't cancelled (cancelled ones are counted separately) |
| **Month** | a booking counts in the month it **starts** (a 30 Jan–2 Feb trip is January) |

Writing these definitions down matters as much as the code: "revenue" means different things to different people.

### Backend: `service/ReportService.java`
- Loads one year of bookings (`findByStartDateBetween`, a **derived query**: Spring writes the SQL from the method name) and groups them with **Java streams**:
  - `filter(...)` keeps some, `count()` counts them;
  - `map(Booking::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add)` adds up money ("start at 0, add each price");
  - `IntStream.rangeClosed(1, 12)` makes **all 12 months**, even empty ones, so the chart never has missing bars.
- `Predicate<Booking>` constants (`EARNING`, `PENDING`, `NOT_CANCELLED`) name each rule once and reuse it everywhere.
- **Streams vs SQL:** for hundreds of bookings, Java is simple and fast enough. For many thousands, let the database do it (`SELECT month, SUM(total_price) ... GROUP BY month`) so you don't load every row.
- `GET /api/reports/yearly?year=2027` is admin-only automatically, because `SecurityConfig` protects everything not listed as public.
- `ReportServiceTest` checks the math with numbers you can verify by hand.

### Frontend: `admin/admin-reports/`
- **`toObservable(this.year)`** turns the year *signal* into a stream, so the same `switchMap` reload pattern works when you click ‹ or ›.
- **Refetch keeps the frame:** while a new year loads, the old numbers stay on screen at half opacity instead of flashing a "Loading…" text.

### Chart design rules used (from a data-visualization checklist)
- **The right form for the job:** single headline numbers are **tiles**, not charts. "Revenue over 12 months" is one series, so it gets **columns in one color** and needs **no legend** (the title says what it is).
- **The color was checked, not guessed:** the app's dark navy `#1f3b57` *failed* a palette validator (too dark and grayish for data marks). The chart uses blue `#2a78d6`, which passes, plus a lighter blue on hover.
- **Thin marks:** columns at most 24px wide, rounded at the top, square at the baseline. Gridlines are 1px, light and solid.
- **Clean axis numbers:** `niceTicks()` picks a step of 1, 2, 2.5 or 5 × a power of ten, then stops at the first tick above the data (max ₱28,000 → step ₱10K → 0, 10K, 20K, 30K). The first version rounded the *top* instead (28K → 50K), which made every bar look short.
- **Label sparingly:** only the best month shows its value. The rest is in the tooltip and table. Text is gray "ink", never the bar color.
- **Tooltip on hover *and* keyboard focus** (`tabindex="0"`, `aria-label` on each column). The whole column is the hover target, not just the thin bar.
- **"Show as table"** gives every exact number without hovering (accessibility, and handy for copying).
- Built with plain HTML/CSS (`height: x%` divs) instead of SVG, so text stays readable on a phone instead of shrinking with the drawing.
- `font-variant-numeric: tabular-nums` on table numbers makes digits equal width, so columns of numbers line up.

### Gotcha, properly explained: the stale error overlay
This happened in Phase 5 and again here. The real cause: making a change with **two separate edits** (remove `niceMax`, then update the code that used it). The dev server compiled in the split second between them, hit "Cannot find name 'niceMax'", and **kept showing that overlay even after the next build succeeded**, even in newly opened tabs.
- **How to tell:** `ng build` passes, but the browser shows an error. The `ng serve` terminal shows "generation failed" followed later by "complete".
- **Fix:** stop `ng serve` (Ctrl+C) and start it again. (In Phase 5 a leftover old server on port 4200 made it worse; check the port as described there.)

### How it was tested
- `ReportServiceTest` (2 tests): totals, months, per van, an empty year.
- 10 sample bookings in 2027 (mixed statuses, one cancelled): the API answered revenue ₱90,500 and pending ₱14,000, matching a hand calculation. Without a token: 401. Year 1990: 400.
- Browser (11 checks): login returns to Reports; tiles; 12 columns / 6 bars (May has only a *pending* booking, so no revenue bar, which is correct); one label; axis ₱0–₱30K; hover tooltip; keyboard-focus tooltip; table view; per-van order; no sideways scroll on a phone.

### Ideas for later
- Compare with last year ("+12% vs 2026"), and occupancy % per van (days booked ÷ days in the year).
- Export to CSV for your accountant.

---

## Phase 6.4: Pickup location map

The booking form has an optional **Pickup location** section:
- an address or landmark field;
- a map where the customer taps to drop a pin, or presses **Use my location** on a phone.

The confirmation page and the admin table show the address and a **Google Maps link** to the pin, for the driver.

### Why OpenStreetMap + Leaflet instead of Google Maps
- The Google Maps *JavaScript API* (an embedded interactive map) needs an API key with **billing** enabled.
- **Leaflet** (an open-source map library) + **OpenStreetMap** tiles is free with no key. The one rule: show the "© OpenStreetMap contributors" credit on the map (Leaflet does).
- Plain Google Maps **links** (`https://www.google.com/maps?q=lat,lng`) need no key at all. They just open Google Maps on the driver's phone, so you get the best of both (`shared/maps.ts`).
- ⚠️ OpenStreetMap's free tile server is for light use. A busy public site should use a tile provider (MapTiler, Stadia, etc.) with its own free tier.

### Backend
- `Booking` gets `pickupAddress`, `pickupLatitude`, `pickupLongitude` (all optional). `ddl-auto=update` added the columns.
- `BookingRequest` validation: `@DecimalMin("-90") @DecimalMax("90")` for latitude, ±180 for longitude. **Cross-field rule** in `BookingService.validatePickup`: latitude and longitude must come **together**. One field's annotations can't see another field, so that check lives in the service.
- Small refactor: `create` and `update` used to copy the same fields twice. Now both call `applyRequest(...)`, one place to change when a field is added (as happened right here).
- The confirmation email includes the pickup address and the map link.

### Frontend: `shared/pickup-map/pickup-map.component.ts`
- **Installing a library:** `npm install leaflet@1.9.4` + `npm install -D @types/leaflet` (TypeScript type definitions, so `L.map(...)` gets autocomplete and type checks).
- **Leaflet's CSS** goes in `angular.json` → `styles`. Component styles are *scoped* to the component's own template, and Leaflet creates its HTML at runtime, so scoped styles would never reach it.
- `allowedCommonJsDependencies: ["leaflet"]`: Leaflet is an older CommonJS package, so Angular warns it can't optimize it fully. Listing it acknowledges that on purpose.
- **Using a non-Angular library inside Angular:**
  - create it in `ngAfterViewInit` (the `<div>` exists only after the view is built) and **destroy it in `ngOnDestroy`** (`map.remove()`), or its listeners leak every time you leave the page;
  - `viewChild.required('mapElement')` gets the `<div>` (a signal-based `@ViewChild`).
- **`NgZone.runOutsideAngular`:** Angular (with zone.js) re-checks the page after *every* browser event. Leaflet produces hundreds (every mouse move over the map). Creating the map outside the zone stops that, and `zone.run(...)` steps back in only when something matters (a pin was placed), so the form updates.
- **`L.circleMarker`** instead of the default pin icon: the default marker needs image files that bundlers often can't find (a famous Leaflet + bundler gotcha, broken-image pins). A circle is drawn as a vector, so no images are needed.
- `output<PickupPoint | null>()` (the signal-era `@Output`) sends the pin to the booking form, which `patchValue`s it into the `pickup` form group.
- **"Use my location"** uses the browser's `navigator.geolocation`. It only works on **https** or `localhost`, and the user must allow it. If refused, a friendly message suggests tapping the map instead.
- Coordinates are rounded to 6 decimals (about 10 cm), more precise than any phone GPS.
- **Accessibility:** clicking a map isn't possible with a keyboard or screen reader, which is why the **address field** exists and both are optional.

### Gotchas hit while building
- **`ClassFormatError: Invalid method Code length 0`** when running the tests. VS Code's Java extension compiles into the **same `target/classes` folder** as Maven. If it compiles while the code has an error (mid-edit), it writes a broken placeholder `.class` file, and Maven thinks it's up to date. Fix: **`.\mvnw.cmd clean test`** (`clean` deletes `target/` and compiles everything fresh).
- In my browser-test scripts, `\s` inside a JavaScript **template string** (`` `...` ``) silently becomes a plain `s` (an unknown escape), so a "remove whitespace" regex deleted every letter s. Test text showed "Van" for "Vans" and "Open in Google Map". It must be written `\\s` inside backticks. The app was fine; the test was wrong.

### How it was tested
- Unit tests: pickup fields stored and trimmed; latitude without longitude → 400; email includes pickup and map link (23 backend tests in total).
- API: half coordinates → 400 "must be given together"; latitude 200 → 400.
- Browser (10 checks): map tiles load with the credit; clicking the map places a pin near Manila; Remove pin; **Use my location** with an emulated GPS position (SM Megamall) → pin there; the booking saves the address; the confirmation links to `google.com/maps?q=14.5849,121.0563`; the admin row shows pickup + link; no sideways scroll on a phone.

---

## Phase 7.1: Customers sign in with Google

Customers must **Sign in with Google** before booking. Their name and email come from Google, so the booking emails always reach a real inbox. The admin keeps the username/password login.

### How it works
```
Browser: Google's button → user signs in at Google → Google gives the page an ID token (a JWT signed by Google)
   → POST /api/auth/google { credential }
Backend: GoogleTokenVerifier checks Google's signature + issuer + audience + expiry + email_verified
   → issues OUR OWN token with role CUSTOMER (subject = Google's user id)
Browser: sends our token with every request, like the admin token
```
- **Why not just trust the browser?** Anything the browser says can be faked with dev tools. Only a token **signed by Google** (checked against Google's public keys at `googleapis.com/oauth2/v3/certs`) proves who the user is.
- **Audience check:** the token must be made for *your* Client ID. Without it, a token someone got from *another* website's Google login would work on yours.
- **Why exchange it for our own token?** Google ID tokens expire in about an hour and carry no roles. Ours carries `roles: ["CUSTOMER"]`, which `SecurityConfig` checks with `hasRole("CUSTOMER")`.
- **Google user id (`sub`), not email, is the key:** emails can change, the id never does. `Customer.googleId` links the record; an older customer row with the same email gets linked on first sign-in.

### Backend changes
- `security/GoogleTokenVerifier.java`: `NimbusJwtDecoder.withJwkSetUri(Google's keys)` plus custom validators for issuer and audience. No Client ID set → 503 "not set up yet".
- `POST /api/auth/google` (public), `POST /api/bookings` (**CUSTOMER only**), `GET /api/bookings/mine` (CUSTOMER), `POST /api/customers` (now admin only).
- `@AuthenticationPrincipal Jwt jwt` in a controller method gives you the verified token of whoever is calling, so `jwt.getSubject()` is the Google id. The customer is **never** taken from the request body, so nobody can book in someone else's name.
- `CustomerBookingRequest` has no `customerId` or name, only `contactNumber`. The customer is created or updated **in the same transaction** as the booking: if the dates turn out to be taken, no half-made customer is left behind. That fixes the Phase 4 limitation.
- **401 vs 403:** 401 means "who are you? (no or invalid token)"; 403 means "I know who you are, but you're not allowed". An admin calling `/api/bookings/mine` gets 403; a customer calling admin pages gets 403.

### Frontend changes
- `index.html` loads Google's script (`accounts.google.com/gsi/client`). `src/google-gsi.d.ts` declares the few functions we use so TypeScript knows them (a `.d.ts` file = types only, no code).
- `auth/google-sign-in-button.component.ts`: `google.accounts.id.initialize({ client_id, callback })` + `renderButton(...)`. Google calls the callback **outside Angular's zone**, so `zone.run(...)` steps back in. The script loads asynchronously, so the component waits for it, gives up after about 10 seconds, and shows a message (ad blockers sometimes block it).
- `AuthService` now has `roles`: `isAdmin()` / `isCustomer()`. One session at a time. Old saved sessions without roles are ignored.
- `customerGuard` sends `/book` and `/my-bookings` to `/signin?returnUrl=...`; after signing in you come back to the van you picked.
- The booking form shows "Booking as Maria Santos · maria@gmail.com" and only asks for a **contact number**.
- New pages: `/signin`, `/my-bookings`. The header shows **My bookings** and **Sign out** for customers.
- On sign out, `google.accounts.id.disableAutoSelect()` stops Google from silently signing the same account back in.

### Setting up Google (one time, about 10 minutes)
1. Go to **console.cloud.google.com** → create a project (e.g. "Van Rental").
2. **APIs & Services → OAuth consent screen**: user type **External**, app name, your email. Add yourself (and testers) as test users while the app is in "Testing".
3. **APIs & Services → Credentials → Create credentials → OAuth client ID** → type **Web application**.
4. **Authorized JavaScript origins:** add `http://localhost:4200` **and** `http://localhost` (Google requires both for local testing). Later add your real https address.
5. Copy the **Client ID** (ends in `.apps.googleusercontent.com`; it's not a secret) into **both**:
   - `frontend/src/environments/environment.ts` → `googleClientId: '...'`
   - the backend: environment variable `GOOGLE_CLIENT_ID` (or the default in `application.properties`)

### How it was tested
- `GoogleTokenVerifierTest` (7): plays "Google" with its own RSA key. A valid token passes; a wrong audience, wrong issuer, expired token, unverified email or garbage are all **rejected**; no Client ID → 503.
- `GoogleSignInTest`: `/api/auth/google` → CUSTOMER token → `/mine` works (`@MockitoBean` replaces the verifier).
- `SecurityTest`: anonymous booking 401, admin on `/mine` 403, customer on admin pages 403.
- Browser (15 checks), using a locally signed customer token (a real Google login can't be automated): redirect to sign-in with returnUrl; Google name and email on the form; booking linked to the Google account; second booking reuses the customer; another customer sees an empty `/mine`; a customer can't open admin; sign out.

---

## Phase 7.2: ₱500 down payment with GCash QR and proof upload

**Customer:** in the booking form's **Down payment** section (`id="bank-acc"`), scan the GCash/InstaPay QR, send ₱500, upload a screenshot, and request the booking. The ₱500 is **part of the total**: "Total ₱10,500 · down payment ₱500 · balance at pickup ₱10,000".

**Admin:** the bookings table has a **Down payment** column ("₱500 · to check"). **View proof** opens the screenshot. **Verify** (money arrived) or **Reject** (it didn't). **Confirm stays disabled until the payment is verified.** After a rejection the customer sees "Please check your down payment" and can upload a new screenshot.

```
SUBMITTED (customer uploaded) ──► VERIFIED (admin) ──► booking can be CONFIRMED
        │
        └──► REJECTED (admin) ──► customer re-uploads ──► SUBMITTED
```

### Backend
- **`Booking.downPayment` + `paymentStatus`.** The amount is a setting (`app.booking.down-payment=500`), never more than the whole price (`min`). `GET /api/bookings/payment-info` gives the frontend the same number, so the page and the server can't disagree.
- **`PaymentProof` entity in its own table.** The image (`byte[]` → PostgreSQL `bytea`) is *not* a column on `bookings`, so listing 100 bookings never loads 100 images. The `@OneToOne` link lives on the proof side (`booking_id`, unique).
- **Storing images in the database vs on disk:** for a few small screenshots, the database is simplest: backups include them, and there are no file paths to manage. For many large files, use disk or cloud storage (S3 etc.) and keep only the path in the database.
- **Multipart upload:** `POST /api/bookings` is now `multipart/form-data` with two parts: `booking` (the JSON) and `paymentProof` (the file). In the controller: `@RequestPart("booking") @Valid CustomerBookingRequest` and `@RequestPart("paymentProof") MultipartFile`.
- **Never trust an upload's name or Content-Type.** Both are chosen by the sender. `ImageType.detect()` reads the **first bytes** ("magic numbers"): JPEG starts `FF D8 FF`, PNG `89 50 4E 47…`, WebP `RIFF….WEBP`. A text file renamed `.png` is rejected. The stored `contentType` comes from the bytes, too.
- **Size limits in two layers:** `spring.servlet.multipart.max-file-size=5MB` (Spring refuses bigger uploads → 413 "too large"), plus a check in `PaymentService`. `server.tomcat.max-swallow-size` lets Tomcat answer politely instead of cutting the connection on a slightly-too-big upload.
- **Order matters in `createForCustomer`:** check the image **before** creating the customer or booking. Everything is one `@Transactional`, so any failure leaves nothing behind.
- `GET /api/bookings/{id}/payment-proof` (admin) returns the raw bytes with the right content type and `Cache-Control: no-store` (private data shouldn't sit in caches).
- **Re-upload** (`POST /api/bookings/ref/{ref}/payment-proof`, CUSTOMER): only the customer whose Google id owns the booking. Anyone else gets **"not found"**, not "forbidden", so nobody can probe which references exist.
- Deleting a booking deletes its proof first (the proof's foreign key points at the booking).

### Frontend
- **`shared/proof-upload/`:** a file input (`accept="image/jpeg,image/png,image/webp"`) with friendly checks (type, 5 MB) and a **preview**. `URL.createObjectURL(file)` lets an `<img>` show a local file before uploading. It must be released with `URL.revokeObjectURL`, or the image stays in memory.
- **Sending a file + JSON together:** `FormData` with `new Blob([JSON.stringify(request)], { type: 'application/json' })` for the JSON part, plus the `File`. **Don't** set `Content-Type` yourself: the browser adds `multipart/form-data; boundary=...`, and the boundary is required.
- **Showing a protected image:** `<img src="/api/.../payment-proof">` can't send our `Authorization` header, so it would get a 401. Instead, `HttpClient.get(url, { responseType: 'blob' })` goes through the auth interceptor, and `createObjectURL(blob)` shows it.
- **Modal dialog:** a backdrop + `role="dialog"` + `aria-modal`; Escape closes it from anywhere (`host: { '(document:keydown.escape)': 'closeProof()' }`).
- **The booking page follows the booking's state:** request received / please check your down payment / confirmed / cancelled. The same page is reopened from My bookings.
- The QR image: `van-img/bank-acc.jpg` was copied to `frontend/public/images/gcash-qr.jpg`.

### How it was tested
- `PaymentServiceTest` (10): JPEG/PNG/WebP accepted by their bytes; a "program named proof.png" rejected; empty, missing and over-5-MB files rejected; path stripped from the file name; verify/reject rules; owner-only re-upload; a verified payment can't be replaced.
- `BookingServiceTest`: a booking stores ₱500 down and the ₱10,000 balance; an invalid proof stops *before* anything is saved; Confirm is refused until VERIFIED.
- `SecurityTest`: proof images and payment review are admin-only; payment-info is public.
- Browser (19 checks), the full cycle: QR shown → no proof = asked for one → fake image rejected by the server → real screenshot + preview → "being checked" → admin sees "to check", Confirm disabled → proof image in the dialog → reject → customer sees the warning and re-uploads → admin verifies → Confirm works → customer sees "Booking confirmed · received ✓". No sideways scroll on a phone.

### Known limitations
- Checking that the money actually arrived is manual (your GCash history). Automatic confirmation would need a payment gateway (PayMongo, Xendit), which have fees and require business verification.
- If two people pay for the same dates at the same moment, only one booking is created: the second gets "already booked" **after** paying. That's rare, but refunds are manual.

---

## Before going online (deployment checklist)
Everything so far runs on your computer. Before deploying (Render, Railway, a VPS):
1. **Secrets out of the code:** the database password, `ADMIN_PASSWORD` and `JWT_SECRET` become environment variables on the server. Never use the `admin123` / dev secret defaults online.
2. **`ddl-auto=update` → Flyway migrations**, so database changes are versioned scripts, not guesses at startup.
3. **HTTPS** (most hosts do this for you). Required for login tokens and for "Use my location".
4. `CORS_ALLOWED_ORIGINS` set to your real frontend address; `environment.ts` `apiUrl` pointing to the real backend (a separate `environment.prod.ts`).
5. Rate-limit `/api/auth/login`; set up real SMTP (6.2); consider a paid map-tile provider if traffic grows.
