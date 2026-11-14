import { useEffect, useMemo, useState } from "react";
import {
  ArrowDownUp, ArrowRight, ArrowUpRight, Check, CircleHelp, Clock3,
  Globe2, LockKeyhole, LogOut, Menu, Plane, Search, ShieldCheck, Sparkles,
  Ticket, UserRound, X,
} from "lucide-react";
import { Link, Navigate, Route, Routes, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api, getSession, saveSession } from "./api";
import type { Booking, Flight, Order, Session } from "./types";

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => { open: () => void };
  }
}

const money = (amount: number, currency = "INR") =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency, maximumFractionDigits: 0 }).format(amount);
const clock = (value: string) => new Date(value).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });
const day = (value: string) => new Date(value).toLocaleDateString("en-IN", { weekday: "short", day: "numeric", month: "short" });

export default function App() {
  const [session, setSession] = useState<Session | null>(() => getSession());
  const [mobileOpen, setMobileOpen] = useState(false);
  const updateSession = (next: Session | null) => {
    saveSession(next);
    setSession(next);
  };
  return (
    <div className="app-shell">
      <header className="site-header">
        <Link to="/" className="brand" aria-label="Jet Set Go home" onClick={() => setMobileOpen(false)}>
          <span className="brand-mark"><Plane size={19} fill="currentColor" /></span>
          <span>jet set <b>go</b><small>TRAVEL, BEAUTIFULLY</small></span>
        </Link>
        <button className="mobile-toggle icon-button" onClick={() => setMobileOpen(!mobileOpen)} aria-label="Toggle menu">
          {mobileOpen ? <X /> : <Menu />}
        </button>
        <nav className={mobileOpen ? "main-nav is-open" : "main-nav"}>
          <Link to="/flights" onClick={() => setMobileOpen(false)}>Explore flights</Link>
          <Link to="/bookings" onClick={() => setMobileOpen(false)}>My trips</Link>
          <a href="#why-us" onClick={() => setMobileOpen(false)}>Why Jet Set Go</a>
          {session ? (
            <button className="nav-account" onClick={() => updateSession(null)}>
              <span className="avatar">{session.user.name.slice(0, 1).toUpperCase()}</span>
              <span>{session.user.name.split(" ")[0]}</span><LogOut size={15} />
            </button>
          ) : <Link className="nav-login" to="/login" onClick={() => setMobileOpen(false)}><UserRound size={17} /> Log in</Link>}
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/flights" element={<FlightsPage />} />
          <Route path="/flight/:id" element={<FlightPage session={session} />} />
          <Route path="/login" element={session ? <Navigate to="/bookings" replace /> : <AuthPage onSession={updateSession} />} />
          <Route path="/bookings" element={session ? <BookingsPage /> : <LoginPrompt />} />
          <Route path="/checkout/:id" element={session ? <CheckoutPage /> : <LoginPrompt />} />
          <Route path="/ticket/:id" element={session ? <TicketPage /> : <LoginPrompt />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
      <Footer />
    </div>
  );
}

function HomePage() {
  const navigate = useNavigate();
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const swap = () => { setFrom(to); setTo(from); };
  return (
    <>
      <section className="hero">
        <div className="hero-grid">
          <div className="hero-copy">
            <div className="eyebrow"><span className="eyebrow-line" /> YOUR NEXT CHAPTER STARTS HERE</div>
            <h1>The world is<br />closer than <em>ever.</em></h1>
            <p>Find the flight that feels right. Thoughtfully chosen routes, transparent fares, and a smoother way to get there.</p>
            <div className="hero-proof"><span><ShieldCheck size={16} /> Secure booking</span><span><Sparkles size={16} /> Curated journeys</span></div>
          </div>
          <div className="hero-stamp"><Plane size={22} /><span>MADE FOR<br />THE WAY YOU GO</span></div>
        </div>
        <div className="search-panel">
          <div className="search-label"><span>01</span> PLAN YOUR JOURNEY</div>
          <form className="search-form" onSubmit={(event) => {
            event.preventDefault();
            const params = new URLSearchParams();
            if (from) params.set("from", from.toUpperCase());
            if (to) params.set("to", to.toUpperCase());
            navigate(`/flights?${params}`);
          }}>
            <label className="search-field"><span>FROM</span><input value={from} onChange={(e) => setFrom(e.target.value)} placeholder="City or airport" aria-label="Origin airport code" maxLength={3} /><small>e.g. DEL</small></label>
            <button type="button" className="swap-button" onClick={swap} aria-label="Swap origin and destination"><ArrowDownUp size={17} /></button>
            <label className="search-field"><span>TO</span><input value={to} onChange={(e) => setTo(e.target.value)} placeholder="Where to?" aria-label="Destination airport code" maxLength={3} /><small>e.g. BOM</small></label>
            <button className="search-submit" type="submit"><Search size={18} /><span>Find flights</span><ArrowRight size={17} /></button>
          </form>
          <div className="search-foot"><span><LockKeyhole size={13} /> Your journey, your way</span><span>Start somewhere wonderful</span></div>
        </div>
        <div className="hero-orbit orbit-one" /><div className="hero-orbit orbit-two" />
      </section>
      <section className="intro-section" id="why-us">
        <div><span className="eyebrow muted"><span className="eyebrow-line" /> A LITTLE MORE HUMAN</span><h2>Travel should feel<br />like <em>the beginning.</em></h2></div>
        <div className="intro-right"><p>From the first search to the final boarding call, we make the details feel easy—so you can focus on the moments waiting at the other end.</p><Link to="/flights" className="text-link">Explore all flights <ArrowUpRight size={16} /></Link></div>
      </section>
      <section className="values-grid">
        <article className="value-card value-blue"><span className="value-icon"><Globe2 /></span><span className="value-number">01 / DISCOVER</span><h3>Good routes.<br />Better stories.</h3><p>Thoughtful connections between the places that matter to you.</p><Link to="/flights">Find your route <ArrowRight size={16} /></Link></article>
        <article className="value-card value-sand"><span className="value-icon"><Ticket /></span><span className="value-number">02 / BOOK</span><h3>Simple from<br />the start.</h3><p>Clear pricing and a booking experience that respects your time.</p><Link to="/flights">See available seats <ArrowRight size={16} /></Link></article>
        <article className="value-card value-green"><span className="value-icon"><CircleHelp /></span><span className="value-number">03 / GO</span><h3>Here for<br />the whole trip.</h3><p>Your itinerary and ticket are right where you need them.</p><Link to="/bookings">View my trips <ArrowRight size={16} /></Link></article>
      </section>
    </>
  );
}

function FlightsPage() {
  const [params] = useSearchParams();
  const [flights, setFlights] = useState<Flight[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const from = params.get("from") ?? "";
  const to = params.get("to") ?? "";
  useEffect(() => {
    setLoading(true);
    api.flights(from, to).then(setFlights).catch((e: Error) => setError(e.message)).finally(() => setLoading(false));
  }, [from, to]);
  return (
    <section className="content-page">
      <div className="page-kicker"><span className="eyebrow-line" /> YOUR ROUTE, YOUR RHYTHM</div>
      <div className="page-heading-row"><div><h1>{from || to ? `${from || "Anywhere"} to ${to || "everywhere"}` : "Flights worth looking forward to."}</h1><p>Choose a flight and we’ll take it from here.</p></div><Link to="/" className="outline-button">Edit search <ArrowUpRight size={15} /></Link></div>
      {loading ? <Loading /> : error ? <ErrorNote message={error} /> : flights.length ? (
        <div className="flight-list">{flights.map((flight) => <FlightCard key={flight.id} flight={flight} />)}</div>
      ) : <div className="empty-state"><Plane size={30} /><h3>No flights on this route yet</h3><p>Try another airport code, or browse all current routes.</p><Link to="/flights" className="primary-button">Browse flights <ArrowRight size={16} /></Link></div>}
    </section>
  );
}

function FlightCard({ flight }: { flight: Flight }) {
  return (
    <article className="flight-card">
      <div className="airline-cell"><span className="airline-icon"><Plane size={17} /></span><span><b>{flight.airline}</b><small>{flight.flightNumber} · Economy</small></span></div>
      <div className="route-cell"><div><b>{clock(flight.departure)}</b><span>{flight.originCode}</span></div><div className="route-line"><small>{day(flight.departure)}</small><span /><small>Direct</small></div><div className="route-destination"><b>{clock(flight.arrival)}</b><span>{flight.destinationCode}</span></div></div>
      <div className="price-cell"><small>from</small><b>{money(flight.price, flight.currency)}</b><Link to={`/flight/${flight.id}`} className="choose-flight">Choose flight <ArrowRight size={15} /></Link></div>
    </article>
  );
}

function FlightPage({ session }: { session: Session | null }) {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const [flight, setFlight] = useState<Flight | null>(null);
  const [seats, setSeats] = useState<string[]>([]);
  const [seat, setSeat] = useState("");
  const [name, setName] = useState(session?.user.name ?? "");
  const [email, setEmail] = useState(session?.user.email ?? "");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  useEffect(() => {
    Promise.all([api.flight(id), api.seats(id)])
      .then(([nextFlight, nextSeats]) => { setFlight(nextFlight); setSeats(nextSeats); })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [id]);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!session) { navigate("/login", { state: { from: `/flight/${id}` } }); return; }
    if (!seat) { setError("Choose an available seat to continue."); return; }
    setBusy(true); setError("");
    try {
      const booking = await api.createBooking({ flightId: id, seatNumber: seat, passengerName: name, passengerEmail: email }, idempotencyKey);
      navigate(`/checkout/${booking.id}`, { state: { booking } });
    } catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  if (loading) return <Loading />;
  if (error && !flight) return <section className="content-page"><ErrorNote message={error} /></section>;
  if (!flight) return null;
  return (
    <section className="content-page">
      <Link to="/flights" className="back-link">← All flights</Link>
      <div className="detail-layout">
        <div>
          <div className="detail-banner"><span className="detail-airline"><Plane size={18} /> {flight.airline} · {flight.flightNumber}</span><div className="detail-route"><div><span>{flight.originCode}</span><small>{flight.originCity}</small><b>{clock(flight.departure)}</b></div><div className="detail-route-line"><span /> <small><Clock3 size={14} /> {Math.round((new Date(flight.arrival).getTime() - new Date(flight.departure).getTime()) / 60000)} min · non-stop</small><span /></div><div><span>{flight.destinationCode}</span><small>{flight.destinationCity}</small><b>{clock(flight.arrival)}</b></div></div><div className="detail-date">{day(flight.departure)} <span>·</span> {flight.currency} fare, taxes included</div></div>
          <div className="seat-section"><div className="section-title"><div><span className="eyebrow muted">MAKE IT YOURS</span><h2>Choose your seat</h2></div><span className="seat-legend"><i /> Available</span></div><div className="seat-grid">{seats.map((item) => <button key={item} type="button" onClick={() => { setSeat(item); setIdempotencyKey(crypto.randomUUID()); }} className={`seat ${seat === item ? "selected" : ""}`} aria-pressed={seat === item}>{item}</button>)}</div><p className="seat-note">Seat availability is confirmed when you continue. Rows 1–8 · 3 seats per side</p></div>
        </div>
        <aside className="booking-side"><div className="side-heading"><span className="eyebrow muted">PASSENGER DETAILS</span><h2>Almost there.</h2><p>Your ticket will be issued to these details.</p></div><form onSubmit={submit} className="stack-form"><label>Passenger full name<input required maxLength={100} value={name} onChange={(e) => { setName(e.target.value); setIdempotencyKey(crypto.randomUUID()); }} placeholder="As shown on your ID" /></label><label>Email address<input required type="email" maxLength={254} value={email} onChange={(e) => { setEmail(e.target.value); setIdempotencyKey(crypto.randomUUID()); }} placeholder="you@example.com" /></label>{error && <ErrorNote message={error} />}{!session && <p className="form-hint">You’ll sign in before creating your booking.</p>}<div className="fare-summary"><span>Flight fare</span><b>{money(flight.price, flight.currency)}</b><span>Seat {seat || "not selected"}</span><b>Included</b></div><button className="primary-button full" disabled={busy}>{busy ? "Creating booking…" : <>Continue to payment <ArrowRight size={17} /></>}</button><p className="safe-note"><LockKeyhole size={13} /> Secure checkout powered by Razorpay</p></form></aside>
      </div>
    </section>
  );
}

function AuthPage({ onSession }: { onSession: (session: Session) => void }) {
  const navigate = useNavigate();
  const [register, setRegister] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError("");
    try {
      const session = register ? await api.register(name, email, password) : await api.login(email, password);
      onSession(session); navigate("/bookings", { replace: true });
    } catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  return <section className="auth-page"><div className="auth-art"><span className="eyebrow"><span className="eyebrow-line" /> WELCOME ABOARD</span><h1>Every great<br />trip starts with<br /><em>one small step.</em></h1><p>Your next adventure is closer than you think.</p><Plane className="auth-plane" size={40} /></div><div className="auth-form-wrap"><form className="auth-form" onSubmit={submit}><span className="eyebrow muted">{register ? "CREATE YOUR ACCOUNT" : "GOOD TO SEE YOU AGAIN"}</span><h2>{register ? "Join us on the way." : "Welcome back."}</h2><p>{register ? "A few details and you're ready to explore." : "Sign in to pick up where you left off."}</p>{register && <label>Your name<input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" /></label>}<label>Email address<input required type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" /></label><label>Password<input required type="password" minLength={register ? 10 : 1} maxLength={72} value={password} onChange={(e) => setPassword(e.target.value)} autoComplete={register ? "new-password" : "current-password"} />{register && <small>Use at least 10 characters.</small>}</label>{error && <ErrorNote message={error} />}<button className="primary-button full" disabled={busy}>{busy ? "One moment…" : register ? "Create account" : "Sign in"} <ArrowRight size={17} /></button><div className="auth-switch">{register ? "Already travelling with us?" : "New around here?"} <button type="button" onClick={() => { setRegister(!register); setError(""); }}>{register ? "Sign in" : "Create an account"}</button></div></form></div></section>;
}

function BookingsPage() {
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [flights, setFlights] = useState<Flight[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    Promise.all([api.bookings(), api.flights()]).then(([bookingsData, flightsData]) => { setBookings(bookingsData); setFlights(flightsData); })
      .catch((e: Error) => setError(e.message)).finally(() => setLoading(false));
  }, [refresh]);
  const flightById = useMemo(() => new Map(flights.map((flight) => [flight.id, flight])), [flights]);
  async function cancel(id: string) {
    if (!window.confirm("Cancel this flight? Confirmed bookings will be refunded through Razorpay.")) return;
    try { await api.cancelBooking(id); setRefresh((value) => value + 1); }
    catch (e) { setError((e as Error).message); }
  }
  return <section className="content-page"><div className="page-kicker"><span className="eyebrow-line" /> YOUR JOURNEY, ALL IN ONE PLACE</div><div className="page-heading-row"><div><h1>My trips</h1><p>Your plans, tickets, and everything in between.</p></div><Link to="/flights" className="primary-button">Find a flight <ArrowRight size={16} /></Link></div>{loading ? <Loading /> : error ? <ErrorNote message={error} /> : bookings.length ? <div className="booking-list">{bookings.map((booking) => <article className="booking-card" key={booking.id}><div className="booking-status"><span className={`status-pill status-${booking.status.toLowerCase()}`}>{booking.status.replace("_", " ")}</span><span className="pnr">PNR · {booking.pnr}</span></div><div className="booking-info"><div><span className="eyebrow muted">FLIGHT</span><h3>{flightById.get(booking.flightId)?.originCode ?? "—"} <ArrowRight size={18} /> {flightById.get(booking.flightId)?.destinationCode ?? "—"}</h3><p>{flightById.get(booking.flightId) ? day(flightById.get(booking.flightId)!.departure) : "Flight details"} · Seat {booking.seatNumber}</p></div><div className="booking-amount"><small>Paid / due</small><b>{money(booking.amount, booking.currency)}</b></div></div><div className="booking-actions">{booking.status === "CONFIRMED" && <Link to={`/ticket/${booking.id}`} className="text-link">View e-ticket <ArrowUpRight size={16} /></Link>}{booking.status === "PENDING_PAYMENT" && <Link to={`/checkout/${booking.id}`} className="text-link">Complete payment <ArrowRight size={16} /></Link>}{booking.status !== "CANCELLED" && <button className="cancel-button" onClick={() => cancel(booking.id)}>Cancel trip</button>}</div></article>)}</div> : <div className="empty-state"><Ticket size={30} /><h3>Your next trip belongs here</h3><p>You haven’t booked any journeys yet. Let’s find somewhere worth going.</p><Link className="primary-button" to="/flights">Explore flights <ArrowRight size={16} /></Link></div>}</section>;
}

function CheckoutPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => { api.bookings().then((items) => setBooking(items.find((item) => item.id === id) ?? null)).catch((e: Error) => setError(e.message)).finally(() => setLoading(false)); }, [id]);
  async function pay() {
    setBusy(true); setError("");
    try {
      const order: Order = await api.order(id);
      await loadRazorpay();
      if (!window.Razorpay) throw new Error("Razorpay Checkout could not be loaded. Check your connection and try again.");
      const checkout = new window.Razorpay({
        key: order.keyId,
        amount: order.amount,
        currency: order.currency,
        name: "Jet Set Go",
        description: `Flight ticket · ${booking?.pnr ?? ""}`,
        order_id: order.orderId,
        prefill: { name: booking?.passengerName, email: booking?.passengerEmail },
        theme: { color: "#10263f" },
        handler: async (response: Record<string, string>) => {
          try {
            await api.verifyPayment(id, {
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            });
            navigate(`/ticket/${id}`, { replace: true });
          } catch (e) { setError((e as Error).message); setBusy(false); }
        },
        modal: { ondismiss: () => setBusy(false) },
      });
      checkout.open();
    } catch (e) { setError((e as Error).message); setBusy(false); }
  }
  if (loading) return <Loading />;
  if (!booking) return <section className="content-page"><ErrorNote message={error || "Booking not found."} /></section>;
  return <section className="content-page checkout-page"><div className="page-kicker"><span className="eyebrow-line" /> ONE LAST STEP</div><div className="checkout-card"><span className="checkout-icon"><LockKeyhole size={23} /></span><span className="eyebrow muted">SECURE CHECKOUT</span><h1>Your seat is waiting.</h1><p>Complete payment securely with Razorpay to confirm your booking and issue your e-ticket.</p><div className="checkout-line"><span>Passenger</span><b>{booking.passengerName}</b></div><div className="checkout-line"><span>Seat · PNR</span><b>{booking.seatNumber} · {booking.pnr}</b></div><div className="checkout-total"><span>Total · taxes included</span><b>{money(booking.amount, booking.currency)}</b></div>{error && <ErrorNote message={error} />}<button className="primary-button full" onClick={pay} disabled={busy || booking.status !== "PENDING_PAYMENT"}>{busy ? "Opening secure checkout…" : booking.status === "CONFIRMED" ? "Payment complete" : "Pay securely with Razorpay"} <ArrowRight size={17} /></button><p className="safe-note"><ShieldCheck size={14} /> Test-mode checkout until your live credentials are configured.</p></div></section>;
}

