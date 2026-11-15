# OOM design and viva notes

## Object-oriented design in this project

- **Encapsulation:** `BookingService` owns booking validation and lifecycle rules. Seat hold/release operations are isolated in `SeatInventoryService`; persistence details stay behind MongoDB repositories.
- **Abstraction:** `PaymentProcessor` describes payment operations without exposing the checkout provider to booking callers. `FlightScheduleGenerator` separates schedule generation from route-template administration. `FarePolicy` defines the fare calculation contract.
- **Polymorphism:** `FarePolicyResolver` selects a fare-policy implementation by travel class. `PaymentProcessorFacade` implements `PaymentProcessor` and delegates between the demo and Razorpay test payment services.
- **Inheritance:** `AbstractFarePolicy` shares common fare calculation behavior; economy, premium economy, business, and first-class policies supply their own multipliers.
- **Composition:** `BookingService` composes repositories, a payment processor, seat inventory, and fare-policy resolution. `RouteTemplateService` composes route-template persistence and the schedule-generator abstraction.

## Core design decisions

1. A route template is a recurring rule, not a flight instance. `RollingFlightScheduleGenerator` creates a 120-day rolling set of dated `Flight` documents and extends it daily.
2. The stable flight key is `templateId:departureDate`. MongoDB upserts use `$setOnInsert`, making repeated generation idempotent and preserving changed flight status or inventory.
3. IANA origin/destination timezones are used to convert local departure time and elapsed duration to instants, including overnight and cross-timezone routes.
4. A seat is held with one atomic MongoDB `findAndModify`. The reservation ID ensures cleanup only releases seats owned by that booking.
5. A pending booking hold expires after 15 minutes. Expiry and payment confirmation compete through a status transition, preventing an expired hold from being confirmed after its seats are released.
6. Travel-class pricing uses a strategy contract rather than a switch embedded in booking logic.
7. Admin edits preserve flights with booking history or active seat reservations. Historical data is not deleted when a route is disabled.

## Sample viva questions

**Why use `FlightScheduleGenerator` as an interface?**
It lets route administration depend on a schedule-generation contract instead of a specific algorithm. Another generator can be substituted without changing the route-template API.

**How do you prevent duplicate flights when the scheduler runs repeatedly?**
Each route/date pair has a deterministic ID, and MongoDB performs an upsert that only sets fields on insert. Re-running generation cannot create another record or overwrite existing status.

**How do you prevent two customers from booking the same seat?**
The seat update matches only an available seat and atomically marks it unavailable with a reservation ID. Only one concurrent update can match that state.

**Why does the project use fare strategies?**
Each travel class implements the same `FarePolicy` contract. The resolver selects a policy, so class-specific pricing can change without growing booking-service conditionals.

**What is the difference between a route template and a flight?**
A route template stores recurring schedule configuration. A flight is a dated, searchable instance generated from that template.

**Is this production airline inventory?**
No. The generated schedules, fares, seats, and booking references are marked as demo data. Real inventory requires a contracted airline/GDS provider and operational integration.

## Diagrams

- [Architecture](./architecture.puml)
- [Booking/payment sequence](./booking-flow.puml)
- [Recurring schedule generation sequence](./route-schedule-sequence.puml)
- [Booking lifecycle](./booking-lifecycle.puml)
