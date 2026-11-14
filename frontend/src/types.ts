export type User = { id: string; name: string; email: string; role: "CUSTOMER" | "ADMIN" };
export type Session = { token: string; user: User };
export type Airport = { code: string; city: string; name: string; country: string; timeZone: string };
export type Flight = {
  id: string;
  airline: string;
  flightNumber: string;
  originCode: string;
  originCity: string;
  destinationCode: string;
  destinationCity: string;
  departure: string;
  arrival: string;
  price: number;
  currency: string;
  capacity: number;
  status: string;
  dataSource: "DEMO" | "LIVE";
};
export type Booking = {
  id: string;
  pnr: string;
  flightId: string;
  seatNumber: string;
  passengerName: string;
  passengerEmail: string;
  amount: number;
  currency: string;
  status: "PENDING_PAYMENT" | "PAYMENT_PROCESSING" | "CONFIRMED" | "CANCELLING" | "CANCELLED";
  createdAt: string;
};
export type Order = { orderId: string; keyId: string; amount: number; currency: string; demoMode: boolean };
export type DemoPaymentResult = {
  booking: Booking;
  reference: string;
  status: "SUCCEEDED" | "FAILED";
};
export type AdminSummary = {
  totalFlights: number;
  totalBookings: number;
  registeredUsers: number;
  confirmedRevenue: number;
  cancelledBookings: number;
};
export type UserSummary = { id: string; name: string; email: string; role: User["role"] };
