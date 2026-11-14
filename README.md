# Jet Set Go

Jet Set Go is a full-stack airline booking project built with Java 21 / Spring Boot, React / TypeScript, MongoDB Atlas, and a Razorpay-compatible checkout. It includes flight search, airport autocomplete, seat reservation, booking management, and either a clearly labelled payment simulator or Razorpay Test Mode.

## What works

- Customer registration and sign-in with BCrypt-hashed passwords and signed JWTs.
- Airport autocomplete against curated IATA airport records, with keyboard selection, airport swapping, one-way date search, and sort by departure, fare, or duration.
- Four internal demonstration flight schedules, visibly identified as demo data and never presented as live inventory/status.
- Per-seat atomic reservation in MongoDB, plus an idempotency key and unique index to protect booking creation retries.
- Passenger details, internal Jet Set Go booking references, pending bookings, customer-scoped booking history, and printable itineraries.
- A no-credentials educational payment simulator (no payment details collected) and optional Razorpay Test Mode. Razorpay callbacks are signature-checked and the API confirms captured amount/currency before confirmation.
- Printable demo itinerary and cancellation. Demo refund state is recorded separately; Razorpay Test Mode cancellations request a refund before freeing the seat.
- Role-protected admin API/dashboard for demo flight schedules, statuses, users, and booking summaries.
- Responsive React pages for home, search, flight/seat selection, login/register, checkout, bookings, admin tools, and printable itinerary.
- GitHub Actions build checks, Docker image for the Java API, and Render/Vercel configuration.

This is a working academic demo, not a production airline reservation platform. It currently does not connect to a live flight-status/inventory API and does not include round trips, online check-in, or customer profile editing. A generated booking reference is internal to Jet Set Go, not a real airline PNR or issued ticket.

## Run locally

Requirements: Java 21, Maven 3.9+, Node.js 22+, npm, and a MongoDB Atlas cluster. This project uses Atlas; it does not start a local MongoDB server.

1. In MongoDB Atlas, create a database user and cluster. You will need the Java driver connection string the first time you start the backend. The launcher prompts for it with hidden input, so it will not be echoed into the terminal.

2. From the repository root, start the API and seed the airport catalog/demo schedules into empty Atlas collections:

   ```sh
   ./run-backend.sh
   ```

   The prompt accepts your Atlas connection string without displaying it. Seeding only fills empty airport/flight collections; it does not drop or replace records. The API is at `http://localhost:8080`. Add `DEMO_ADMIN_EMAIL` and `DEMO_ADMIN_PASSWORD` to the process environment before startup to create an admin login; use a strong unique password.

   To use Razorpay **Test Mode** instead, set `PAYMENT_MODE=RAZORPAY_TEST`, `RAZORPAY_KEY_ID`, and `RAZORPAY_KEY_SECRET` on the backend only. Never use live credentials for this demo.

4. Start the frontend in another terminal:

   ```sh
   cd frontend
   npm ci
   npm run dev
   ```

   Open `http://localhost:5173`. `VITE_API_URL` defaults to `http://localhost:8080`.

5. Create a customer account in the UI, search an airport by city/name/IATA code, select a sample flight and seat, and complete the labelled demo checkout. For Razorpay Test Mode, use Razorpay's test payment details. The secret stays server-side.

## Environment variables

| Variable | Used by | Purpose |
|---|---|---|
| `MONGODB_URI` | API | Required MongoDB Atlas connection string; set in the backend process environment |
| `JWT_SECRET` | API | Random signing key; use at least 32 bytes and set a unique production value |
| `JWT_EXPIRATION_HOURS` | API | JWT lifetime; defaults to 24 |
| `CORS_ORIGINS` | API | Comma-separated exact frontend origins, e.g. `https://your-site.vercel.app` |
| `SEED_DEMO_DATA` | API | Set to `true` to seed airport and flight demo records only into empty collections |
| `PAYMENT_MODE` | API | `DEMO` (default; no real money) or `RAZORPAY_TEST` |
| `RAZORPAY_KEY_ID` | API | Razorpay test key ID; required only in `RAZORPAY_TEST` mode |
| `RAZORPAY_KEY_SECRET` | API | Razorpay test secret; required only in `RAZORPAY_TEST` mode; never expose to browser |
| `VITE_API_URL` | Frontend build | Base URL of the deployed API, without a trailing slash |
| `DEMO_ADMIN_EMAIL`, `DEMO_ADMIN_PASSWORD` | API | Optional; creates an admin if both are set and the email does not exist. Use a unique strong password. |

