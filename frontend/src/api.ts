import type { AdminSummary, Airport, Booking, DemoPaymentResult, Flight, Order, RouteTemplate, Session, UserSummary } from "./types";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const SESSION_KEY = "jetsetgo-session";

export function getSession(): Session | null {
  const raw = localStorage.getItem(SESSION_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Session;
  } catch {
    localStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export function saveSession(session: Session | null) {
  if (session) localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  else localStorage.removeItem(SESSION_KEY);
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const session = getSession();
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(session ? { Authorization: `Bearer ${session.token}` } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.detail ?? body?.message ?? `Request failed (${response.status})`);
  }
  const body = await response.text();
  return (body ? JSON.parse(body) : undefined) as T;
}

export const api = {
  airports: (query = "", countryCode?: string) => {
    const params = new URLSearchParams();
    if (query) params.set("q", query);
    if (countryCode) params.set("countryCode", countryCode);
    return request<Airport[]>(`/api/airports?${params}`);
  },
  airportCountries: () => request<string[]>("/api/airports/countries"),
  flights: (from?: string, to?: string, date?: string, sort = "departure", mode?: string, countryCode?: string) => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    if (date) params.set("date", date);
    if (sort) params.set("sort", sort);
    if (mode) params.set("mode", mode);
    if (countryCode) params.set("countryCode", countryCode);
    return request<Flight[]>(`/api/flights?${params}`);
  },
  featuredFlights: () => request<Flight[]>("/api/flights/featured"),
  flight: (id: string) => request<Flight>(`/api/flights/${id}`),
  seats: (id: string, travelClass = "ECONOMY") =>
    request<string[]>(`/api/flights/${id}/seats?travelClass=${encodeURIComponent(travelClass)}`),
  login: (email: string, password: string) =>
    request<Session>("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) }),
  register: (name: string, email: string, password: string) =>
    request<Session>("/api/auth/register", { method: "POST", body: JSON.stringify({ name, email, password }) }),
  createBooking: (payload: {
    flightId: string;
    seatNumber: string;
    passengerName: string;
    passengerEmail: string;
    returnFlightId?: string;
    seatNumbers: string[];
    returnSeatNumbers: string[];
    passengers: Array<{ name: string; nationality?: string; passportExpiryDate?: string }>;
    travelClass: string;
  }, idempotencyKey: string) =>
    request<Booking>("/api/bookings", {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(payload),
    }),
  bookings: () => request<Booking[]>("/api/bookings"),
  cancelBooking: (id: string) => request<Booking>(`/api/bookings/${id}/cancel`, { method: "POST" }),
  order: (id: string) => request<Order>(`/api/bookings/${id}/payments/order`, { method: "POST" }),
  verifyPayment: (id: string, payload: { razorpayOrderId: string; razorpayPaymentId: string; razorpaySignature: string }) =>
    request<Booking>(`/api/bookings/${id}/payments/verify`, { method: "POST", body: JSON.stringify(payload) }),
  demoPayment: (id: string, method: string, succeed: boolean, idempotencyKey: string) =>
    request<DemoPaymentResult>(`/api/bookings/${id}/payments/demo/complete`, {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ method, succeed }),
    }),
  adminSummary: () => request<AdminSummary>("/api/admin/summary"),
  adminFlights: (filters: { from?: string; to?: string; date?: string; flightNumber?: string } = {}) => {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); });
    return request<Flight[]>(`/api/admin/flights?${params}`);
  },
  adminRoutes: () => request<RouteTemplate[]>("/api/admin/routes"),
  createRoute: (route: Omit<RouteTemplate, "id" | "active">) =>
    request<RouteTemplate>("/api/admin/routes", { method: "POST", body: JSON.stringify(route) }),
  updateRoute: (id: string, route: Omit<RouteTemplate, "id" | "active">) =>
    request<RouteTemplate>(`/api/admin/routes/${id}`, { method: "PUT", body: JSON.stringify(route) }),
  setRouteActive: (id: string, active: boolean) =>
    request<RouteTemplate>(`/api/admin/routes/${id}/active`, {
      method: "PATCH", body: JSON.stringify({ active }),
    }),
  adminBookings: (status?: string) =>
    request<Booking[]>(`/api/admin/bookings${status ? `?status=${encodeURIComponent(status)}` : ""}`),
  cancelAdminBooking: (id: string) =>
    request<Booking>(`/api/admin/bookings/${id}/cancel`, { method: "POST" }),
  adminUsers: () => request<UserSummary[]>("/api/admin/users"),
  createFlight: (flight: Record<string, unknown>) =>
    request<Flight>("/api/admin/flights", { method: "POST", body: JSON.stringify(flight) }),
  updateFlight: (id: string, flight: Record<string, unknown>) =>
    request<Flight>(`/api/admin/flights/${id}`, { method: "PUT", body: JSON.stringify(flight) }),
  updateFlightStatus: (id: string, status: string) =>
    request<Flight>(`/api/admin/flights/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) }),
  deleteFlight: (id: string) =>
    request<void>(`/api/admin/flights/${id}`, { method: "DELETE" }),
};