function TicketPage() {
  const { id = "" } = useParams();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [flight, setFlight] = useState<Flight | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    api.bookings().then((items) => {
      const found = items.find((item) => item.id === id);
      if (!found) throw new Error("Ticket not found.");
      setBooking(found);
      return api.flight(found.flightId);
    }).then(setFlight).catch((e: Error) => setError(e.message));
  }, [id]);
  if (error) return <section className="content-page"><ErrorNote message={error} /></section>;
  if (!booking || !flight) return <Loading />;
  return <section className="content-page ticket-page"><div className="ticket-success"><span><Check size={21} /></span><div><b>{booking.status === "CONFIRMED" ? "You’re all set." : "Booking received."}</b><small>{booking.status === "CONFIRMED" ? "Your ticket is confirmed. Have a beautiful trip." : "Payment is still pending. Complete checkout to issue your ticket."}</small></div></div><article className="ticket"><div className="ticket-top"><div className="brand mini"><span className="brand-mark"><Plane size={15} fill="currentColor" /></span><span>jet set <b>go</b><small>BOARDING PASS</small></span></div><div className="ticket-status">{booking.status.replace("_", " ")}</div></div><div className="ticket-route"><div><small>FROM</small><b>{flight.originCode}</b><span>{flight.originCity}</span></div><div className="ticket-flight"><Plane size={19} /><span>{flight.flightNumber} · Direct</span></div><div className="ticket-destination"><small>TO</small><b>{flight.destinationCode}</b><span>{flight.destinationCity}</span></div></div><div className="ticket-details"><div><small>PASSENGER</small><b>{booking.passengerName}</b></div><div><small>DATE & TIME</small><b>{day(flight.departure)} · {clock(flight.departure)}</b></div><div><small>SEAT</small><b>{booking.seatNumber}</b></div><div><small>BOOKING REFERENCE</small><b>{booking.pnr}</b></div></div><div className="ticket-bottom"><span>JET SET GO · YOUR JOURNEY STARTS HERE</span><span>{money(booking.amount, booking.currency)}</span></div></article><div className="ticket-actions"><button className="primary-button" onClick={() => window.print()}>Print / save ticket <ArrowUpRight size={16} /></button><Link to="/bookings" className="outline-button">Back to my trips</Link></div></section>;
}

function LoginPrompt() { return <section className="content-page prompt-page"><UserRound size={31} /><h2>Your journeys, all together.</h2><p>Sign in to see bookings and tickets made with Jet Set Go.</p><Link to="/login" className="primary-button">Sign in <ArrowRight size={16} /></Link></section>; }
function NotFound() { return <section className="content-page prompt-page"><Plane size={31} /><h2>This route took a detour.</h2><p>We couldn’t find the page you were looking for.</p><Link to="/" className="primary-button">Back to the beginning <ArrowRight size={16} /></Link></section>; }
function Loading() { return <div className="loading-state"><span className="spinner" /> Finding your way…</div>; }
function ErrorNote({ message }: { message: string }) { return <div className="error-note" role="alert">{message}</div>; }
function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve();
  return new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Could not load Razorpay Checkout."));
    document.body.appendChild(script);
  });
}
function Footer() { return <footer className="site-footer"><Link to="/" className="brand"><span className="brand-mark"><Plane size={17} fill="currentColor" /></span><span>jet set <b>go</b><small>TRAVEL, BEAUTIFULLY</small></span></Link><span>Made for wherever life takes you.</span><span>© {new Date().getFullYear()} Jet Set Go</span></footer>; }
