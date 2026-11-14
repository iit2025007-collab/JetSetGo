import type { Booking, Flight, Order, Session } from "./types";

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
  return response.json() as Promise<T>;
}

export const api = {
  flights: (from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    return request<Flight[]>(`/api/flights?${params}`);
  },
  flight: (id: string) => request<Flight>(`/api/flights/${id}`),
  seats: (id: string) => request<string[]>(`/api/flights/${id}/seats`),
  login: (email: string, password: string) =>
    request<Session>("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) }),
  register: (name: string, email: string, password: string) =>
    request<Session>("/api/auth/register", { method: "POST", body: JSON.stringify({ name, email, password }) }),
  createBooking: (payload: { flightId: string; seatNumber: string; passengerName: string; passengerEmail: string }, idempotencyKey: string) =>
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
};