`.env.example` is a placeholder reference only; Spring does not automatically load it. Set backend variables in your shell/IDE or hosting dashboard. Never commit `.env` files, Atlas credentials, JWT secrets, or Razorpay secrets.

## GitHub, Render, and Vercel setup

1. Create a **private** GitHub repository, add it as this checkout's `origin`, then push the branch. Keep `main` protected if this is shared coursework. `.github/workflows/ci.yml` runs Maven tests and the frontend production build on pushes and pull requests.
2. In Render, choose **New → Blueprint** and select that GitHub repository. The root `render.yaml` defines the Docker API and a Render static-site option. For the API, supply `MONGODB_URI` and `CORS_ORIGINS`; `PAYMENT_MODE` defaults to safe `DEMO`. Render generates `JWT_SECRET`. Add `DEMO_ADMIN_EMAIL` and `DEMO_ADMIN_PASSWORD` in the Render dashboard if you want an admin login. For Razorpay Test Mode, change `PAYMENT_MODE` and add test keys in Render's environment settings. Do not put secret values in `render.yaml`.
3. In Vercel, import the same GitHub repository and set the project **Root Directory** to `frontend`. Add `VITE_API_URL` as the Render API's base URL, then redeploy (Vite variables are embedded at build time).
4. Set the API's `CORS_ORIGINS` to the exact Vercel production URL (and any preview URL you intentionally use). If you use Render's optional static frontend instead, set that service's `VITE_API_URL` and allow its URL in `CORS_ORIGINS`.
5. In MongoDB Atlas, create a database/user and configure Network Access for the Render service. Avoid `0.0.0.0/0` for production; if your Render plan has changing egress IPs, use a private networking option or a restricted allowlist compatible with your plan.
6. To seed a new Atlas database, temporarily set `SEED_DEMO_DATA=true` in Render and deploy once; after the four demo routes appear, remove the flag. In Razorpay, create test keys and configure allowed checkout origins if your account requires it. Do not enable live payments.

The GitHub repository URL, Render service URL, Vercel domain, Atlas URI, and Razorpay keys are account-specific. They cannot be inferred from this workspace; add them through the respective dashboards and do not paste secret values into chat or source control.

## API outline

- `POST /api/auth/register`, `POST /api/auth/login`
- `GET /api/airports?q=del` (seed the Atlas airport collection first)
- `GET /api/flights?from=DEL&to=BOM&date=YYYY-MM-DD&sort=price`, `GET /api/flights/{id}`, `GET /api/flights/{id}/seats`
- Authenticated: `POST /api/bookings` (requires `Idempotency-Key`), `GET /api/bookings`, `GET /api/bookings/{id}`, `POST /api/bookings/{id}/cancel`
- Authenticated: `POST /api/bookings/{id}/payments/order`, Razorpay `POST /api/bookings/{id}/payments/verify`, or demo `POST /api/bookings/{id}/payments/demo/complete`
- Admin-only: `/api/admin/summary`, `/api/admin/flights`, `/api/admin/bookings`, `/api/admin/users`
- `GET /api/health`

Passenger-facing amounts are INR and stored as `BigDecimal`. Flight records with `dataSource=DEMO` are sample schedules, not live offers or status. Seat reservation uses an atomic MongoDB `findAndModify`; Razorpay payment confirmation is server-verified, while simulator attempts are stored separately in `demo_payments`.

## Tests and design notes

```sh
mvn test
cd frontend && npm ci && npm run build
```

Focused backend tests cover payment-signature validation, demo payment success/failure, booking state transitions, and date/airport filters. The UML sources in [`docs/`](./docs) describe the implemented API/domain flow; render them with PlantUML to view the diagrams.

The layered monolith separates REST controllers, services/use cases, MongoDB repositories/documents, and security configuration. `Booking` has explicit lifecycle states; `BookingService` owns reservation/cancellation rules; `RazorpayService` isolates the external payment boundary. MongoDB repositories provide the persistence boundary, while a unique idempotency index and atomic seat update protect the critical booking path.

## Deployment and safety notes

- Render free services may sleep and have limited resources; use a paid service and managed database for a real deployment.
- Atlas and Razorpay account setup, billing, network rules, and production compliance remain the project owner's responsibility.
- `MONGODB_URI` is required; the application does not connect to localhost MongoDB by default. The default JWT key is only a local-development fallback. Always set `JWT_SECRET` in deployment.
- This project collects passenger name and email only. Do not collect or store card details; Razorpay Checkout handles payment credentials.
- A refund is an external side effect. For production, add Razorpay webhooks and a durable reconciliation process for network failures between refund creation and booking persistence.
