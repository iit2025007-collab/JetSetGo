# Jet Set Go

Jet Set Go is a full-stack airline booking project built with Java 21 / Spring Boot, React / TypeScript, MongoDB, and Razorpay Checkout. It includes a real booking path from flight search through seat reservation and payment verification, and is configured for GitHub CI, Render, and Vercel.

## What works

- Customer registration and sign-in with BCrypt-hashed passwords and signed JWTs.
- Flight search by origin/destination airport code; sample routes are seeded automatically when the flights collection is empty.
- Per-seat atomic reservation in MongoDB, plus an idempotency key and unique index to protect booking creation retries.
- Passenger details, PNR generation, pending bookings, and customer-scoped booking history.
- Razorpay test-mode order creation and Checkout. The API verifies the HMAC signature and confirms the payment is captured for the expected amount and currency before confirming a ticket.
- Printable e-ticket and cancellation. Confirmed cancellations request a full Razorpay refund before freeing the seat.
- Responsive React pages for home, search, flight/seat selection, login/register, checkout, bookings, and printable ticket.
- GitHub Actions build checks, Docker build for the API, local MongoDB Compose service, and Render/Vercel configuration.

This is a working academic starter, not a production airline reservation platform. It does not yet include round trips, online check-in/boarding passes, administrator flight-management screens, fare classes, or a full audit/refund-reconciliation workflow. Run payments in Razorpay test mode until you have completed security, compliance, and operational review.

## Run locally

Requirements: Java 21, Maven 3.9+, Node.js 22+, npm, and Docker (for the local MongoDB option).

1. Start MongoDB:

   ```sh
   docker compose up -d mongodb
   ```

   You can use MongoDB Atlas instead by setting `MONGODB_URI` to the connection string from your Atlas cluster. Create a database user with only the permissions the app needs and allow the deployed API's network access in Atlas.

2. Start the API from the repository root:

   ```sh
   mvn spring-boot:run
   ```

   The API is at `http://localhost:8080`; health check: `http://localhost:8080/api/health`. MongoDB defaults to `mongodb://localhost:27017/jetsetgo`. For local Razorpay Checkout, set test credentials in the process environment:

   ```sh
   RAZORPAY_KEY_ID=rzp_test_... RAZORPAY_KEY_SECRET=... mvn spring-boot:run
   ```

3. Start the frontend in another terminal:

   ```sh
   cd frontend
   npm ci
   npm run dev
   ```

   Open `http://localhost:5173`. `VITE_API_URL` defaults to `http://localhost:8080`.

4. Create a customer account in the UI, select a flight and seat, and complete payment using Razorpay test-mode payment details. Razorpay keys are only used by the API for order creation/verification; the key ID is sent to Checkout, while the key secret stays server-side.

## Environment variables

| Variable | Used by | Purpose |
|---|---|---|
| `MONGODB_URI` | API | Atlas or local MongoDB connection string |
| `JWT_SECRET` | API | Random signing key; use at least 32 bytes and set a unique production value |
| `JWT_EXPIRATION_HOURS` | API | JWT lifetime; defaults to 24 |
| `CORS_ORIGINS` | API | Comma-separated exact frontend origins, e.g. `https://your-site.vercel.app` |
| `RAZORPAY_KEY_ID` | API | Razorpay test key ID |
| `RAZORPAY_KEY_SECRET` | API | Razorpay test secret; never expose this to the browser |
| `VITE_API_URL` | Frontend build | Base URL of the deployed API, without a trailing slash |
| `DEMO_ADMIN_EMAIL`, `DEMO_ADMIN_PASSWORD` | API | Optional; seeds an admin account if both are set and the email does not exist. Use a unique strong password. |

`.env.example` is a reference only; Spring does not automatically load it. Set environment variables in your shell or hosting dashboard. Never commit `.env` files, Atlas credentials, JWT secrets, or Razorpay secrets.

## GitHub, Render, and Vercel setup

1. Create a **private** GitHub repository, add it as this checkout's `origin`, then push the branch. Keep `main` protected if this is shared coursework. `.github/workflows/ci.yml` runs Maven tests and the frontend production build on pushes and pull requests.
2. In Render, choose **New → Blueprint** and select that GitHub repository. The root `render.yaml` defines the Docker API and a Render static-site option. For the API, supply `MONGODB_URI`, `CORS_ORIGINS`, and Razorpay **test** keys in Render's environment settings. Render generates `JWT_SECRET`. Do not put secret values in `render.yaml`.
3. In Vercel, import the same GitHub repository and set the project **Root Directory** to `frontend`. Add `VITE_API_URL` as the Render API's base URL, then redeploy (Vite variables are embedded at build time).
4. Set the API's `CORS_ORIGINS` to the exact Vercel production URL (and any preview URL you intentionally use). If you use Render's optional static frontend instead, set that service's `VITE_API_URL` and allow its URL in `CORS_ORIGINS`.
5. In MongoDB Atlas, create a database/user and configure Network Access for the Render service. Avoid `0.0.0.0/0` for production; if your Render plan has changing egress IPs, use a private networking option or a restricted allowlist compatible with your plan.
6. In Razorpay, create test keys and configure allowed checkout origins if your account requires it. Confirm the full test flow before switching any credentials to live mode.

The GitHub repository URL, Render service URL, Vercel domain, Atlas URI, and Razorpay keys are account-specific. They cannot be inferred from this workspace; add them through the respective dashboards and do not paste secret values into chat or source control.

## API outline

- `POST /api/auth/register`, `POST /api/auth/login`
- `GET /api/flights?from=DEL&to=BOM`, `GET /api/flights/{id}`, `GET /api/flights/{id}/seats`
- Authenticated: `POST /api/bookings` (requires `Idempotency-Key`), `GET /api/bookings`, `GET /api/bookings/{id}`, `POST /api/bookings/{id}/cancel`
- Authenticated: `POST /api/bookings/{id}/payments/order`, `POST /api/bookings/{id}/payments/verify`
- `GET /api/health`

Passenger-facing amounts are INR and stored as `BigDecimal`. Seat reservation uses an atomic MongoDB `findAndModify`; payment confirmation is server-verified. A booking is not confirmed just because the browser reports checkout success.

## Tests and design notes

```sh
mvn test
cd frontend && npm ci && npm run build
```

Focused backend tests cover payment-signature validation, booking state transitions, and one-sided flight filters. The UML sources in [`docs/`](./docs) describe the implemented API/domain flow; render them with PlantUML to view the diagrams.

The layered monolith separates REST controllers, services/use cases, MongoDB repositories/documents, and security configuration. `Booking` has explicit lifecycle states; `BookingService` owns reservation/cancellation rules; `RazorpayService` isolates the external payment boundary. MongoDB repositories provide the persistence boundary, while a unique idempotency index and atomic seat update protect the critical booking path.

## Deployment and safety notes

- Render free services may sleep and have limited resources; use a paid service and managed database for a real deployment.
- Atlas and Razorpay account setup, billing, network rules, and production compliance remain the project owner's responsibility.
- The default JWT key is only a local-development fallback. Always set `JWT_SECRET` in deployment.
- This project collects passenger name and email only. Do not collect or store card details; Razorpay Checkout handles payment credentials.
- A refund is an external side effect. For production, add Razorpay webhooks and a durable reconciliation process for network failures between refund creation and booking persistence.
