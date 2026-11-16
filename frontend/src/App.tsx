import { useEffect, useMemo, useState } from "react";
import {
  ArrowDownUp, ArrowRight, ArrowUpRight, BarChart3, Check, CircleHelp, Clock3, Download,
  Globe2, LayoutDashboard, LockKeyhole, LogOut, Menu, Plane, Plus, Search, ShieldCheck, Sparkles,
  Ticket, Trash2, UserRound, Users, X,
} from "lucide-react";
import { Link, Navigate, Route, Routes, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api, getSession, saveSession } from "./api";
import AirportCombobox from "./components/AirportCombobox";
import type { AdminSummary, Airport, Booking, Flight, Order, RouteTemplate, Session, UserSummary } from "./types";

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => { open: () => void };
  }
}

const money = (amount: number, currency = "INR") =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency, maximumFractionDigits: 0 }).format(amount);
const clock = (value: string, timeZone?: string | null) => new Date(value).toLocaleTimeString("en-IN", {
  hour: "2-digit", minute: "2-digit", ...(timeZone ? { timeZone } : {}),
});
const day = (value: string, timeZone?: string | null) => new Date(value).toLocaleDateString("en-IN", {
  weekday: "short", day: "numeric", month: "short", ...(timeZone ? { timeZone } : {}),
});
const displayCountry = (code: string) =>
  new Intl.DisplayNames(["en"], { type: "region" }).of(code) ?? code;
const offsetDate = (value: string, days: number) => {
  const date = new Date(`${value}T12:00:00`);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
};
const localDateInput = (offsetDays = 0) => {
  const date = new Date();
  date.setHours(12, 0, 0, 0);
  date.setDate(date.getDate() + offsetDays);
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 10);
};
const toLocalDateTimeInput = (value: string) => {
  const date = new Date(value);
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 16);
};
const matchesTimeWindow = (flight: Flight, window: string) => {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: flight.originTimeZone ?? undefined,
    hour: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(flight.departure));
  const hour = Number(parts.find((part) => part.type === "hour")?.value ?? 0);
  return window === "morning" ? hour < 12 : window === "afternoon" ? hour >= 12 && hour < 18 : hour >= 18;
};

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
          <BrandLockup />
        </Link>
        <button className="mobile-toggle icon-button" onClick={() => setMobileOpen(!mobileOpen)} aria-label="Toggle menu">
          {mobileOpen ? <X /> : <Menu />}
        </button>
        <nav className={mobileOpen ? "main-nav is-open" : "main-nav"}>
          <Link to="/flights" onClick={() => setMobileOpen(false)}>Explore flights</Link>
          <Link to="/bookings" onClick={() => setMobileOpen(false)}>My trips</Link>
          {session?.user.role === "ADMIN" && <Link to="/admin" onClick={() => setMobileOpen(false)}>Admin</Link>}
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
          <Route path="/login" element={session ? <Navigate to={session.user.role === "ADMIN" ? "/admin" : "/bookings"} replace /> : <AuthPage onSession={updateSession} />} />
          <Route path="/bookings" element={session ? <BookingsPage /> : <LoginPrompt />} />
          <Route path="/admin" element={session?.user.role === "ADMIN" ? <AdminPage /> : <AccessDenied />} />
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
  const [from, setFrom] = useState<Airport | null>(null);
  const [to, setTo] = useState<Airport | null>(null);
  const [mode, setMode] = useState<"domestic" | "international">("domestic");
  const [countryCode, setCountryCode] = useState("IN");
  const [countryCodes, setCountryCodes] = useState<string[]>(["IN"]);
  const [countryError, setCountryError] = useState("");
  const [departureDate, setDepartureDate] = useState(() => localDateInput(2));
  const [tripType, setTripType] = useState<"ONE_WAY" | "ROUND_TRIP">("ONE_WAY");
  const [returnDate, setReturnDate] = useState(() => localDateInput(7));
  const [passengerCount, setPassengerCount] = useState(1);
  const [travelClass, setTravelClass] = useState("ECONOMY");
  const [error, setError] = useState("");
  useEffect(() => {
    api.airportCountries().then((codes) => {
      setCountryCodes(codes.length ? codes : ["IN"]);
      setCountryError("");
    }).catch(() => setCountryError("Airport country list is unavailable."));
  }, []);
  const swap = () => { setFrom(to); setTo(from); setError(""); };
  const search = (origin: Airport, destination: Airport, date: string) => {
    const params = new URLSearchParams({
      from: origin.code,
      to: destination.code,
      mode,
      tripType,
      passengers: String(passengerCount),
      travelClass,
      ...(mode === "domestic" ? { countryCode } : {}),
    });
    if (date) params.set("date", date);
    if (tripType === "ROUND_TRIP") params.set("returnDate", returnDate);
    navigate(`/flights?${params}`);
  };
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
          <div className="search-label"><span>01</span> PLAN YOUR JOURNEY <b className="demo-data-pill">DEMO DATA · NOT LIVE</b></div>
          <div className="flight-mode-tabs" role="tablist" aria-label="Flight type">
            <button type="button" role="tab" aria-selected={mode === "domestic"} className={mode === "domestic" ? "active" : ""}
              onClick={() => { setMode("domestic"); setFrom(null); setTo(null); setError(""); }}>Domestic flights</button>
            <button type="button" role="tab" aria-selected={mode === "international"} className={mode === "international" ? "active" : ""}
              onClick={() => { setMode("international"); setFrom(null); setTo(null); setError(""); }}>International flights</button>
          </div>
          <form className={`search-form ${mode === "domestic" ? "has-country" : ""}`} onSubmit={(event) => {
            event.preventDefault();
            if (!from || !to) { setError("Choose an origin and destination airport to search."); return; }
            if (from.code === to.code) { setError("Origin and destination must be different airports."); return; }
            if (mode === "domestic" && (from.countryCode !== countryCode || to.countryCode !== countryCode)) {
              setError("Choose both airports within the selected country."); return;
            }
            if (mode === "international" && from.countryCode === to.countryCode) {
              setError("International flights must connect airports in different countries."); return;
            }
            if (tripType === "ROUND_TRIP" && returnDate <= departureDate) {
              setError("Return date must be after the departure date."); return;
            }
            setError("");
            search(from, to, departureDate);
          }}>
            {mode === "domestic" && <label className="search-field country-select"><span>DOMESTIC COUNTRY</span>
              <select aria-label="Domestic country" value={countryCode} onChange={(event) => {
                setCountryCode(event.target.value); setFrom(null); setTo(null); setError("");
              }}>
                {countryCodes.map((code) => <option key={code} value={code}>{displayCountry(code)}</option>)}
              </select>
            </label>}
            <AirportCombobox label="FROM" selected={from} countryCode={mode === "domestic" ? countryCode : undefined}
              excludeCode={to?.code} onChange={setFrom} />
            <button type="button" className="swap-button" onClick={swap} aria-label="Swap origin and destination"><ArrowDownUp size={17} /></button>
            <AirportCombobox label="TO" selected={to} countryCode={mode === "domestic" ? countryCode : undefined}
              excludeCode={from?.code} onChange={setTo} />
            <label className="search-field search-date"><span>DEPARTURE</span><input type="date" min={localDateInput()} value={departureDate} onChange={(event) => {
              const nextDate = event.target.value;
              setDepartureDate(nextDate);
              if (returnDate <= nextDate) {
                setReturnDate(offsetDate(nextDate, 1));
              }
            }} aria-label="Departure date" /></label>
            <button className="search-submit" type="submit"><Search size={18} /><span>Find flights</span><ArrowRight size={17} /></button>
          </form>
          <div className="journey-options">
            <label><span>TRIP</span><select value={tripType} onChange={(event) => setTripType(event.target.value as "ONE_WAY" | "ROUND_TRIP")}><option value="ONE_WAY">One way</option><option value="ROUND_TRIP">Round trip</option></select></label>
            {tripType === "ROUND_TRIP" && <label><span>RETURN</span><input type="date" min={offsetDate(departureDate, 1)} value={returnDate} onChange={(event) => setReturnDate(event.target.value)} /></label>}
            <label><span>PASSENGERS</span><select value={passengerCount} onChange={(event) => setPassengerCount(Number(event.target.value))}>{Array.from({ length: 6 }, (_, index) => index + 1).map((count) => <option key={count} value={count}>{count} {count === 1 ? "passenger" : "passengers"}</option>)}</select></label>
            <label><span>TRAVEL CLASS</span><select value={travelClass} onChange={(event) => setTravelClass(event.target.value)}><option value="ECONOMY">Economy</option><option value="PREMIUM_ECONOMY">Premium economy</option><option value="BUSINESS">Business</option><option value="FIRST">First</option></select></label>
          </div>
          {error && <div className="search-error" role="alert">{error}</div>}
          {countryError && <div className="search-error" role="status">{countryError} Domestic search currently defaults to India.</div>}
          <div className="search-foot"><span><LockKeyhole size={13} /> Your journey, your way</span><span>DEMO SCHEDULES · NOT LIVE FLIGHT DATA</span></div>
        </div>
        <div className="hero-orbit orbit-one" /><div className="hero-orbit orbit-two" />
      </section>
      <DemoFlightsSection />
      <section className="intro-section" id="why-us">
        <div><span className="eyebrow muted"><span className="eyebrow-line" /> A LITTLE MORE HUMAN</span><h2>Travel should feel<br />like <em>the beginning.</em></h2></div>
        <div className="intro-right"><p>From the first search to the final boarding call, we make the details feel easy—so you can focus on the moments waiting at the other end.</p><Link to="/flights" className="text-link">Explore all flights <ArrowUpRight size={16} /></Link></div>
      </section>
      <section className="destinations-section">
        <div className="destination-heading">        <div><span className="eyebrow muted"><span className="eyebrow-line" /> A FEW GOOD WAYS TO GET AWAY</span><h2>Somewhere is <em>calling.</em></h2></div><p>Explore sample routes from our demo schedule. Fares are illustrative, not live offers.</p></div>
        <div className="destination-grid">
          {[
            { from: "DEL", to: "BOM", city: "Mumbai", eyebrow: "CITY LIGHTS", detail: "A quick escape from Delhi", style: "destination-mumbai" },
            { from: "BOM", to: "BLR", city: "Bengaluru", eyebrow: "GARDEN CITY", detail: "Mumbai to Bengaluru", style: "destination-bengaluru" },
            { from: "BLR", to: "DEL", city: "New Delhi", eyebrow: "A CHANGE OF SCENE", detail: "Bengaluru to the capital", style: "destination-delhi" },
            { from: "DEL", to: "BLR", city: "Bengaluru", eyebrow: "SOUTHBOUND", detail: "A direct sample route", style: "destination-south" },
          ].map((route) => (
            <Link key={`${route.from}-${route.to}`} className={`destination-card ${route.style}`} to={`/flights?from=${route.from}&to=${route.to}`}>
              <span className="destination-code">{route.from} <ArrowRight size={14} /> {route.to}</span>
              <span className="destination-copy"><small>{route.eyebrow}</small><b>{route.city}</b><span>{route.detail}</span></span>
              <ArrowUpRight className="destination-arrow" size={18} />
            </Link>
          ))}
        </div>
      </section>
      <section className="values-grid">
        <article className="value-card value-blue"><span className="value-icon"><Globe2 /></span><span className="value-number">01 / DISCOVER</span><h3>Good routes.<br />Better stories.</h3><p>Thoughtful connections between the places that matter to you.</p><Link to="/flights">Find your route <ArrowRight size={16} /></Link></article>
        <article className="value-card value-sand"><span className="value-icon"><Ticket /></span><span className="value-number">02 / BOOK</span><h3>Simple from<br />the start.</h3><p>Clear pricing and a booking experience that respects your time.</p><Link to="/flights">See available seats <ArrowRight size={16} /></Link></article>
        <article className="value-card value-green"><span className="value-icon"><CircleHelp /></span><span className="value-number">03 / GO</span><h3>Here for<br />the whole trip.</h3><p>Your itinerary and ticket are right where you need them.</p><Link to="/bookings">View my trips <ArrowRight size={16} /></Link></article>
      </section>
    </>
  );
}

function DemoFlightsSection() {
  const [flights, setFlights] = useState<Flight[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    api.featuredFlights()
      .then((items) => { if (active) setFlights(items); })
      .catch((failure: Error) => { if (active) setError(failure.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  return (
    <section className="demo-flights-section">
      <div className="demo-flights-heading">
        <div>
          <span className="eyebrow muted"><span className="eyebrow-line" /> SAMPLE SCHEDULES · NOT LIVE INVENTORY</span>
          <h2>Demo flights, ready to explore.</h2>
        </div>
        <Link to="/flights" className="text-link">Browse demo flights <ArrowRight size={16} /></Link>
      </div>
      <p className="demo-flights-note">These simulated flights and fares are for demonstration only. No real airline inventory is sold.</p>
      {loading ? <Loading /> : error ? <ErrorNote message={error} /> : flights.length ? (
        <div className="flight-list">
          {flights.map((flight) => <FlightCard key={flight.id} flight={flight} searchQuery="" />)}
        </div>
      ) : (
        <div className="demo-flights-empty">
          Demo schedules are not available yet. Please try again after the backend finishes initializing.
        </div>
      )}
    </section>
  );
}

function FlightsPage() {
  const [params, setParams] = useSearchParams();
  const [flights, setFlights] = useState<Flight[]>([]);
  const [airlines, setAirlines] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const from = params.get("from") ?? "";
  const to = params.get("to") ?? "";
  const date = params.get("date") ?? "";
  const sort = params.get("sort") ?? "departure";
  const mode = params.get("mode") ?? "";
  const countryCode = params.get("countryCode") ?? "";
  const tripType = params.get("tripType") ?? "ONE_WAY";
  const returnDate = params.get("returnDate") ?? "";
  const passengerCount = params.get("passengers") ?? "1";
  const travelClass = (params.get("travelClass") ?? "ECONOMY").replace("_", " ");
  const airline = params.get("airline") ?? "";
  const maxPrice = params.get("maxPrice") ?? "";
  const timeWindow = params.get("timeWindow") ?? "";
  const maxDuration = params.get("maxDuration") ?? "";
  useEffect(() => {
    setLoading(true);
    api.flights(from, to, date, sort, mode, countryCode)
      .then((items) => {
        setFlights(items);
        setAirlines([...new Set(items.map((flight) => flight.airline))].sort());
        setError("");
      }).catch((e: Error) => setError(e.message)).finally(() => setLoading(false));
  }, [from, to, date, sort, mode, countryCode]);
  const visibleFlights = flights.filter((flight) =>
    (!airline || flight.airline === airline)
    && (!maxPrice || flight.price <= Number(maxPrice))
    && (!maxDuration || (new Date(flight.arrival).getTime() - new Date(flight.departure).getTime()) / 60000 <= Number(maxDuration))
    && (!timeWindow || matchesTimeWindow(flight, timeWindow)));
  const dateStripStart = date || localDateInput();
  return (
    <section className="content-page">
      <div className="page-kicker"><span className="eyebrow-line" /> {mode ? `${mode.toUpperCase()} SEARCH` : "ALL ROUTES"} · DEMO SCHEDULES · NOT LIVE</div>
      <div className="page-heading-row"><div><h1>{from || to ? `${from || "Anywhere"} to ${to || "everywhere"}` : "Flights worth looking forward to."}</h1><p>{date ? `Sample flights for ${new Date(`${date}T12:00:00`).toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long" })}.` : "Choose a flight and we’ll take it from here."} · {tripType === "ROUND_TRIP" && returnDate ? `Return ${returnDate} · ` : ""}{passengerCount} passenger{passengerCount === "1" ? "" : "s"} · {travelClass}.</p></div><div className="flight-tools">
        <label className="sort-control">Sort by<select value={sort} onChange={(event) => { const next = new URLSearchParams(params); next.set("sort", event.target.value); setParams(next); }}><option value="departure">Earliest departure</option><option value="arrival">Earliest arrival</option><option value="price">Lowest fare</option><option value="duration">Shortest duration</option></select></label>
        <label className="sort-control">Airline<select value={airline} onChange={(event) => { const next = new URLSearchParams(params); event.target.value ? next.set("airline", event.target.value) : next.delete("airline"); setParams(next); }}><option value="">All airlines</option>{airlines.map((item) => <option key={item}>{item}</option>)}</select></label>
        <label className="sort-control">Max fare<input aria-label="Maximum fare" type="number" min="0" placeholder="Any" value={maxPrice} onChange={(event) => { const next = new URLSearchParams(params); event.target.value ? next.set("maxPrice", event.target.value) : next.delete("maxPrice"); setParams(next); }} /></label>
        <label className="sort-control">Departure time<select value={timeWindow} onChange={(event) => { const next = new URLSearchParams(params); event.target.value ? next.set("timeWindow", event.target.value) : next.delete("timeWindow"); setParams(next); }}><option value="">Any time</option><option value="morning">Morning · before noon</option><option value="afternoon">Afternoon · noon–6 PM</option><option value="evening">Evening · after 6 PM</option></select></label>
        <label className="sort-control">Max duration<select value={maxDuration} onChange={(event) => { const next = new URLSearchParams(params); event.target.value ? next.set("maxDuration", event.target.value) : next.delete("maxDuration"); setParams(next); }}><option value="">Any duration</option><option value="180">3 hours</option><option value="360">6 hours</option><option value="600">10 hours</option></select></label>
        <Link to="/" className="outline-button">Edit search <ArrowUpRight size={15} /></Link>
      </div></div>
      {from && to && <nav className="date-strip" aria-label="Nearby departure dates">
        {Array.from({ length: 5 }, (_, index) => {
          const optionDate = offsetDate(dateStripStart, index);
          const next = new URLSearchParams(params);
          next.set("date", optionDate);
          return <Link key={optionDate} to={`/flights?${next.toString()}`} className={optionDate === dateStripStart ? "active" : ""}>
            <small>{new Date(`${optionDate}T12:00:00`).toLocaleDateString("en-IN", { weekday: "short" })}</small>
            <b>{new Date(`${optionDate}T12:00:00`).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}</b>
          </Link>;
        })}
      </nav>}
      {loading ? <Loading /> : error ? <ErrorNote message={error} /> : visibleFlights.length ? (
        <div className="flight-list">{visibleFlights.map((flight) => <FlightCard key={flight.id} flight={flight} searchQuery={params.toString()} />)}</div>
      ) : <div className="empty-state"><Plane size={30} /><h3>{flights.length ? "No flights match these filters" : "No demo flights for this route and date"}</h3><p>{flights.length ? "Adjust the airline, time, duration, or fare filters." : "Demo schedules are generated for airports with scheduled passenger service, up to 120 days ahead. These are simulated flights, not live airline inventory."}</p>{from && to && <Link className="text-link" to={`/flights?${new URLSearchParams({ from, to, date: offsetDate(dateStripStart, 1), mode, countryCode }).toString()}`}>Try the next day <ArrowRight size={16} /></Link>}<Link to="/" className="primary-button">Edit search <ArrowRight size={16} /></Link></div>}
    </section>
  );
}

function FlightCard({ flight, searchQuery }: { flight: Flight; searchQuery: string }) {
  return (
    <article className="flight-card">
      <div className="airline-cell"><span className="airline-icon"><Plane size={17} /></span><span><b>{flight.airline}</b><small>{flight.flightNumber} · {flight.aircraft || "Aircraft not configured"}</small></span><span className={`status-pill status-${flight.status.toLowerCase()}`}>{flight.status} · DEMO</span></div>
      <div className="route-cell"><div><b>{clock(flight.departure, flight.originTimeZone)}</b><span>{flight.originCode}</span></div><div className="route-line"><small>{day(flight.departure, flight.originTimeZone)}</small><span /><small>{Math.round((new Date(flight.arrival).getTime() - new Date(flight.departure).getTime()) / 60000)} min · Direct</small></div><div className="route-destination"><b>{clock(flight.arrival, flight.destinationTimeZone)}</b><span>{flight.destinationCode}</span></div></div>
      <div className="price-cell"><small>{flight.status === "SCHEDULED" ? "demo fare / passenger" : "not bookable"}</small><b>{money(flight.price, flight.currency)}</b><Link to={`/flight/${flight.id}${searchQuery ? `?${searchQuery}` : ""}`} className="choose-flight">{flight.status === "SCHEDULED" ? "Choose flight" : "View status"} <ArrowRight size={15} /></Link></div>
    </article>
  );
}

function FlightPage({ session }: { session: Session | null }) {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const tripType = searchParams.get("tripType") ?? "ONE_WAY";
  const roundTrip = tripType === "ROUND_TRIP";
  const passengerCount = Math.max(1, Math.min(6, Number(searchParams.get("passengers") ?? "1") || 1));
  const travelClass = (searchParams.get("travelClass") ?? "ECONOMY").toUpperCase();
  const mode = searchParams.get("mode") ?? "";
  const countryCode = searchParams.get("countryCode") ?? "";
  const returnDate = searchParams.get("returnDate") ?? "";
  const [flight, setFlight] = useState<Flight | null>(null);
  const [isInternationalRoute, setIsInternationalRoute] = useState(mode === "international");
  const [seats, setSeats] = useState<string[]>([]);
  const [selectedSeats, setSelectedSeats] = useState<string[]>([]);
  const [returnFlights, setReturnFlights] = useState<Flight[]>([]);
  const [returnFlight, setReturnFlight] = useState<Flight | null>(null);
  const [returnSeats, setReturnSeats] = useState<string[]>([]);
  const [selectedReturnSeats, setSelectedReturnSeats] = useState<string[]>([]);
  const [passengers, setPassengers] = useState(() => Array.from({ length: passengerCount }, (_, index) => ({
    name: index === 0 ? session?.user.name ?? "" : "",
    nationality: "",
    passportExpiryDate: "",
  })));
  const [email, setEmail] = useState(session?.user.email ?? "");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  useEffect(() => {
    let active = true;
    setLoading(true);
    setIsInternationalRoute(mode === "international");
    Promise.all([api.flight(id), api.seats(id, travelClass)])
      .then(async ([nextFlight, nextSeats]) => {
        const [originAirports, destinationAirports] = await Promise.all([
          api.airports(nextFlight.originCode),
          api.airports(nextFlight.destinationCode),
        ]);
        const originCountryCode = originAirports.find((airport) => airport.code === nextFlight.originCode)?.countryCode;
        const destinationCountryCode = destinationAirports.find((airport) => airport.code === nextFlight.destinationCode)?.countryCode;
        const hasCountryData = Boolean(originCountryCode && destinationCountryCode);
        const routeIsInternational = hasCountryData
          ? originCountryCode !== destinationCountryCode
          : mode === "international";
        const routeMode = hasCountryData ? routeIsInternational ? "international" : "domestic" : mode;
        const domesticCountryCode = originCountryCode || countryCode || undefined;
        const nextReturnFlights = roundTrip && returnDate
          ? await api.flights(nextFlight.destinationCode, nextFlight.originCode, returnDate,
              "departure", routeMode || undefined,
              routeMode === "domestic" ? domesticCountryCode : undefined)
          : [];
        if (!active) return;
        setFlight(nextFlight);
        setIsInternationalRoute(routeIsInternational);
        setSeats(nextSeats);
        setReturnFlights(nextReturnFlights);
        setReturnFlight(null);
        setReturnSeats([]);
        setSelectedSeats([]);
        setSelectedReturnSeats([]);
        setError("");
      })
      .catch((e: Error) => { if (active) setError(e.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id, roundTrip, returnDate, mode, countryCode, travelClass]);
  useEffect(() => {
    if (!returnFlight) {
      setReturnSeats([]);
      return;
    }
    let active = true;
    setReturnSeats([]);
    api.seats(returnFlight.id, travelClass)
      .then((available) => { if (active) setReturnSeats(available); })
      .catch((e: Error) => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [returnFlight?.id, travelClass]);
  useEffect(() => {
    setPassengers((current) => Array.from({ length: passengerCount }, (_, index) => current[index] ?? {
      name: "", nationality: "", passportExpiryDate: "",
    }));
  }, [passengerCount]);
  function toggleSeat(seat: string, selected: string[], update: (next: string[]) => void) {
    if (selected.includes(seat)) update(selected.filter((item) => item !== seat));
    else if (selected.length < passengerCount) update([...selected, seat]);
    setIdempotencyKey(crypto.randomUUID());
  }
  function chooseReturnFlight(nextFlight: Flight) {
    setError("");
    setReturnFlight(nextFlight);
    setSelectedReturnSeats([]);
    setIdempotencyKey(crypto.randomUUID());
  }
  function updatePassenger(index: number, field: keyof (typeof passengers)[number], value: string) {
    setPassengers((current) => current.map((passenger, passengerIndex) =>
      passengerIndex === index ? { ...passenger, [field]: value } : passenger));
    setIdempotencyKey(crypto.randomUUID());
  }
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!session) { navigate("/login", { state: { from: `${location.pathname}${location.search}` } }); return; }
    if (selectedSeats.length !== passengerCount) { setError(`Choose ${passengerCount} outbound seat${passengerCount === 1 ? "" : "s"}.`); return; }
    if (roundTrip && !returnFlight) { setError("Choose a return flight to continue."); return; }
    if (roundTrip && selectedReturnSeats.length !== passengerCount) { setError(`Choose ${passengerCount} return seat${passengerCount === 1 ? "" : "s"}.`); return; }
    setBusy(true); setError("");
    try {
      const booking = await api.createBooking({
        flightId: id,
        seatNumber: selectedSeats[0],
        seatNumbers: selectedSeats,
        returnFlightId: returnFlight?.id,
        returnSeatNumbers: selectedReturnSeats,
        passengers: passengers.map((passenger) => ({
          name: passenger.name,
          ...(isInternationalRoute ? {
            nationality: passenger.nationality,
            passportExpiryDate: passenger.passportExpiryDate,
          } : {}),
        })),
        travelClass,
        passengerName: passengers[0]?.name ?? "",
        passengerEmail: email,
      }, idempotencyKey);
      navigate(`/checkout/${booking.id}`, { state: { booking } });
    } catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  const classMultiplier = travelClass === "FIRST" ? 4 : travelClass === "BUSINESS" ? 2.5
    : travelClass === "PREMIUM_ECONOMY" ? 1.5 : 1;
  const estimatedTotal = flight
    ? (flight.price + (returnFlight?.price ?? 0)) * passengerCount * classMultiplier : 0;
  if (loading) return <Loading />;
  if (error && !flight) return <section className="content-page"><ErrorNote message={error} /></section>;
  if (!flight) return null;
  return (
    <section className="content-page">
      <Link to="/flights" className="back-link">← All flights</Link>
      <div className="detail-layout">
        <div>
          <div className="detail-banner"><span className="detail-airline"><Plane size={18} /> {flight.airline} · {flight.flightNumber} · {flight.dataSource === "LIVE" ? "LIVE" : "DEMO"}</span><div className="detail-route"><div><span>{flight.originCode}</span><small>{flight.originCity}</small><b>{clock(flight.departure, flight.originTimeZone)}</b></div><div className="detail-route-line"><span /> <small><Clock3 size={14} /> {Math.round((new Date(flight.arrival).getTime() - new Date(flight.departure).getTime()) / 60000)} min · non-stop</small><span /></div><div><span>{flight.destinationCode}</span><small>{flight.destinationCity}</small><b>{clock(flight.arrival, flight.destinationTimeZone)}</b></div></div><div className="detail-date">{day(flight.departure, flight.originTimeZone)} <span>·</span> {flight.currency} illustrative DEMO fare</div></div>
          <div className="flight-spec-grid"><article><small>STATUS · SIMULATED</small><b>{flight.status}</b></article><article><small>AIRCRAFT</small><b>{flight.aircraft || "Not configured"}</b></article><article><small>ORIGIN TERMINAL</small><b>{flight.originTerminal || "Not configured"}</b></article><article><small>DESTINATION TERMINAL</small><b>{flight.destinationTerminal || "Not configured"}</b></article><article><small>DEPARTURE TIMEZONE</small><b>{flight.originTimeZone || "Not configured"}</b></article><article><small>ARRIVAL TIMEZONE</small><b>{flight.destinationTimeZone || "Not configured"}</b></article><article><small>SELECTED CABIN</small><b>{travelClass.replace("_", " ")}</b></article><article><small>AVAILABLE CABIN SEATS</small><b>{seats.length}</b></article><article><small>BAGGAGE ALLOWANCE</small><b>Not configured</b></article><article><small>FARE CONDITIONS</small><b>Demo fare · cancellation rules simulated</b></article></div>
          {flight.status !== "SCHEDULED" && <div className="error-note" role="status">This simulated flight is {flight.status.toLowerCase()} and cannot be booked.</div>}
          <div className="seat-section"><div className="section-title"><div><span className="eyebrow muted">OUTBOUND · {passengerCount} PASSENGER{passengerCount === 1 ? "" : "S"} · {travelClass.replace("_", " ")}</span><h2>Choose {passengerCount === 1 ? "your seat" : "your seats"}</h2></div><span className="seat-legend"><i /> Available</span></div><div className="seat-grid">{seats.map((item) => <button key={item} type="button" disabled={flight.status !== "SCHEDULED" || !selectedSeats.includes(item) && selectedSeats.length >= passengerCount} onClick={() => toggleSeat(item, selectedSeats, setSelectedSeats)} className={`seat ${selectedSeats.includes(item) ? "selected" : ""}`} aria-pressed={selectedSeats.includes(item)}>{item}</button>)}</div><p className="seat-note">Seats are grouped by cabin. Availability is rechecked atomically when you continue.</p></div>
          {roundTrip && <div className="seat-section return-flight-section">
            <div className="section-title"><div><span className="eyebrow muted">RETURN · {returnDate || "DATE NOT SET"}</span><h2>Choose your return flight</h2></div></div>
            {returnFlights.length ? <div className="return-flight-list">{returnFlights.map((item) =>
              <button type="button" key={item.id} className={`return-flight-option ${returnFlight?.id === item.id ? "selected" : ""}`}
                aria-pressed={returnFlight?.id === item.id} disabled={item.status !== "SCHEDULED"} onClick={() => chooseReturnFlight(item)}>
                <span><b>{item.airline} · {item.flightNumber} · {item.status}</b><small>{item.originCode} {clock(item.departure, item.originTimeZone)} → {item.destinationCode} {clock(item.arrival, item.destinationTimeZone)}</small></span>
                <strong>{money(item.price, item.currency)}</strong>
              </button>)}</div> : <p className="seat-note">No DEMO return flights are scheduled for this date. Try the suggested return date or choose one-way.</p>}
            {returnFlight && <><div className="section-title return-seat-heading"><div><span className="eyebrow muted">RETURN SEATS · {travelClass.replace("_", " ")}</span><h2>Choose {passengerCount === 1 ? "your seat" : "your seats"}</h2></div></div><div className="seat-grid">{returnSeats.map((item) => <button key={item} type="button" disabled={!selectedReturnSeats.includes(item) && selectedReturnSeats.length >= passengerCount} onClick={() => toggleSeat(item, selectedReturnSeats, setSelectedReturnSeats)} className={`seat ${selectedReturnSeats.includes(item) ? "selected" : ""}`} aria-pressed={selectedReturnSeats.includes(item)}>{item}</button>)}</div></>}
          </div>}
        </div>
        <aside className="booking-side"><div className="side-heading"><span className="eyebrow muted">PASSENGER DETAILS</span><h2>Almost there.</h2><p>Demo booking for {passengerCount} passenger{passengerCount === 1 ? "" : "s"} · {travelClass.replace("_", " ").toLowerCase()}.</p></div><form onSubmit={submit} className="stack-form">
          {passengers.map((passenger, index) => <fieldset className="passenger-fields" key={index}><legend>Passenger {index + 1}</legend><label>Full name<input required maxLength={100} value={passenger.name} onChange={(event) => updatePassenger(index, "name", event.target.value)} placeholder="As shown on travel ID" /></label>
            {isInternationalRoute && <><label>Nationality (2-letter country code)<input required maxLength={2} pattern="[A-Za-z]{2}" value={passenger.nationality} onChange={(event) => updatePassenger(index, "nationality", event.target.value.toUpperCase())} placeholder="IN" /></label><label>Passport expiry date<input required type="date" min={localDateInput(1)} value={passenger.passportExpiryDate} onChange={(event) => updatePassenger(index, "passportExpiryDate", event.target.value)} /></label></>}
          </fieldset>)}
          <label>Contact email<input required type="email" maxLength={254} value={email} onChange={(e) => { setEmail(e.target.value); setIdempotencyKey(crypto.randomUUID()); }} placeholder="you@example.com" /></label>
          {isInternationalRoute && <p className="form-hint">Demo only: passport number is not collected. Check official entry requirements before travel.</p>}
          {error && <ErrorNote message={error} />}{!session && <p className="form-hint">You’ll sign in before creating your booking.</p>}
          <div className="fare-summary"><span>Outbound / passenger · all-inclusive demo fare</span><b>{money(flight.price, flight.currency)}</b>{returnFlight && <><span>Return / passenger · all-inclusive demo fare</span><b>{money(returnFlight.price, returnFlight.currency)}</b></>}<span>Passengers · {travelClass.replace("_", " ")}</span><b>{passengerCount} · ×{classMultiplier}</b><span>Selected outbound seats</span><b>{selectedSeats.length ? selectedSeats.join(", ") : "not selected"}</b>{returnFlight && <><span>Selected return seats</span><b>{selectedReturnSeats.length ? selectedReturnSeats.join(", ") : "not selected"}</b></>}<span>Estimated total · demo fare</span><b>{money(estimatedTotal, flight.currency)}</b></div>
          <button className="primary-button full" disabled={busy || flight.status !== "SCHEDULED"}>{busy ? "Creating booking…" : <>Continue to payment <ArrowRight size={17} /></>}</button><p className="safe-note"><LockKeyhole size={13} /> {isInternationalRoute ? "Demo itinerary · no airline ticket issued" : "Demo checkout · no airline inventory"}</p></form></aside>
      </div>
    </section>
  );
}

function AuthPage({ onSession }: { onSession: (session: Session) => void }) {
  const navigate = useNavigate();
  const location = useLocation();
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
      onSession(session);
      const requestedPath = (location.state as { from?: string } | null)?.from;
      navigate(requestedPath ?? (session.user.role === "ADMIN" ? "/admin" : "/bookings"), { replace: true });
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
    let active = true;
    setLoading(true);
    setError("");
    api.bookings().then(async (bookingsData) => {
      const flightIds = [...new Set(bookingsData.flatMap((booking) => [
        booking.flightId,
        ...(booking.tripDetails?.returnFlightId ? [booking.tripDetails.returnFlightId] : []),
      ]))];
      const flightsData = await Promise.all(flightIds.map((flightId) => api.flight(flightId)));
      if (active) { setBookings(bookingsData); setFlights(flightsData); }
    }).catch((e: Error) => { if (active) setError(e.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [refresh]);
  const flightById = useMemo(() => new Map(flights.map((flight) => [flight.id, flight])), [flights]);
  async function cancel(id: string) {
    if (!window.confirm("Cancel this trip? A demo payment is recorded as refunded; Razorpay Test Mode bookings request a test refund.")) return;
    try { await api.cancelBooking(id); setRefresh((value) => value + 1); }
    catch (e) { setError((e as Error).message); }
  }
  return <section className="content-page"><div className="page-kicker"><span className="eyebrow-line" /> YOUR JOURNEY, ALL IN ONE PLACE</div><div className="page-heading-row"><div><h1>My trips</h1><p>Your plans, tickets, and everything in between.</p></div><Link to="/flights" className="primary-button">Find a flight <ArrowRight size={16} /></Link></div>{loading ? <Loading /> : error ? <ErrorNote message={error} /> : bookings.length ? <div className="booking-list">{bookings.map((booking) => {
    const outbound = flightById.get(booking.flightId);
    const returnFlight = booking.tripDetails?.returnFlightId ? flightById.get(booking.tripDetails.returnFlightId) : undefined;
    const seats = booking.tripDetails?.seatNumbers?.length ? booking.tripDetails.seatNumbers : [booking.seatNumber];
    return <article className="booking-card" key={booking.id}><div className="booking-status"><span className={`status-pill status-${booking.status.toLowerCase()}`}>{booking.status.replace("_", " ")}</span><span className="pnr">BOOKING REF · {booking.pnr}</span></div><div className="booking-info"><div><span className="eyebrow muted">{returnFlight ? "ROUND TRIP" : "FLIGHT"}</span><h3>{outbound?.originCode ?? "—"} <ArrowRight size={18} /> {outbound?.destinationCode ?? "—"}</h3><p>{outbound ? day(outbound.departure, outbound.originTimeZone) : "Flight details"} · Seats {seats.join(", ")}</p>{returnFlight && <p>Return · {returnFlight.originCode} → {returnFlight.destinationCode} · {day(returnFlight.departure, returnFlight.originTimeZone)} · Seats {booking.tripDetails?.returnSeatNumbers.join(", ")}</p>}<p>{booking.tripDetails?.passengers.length ?? 1} passenger(s) · {(booking.tripDetails?.travelClass ?? "ECONOMY").replace("_", " ")}</p>{booking.status === "PENDING_PAYMENT" && booking.expiresAt && <p>Seat hold expires {new Date(booking.expiresAt).toLocaleTimeString()}.</p>}{booking.status === "EXPIRED" && <p>This seat hold expired. Start a new booking to select current availability.</p>}</div><div className="booking-amount"><small>Paid / due</small><b>{money(booking.amount, booking.currency)}</b></div></div><div className="booking-actions">{booking.status === "CONFIRMED" && <Link to={`/ticket/${booking.id}`} className="text-link">View itinerary <ArrowUpRight size={16} /></Link>}{booking.status === "PENDING_PAYMENT" && <Link to={`/checkout/${booking.id}`} className="text-link">Complete payment <ArrowRight size={16} /></Link>}{(booking.status === "CONFIRMED" || booking.status === "PENDING_PAYMENT") && <button className="cancel-button" onClick={() => cancel(booking.id)}>Cancel trip</button>}</div></article>;
  })}</div> : <div className="empty-state"><Ticket size={30} /><h3>Your next trip belongs here</h3><p>You haven’t booked any journeys yet. Let’s find somewhere worth going.</p><Link className="primary-button" to="/flights">Explore flights <ArrowRight size={16} /></Link></div>}</section>;
}

function CheckoutPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [returnFlight, setReturnFlight] = useState<Flight | null>(null);
  const [order, setOrder] = useState<Order | null>(null);
  const [paymentMethod, setPaymentMethod] = useState("UPI");
  const [paymentReference, setPaymentReference] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    api.bookings().then(async (items) => {
      const found = items.find((item) => item.id === id) ?? null;
      setBooking(found);
      if (found?.tripDetails?.returnFlightId) {
        setReturnFlight(await api.flight(found.tripDetails.returnFlightId));
      }
    }).catch((e: Error) => setError(e.message)).finally(() => setLoading(false));
  }, [id]);
  async function pay() {
    setBusy(true); setError("");
    try {
      const order: Order = await api.order(id);
      setOrder(order);
      if (order.demoMode) { setBusy(false); return; }
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
  async function completeDemo(succeed: boolean) {
    setBusy(true); setError("");
    try {
      const result = await api.demoPayment(id, paymentMethod, succeed, crypto.randomUUID());
      setPaymentReference(result.reference);
      if (result.status === "SUCCEEDED") navigate(`/ticket/${id}`, { replace: true });
      else setError("Demo payment declined. Your seat is still held; try another demo attempt.");
    } catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  if (loading) return <Loading />;
  if (!booking) return <section className="content-page"><ErrorNote message={error || "Booking not found."} /></section>;
  return <section className="content-page checkout-page"><div className="page-kicker"><span className="eyebrow-line" /> ONE LAST STEP</div><div className="checkout-card"><span className="checkout-icon"><LockKeyhole size={23} /></span><span className="eyebrow muted">{order?.demoMode ? "EDUCATIONAL PAYMENT SIMULATOR" : "SECURE CHECKOUT"}</span><h1>Your seat is waiting.</h1><p>{order?.demoMode ? "This demo flow records a simulated result only. It never requests payment credentials or moves money." : "Complete payment securely with Razorpay to confirm your booking and issue your e-ticket."}</p><div className="checkout-line"><span>Outbound · passenger</span><b>{booking.passengerName} · {booking.seatNumber}</b></div>{returnFlight && <div className="checkout-line"><span>Return · seats</span><b>{returnFlight.originCode} → {returnFlight.destinationCode} · {booking.tripDetails?.returnSeatNumbers.join(", ")}</b></div>}<div className="checkout-line"><span>Travellers · class · booking ref</span><b>{booking.tripDetails?.passengers.length ?? 1} · {(booking.tripDetails?.travelClass ?? "ECONOMY").replace("_", " ")} · {booking.pnr}</b></div><div className="checkout-total"><span>Total · taxes included</span><b>{money(booking.amount, booking.currency)}</b></div>{order?.demoMode ? <div className="demo-checkout"><div className="demo-payment-notice"><ShieldCheck size={17} /><span><b>DEMO PAYMENT — NO REAL MONEY</b><small>Choose a demonstration method. No card numbers, CVVs, PINs, or bank details are collected.</small></span></div><div className="demo-methods" role="radiogroup" aria-label="Demo payment method">{[["UPI", "UPI"], ["CARD", "Debit / credit card"], ["NET_BANKING", "Net banking"], ["WALLET", "Wallet"]].map(([value, label]) => <button type="button" role="radio" aria-checked={paymentMethod === value} className={paymentMethod === value ? "demo-method selected" : "demo-method"} key={value} onClick={() => setPaymentMethod(value)}>{label}</button>)}</div>{paymentReference && <small className="demo-reference">Last attempt · {paymentReference}</small>}{error && <ErrorNote message={error} />}<div className="demo-actions"><button type="button" className="outline-button" disabled={busy} onClick={() => completeDemo(false)}>Simulate failure</button><button type="button" className="primary-button" disabled={busy} onClick={() => completeDemo(true)}>{busy ? "Processing demo…" : "Simulate payment success"} <ArrowRight size={16} /></button></div></div> : <>{error && <ErrorNote message={error} />}<button className="primary-button full" onClick={pay} disabled={busy || booking.status !== "PENDING_PAYMENT"}>{busy ? "Opening secure checkout…" : booking.status === "CONFIRMED" ? "Payment complete" : order ? "Retry Razorpay checkout" : "Continue to payment"} <ArrowRight size={17} /></button><p className="safe-note"><ShieldCheck size={14} /> {order ? "Razorpay Test Mode" : "Payment mode is shown before checkout."}</p></>}</div></section>;
}

function TicketPage() {
  const { id = "" } = useParams();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [flight, setFlight] = useState<Flight | null>(null);
  const [returnFlight, setReturnFlight] = useState<Flight | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    api.bookings().then((items) => {
      const found = items.find((item) => item.id === id);
      if (!found) throw new Error("Ticket not found.");
      setBooking(found);
      return Promise.all([
        api.flight(found.flightId),
        found.tripDetails?.returnFlightId ? api.flight(found.tripDetails.returnFlightId) : Promise.resolve(null),
      ]);
    }).then(([outbound, returning]) => { setFlight(outbound); setReturnFlight(returning); })
      .catch((e: Error) => setError(e.message));
  }, [id]);
  if (error) return <section className="content-page"><ErrorNote message={error} /></section>;
  if (!booking || !flight) return <Loading />;
  const passengerList = booking.tripDetails?.passengers.length
    ? booking.tripDetails.passengers.map((passenger) => passenger.name) : [booking.passengerName];
  const outboundSeats = booking.tripDetails?.seatNumbers.length
    ? booking.tripDetails.seatNumbers : [booking.seatNumber];
  const downloadItinerary = () => {
    const lines = [
      "JET SET GO — DEMO ITINERARY",
      `Booking reference: ${booking.pnr}`,
      `Status: ${booking.status}`,
      `Passengers: ${passengerList.join(", ")}`,
      `Class: ${(booking.tripDetails?.travelClass ?? "ECONOMY").replace("_", " ")}`,
      `Outbound: ${flight.originCode} ${flight.originCity} → ${flight.destinationCode} ${flight.destinationCity}`,
      `Departure: ${day(flight.departure, flight.originTimeZone)} ${clock(flight.departure, flight.originTimeZone)}`,
      `Seats: ${outboundSeats.join(", ")}`,
      ...(returnFlight ? [
        `Return: ${returnFlight.originCode} ${returnFlight.originCity} → ${returnFlight.destinationCode} ${returnFlight.destinationCity}`,
        `Departure: ${day(returnFlight.departure, returnFlight.originTimeZone)} ${clock(returnFlight.departure, returnFlight.originTimeZone)}`,
        `Seats: ${booking.tripDetails?.returnSeatNumbers.join(", ") ?? ""}`,
      ] : []),
      `Total: ${money(booking.amount, booking.currency)}`,
      "DEMO ITINERARY — NOT AN AIRLINE-ISSUED TICKET OR REAL AIRLINE PNR.",
    ];
    const url = URL.createObjectURL(new Blob([lines.join("\n")], { type: "text/plain;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `jet-set-go-${booking.pnr}.txt`;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  };
  return <section className="content-page ticket-page"><div className="ticket-success"><span><Check size={21} /></span><div><b>{booking.status === "CONFIRMED" ? "You’re all set." : "Booking received."}</b><small>{booking.status === "CONFIRMED" ? "Your demo itinerary is confirmed. Have a beautiful trip." : "Payment is still pending. Complete checkout to confirm your demo itinerary."}</small></div></div><article className="ticket"><div className="ticket-top"><div className="brand mini"><BrandLockup boarding /></div><div className="ticket-status">{booking.status.replace("_", " ")}</div></div><div className="ticket-route"><div><small>FROM</small><b>{flight.originCode}</b><span>{flight.originCity}</span></div><div className="ticket-flight"><Plane size={19} /><span>{flight.flightNumber} · DEMO</span></div><div className="ticket-destination"><small>TO</small><b>{flight.destinationCode}</b><span>{flight.destinationCity}</span></div></div>{returnFlight && <div className="ticket-route ticket-return-route"><div><small>RETURN FROM</small><b>{returnFlight.originCode}</b><span>{returnFlight.originCity}</span></div><div className="ticket-flight"><Plane size={19} /><span>{returnFlight.flightNumber} · DEMO</span></div><div className="ticket-destination"><small>RETURN TO</small><b>{returnFlight.destinationCode}</b><span>{returnFlight.destinationCity}</span></div></div>}<div className="ticket-details"><div><small>PASSENGERS</small><b>{passengerList.join(", ")}</b></div><div><small>OUTBOUND DATE & TIME</small><b>{day(flight.departure, flight.originTimeZone)} · {clock(flight.departure, flight.originTimeZone)}</b></div><div><small>SEATS · CLASS</small><b>{outboundSeats.join(", ")} · {(booking.tripDetails?.travelClass ?? "ECONOMY").replace("_", " ")}</b></div><div><small>BOOKING REFERENCE</small><b>{booking.pnr}</b></div>{returnFlight && <><div><small>RETURN DATE & TIME</small><b>{day(returnFlight.departure, returnFlight.originTimeZone)} · {clock(returnFlight.departure, returnFlight.originTimeZone)}</b></div><div><small>RETURN SEATS</small><b>{booking.tripDetails?.returnSeatNumbers.join(", ")}</b></div></>}</div><div className="ticket-bottom"><span>DEMO ITINERARY · NOT AN AIRLINE-ISSUED TICKET</span><span>{money(booking.amount, booking.currency)}</span></div></article><div className="ticket-actions"><button className="primary-button" onClick={downloadItinerary}>Download itinerary <Download size={16} /></button><button className="outline-button" onClick={() => window.print()}>Print / save as PDF</button><Link to="/bookings" className="outline-button">Back to my trips</Link></div></section>;
}

function AdminPage() {
  const [summary, setSummary] = useState<AdminSummary | null>(null);
  const [flights, setFlights] = useState<Flight[]>([]);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [routes, setRoutes] = useState<RouteTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [refresh, setRefresh] = useState(0);
  const [editingFlightId, setEditingFlightId] = useState("");
  const [flightSearch, setFlightSearch] = useState("");
  const [flightDateFilter, setFlightDateFilter] = useState("");
  const [bookingStatusFilter, setBookingStatusFilter] = useState("");
  const [editingRouteId, setEditingRouteId] = useState("");
  const [routeForm, setRouteForm] = useState({
    airline: "JetSetGo Demonstration Air", flightNumber: "JG 2001",
    originCode: "DEL", originCity: "Delhi", destinationCode: "BOM", destinationCity: "Mumbai",
    departureLocalTime: "10:30", durationMinutes: "130", operatingDays: [1, 2, 3, 4, 5, 6, 7],
    originTimeZone: "Asia/Kolkata", destinationTimeZone: "Asia/Kolkata",
    baseFare: "5000", currency: "INR", capacity: "48", aircraft: "Illustrative narrow-body aircraft",
  });
  const [form, setForm] = useState({
    airline: "", flightNumber: "", originCode: "", originCity: "",
    destinationCode: "", destinationCity: "", departure: "", arrival: "",
    price: "", capacity: "48", originTimeZone: "Asia/Kolkata",
    destinationTimeZone: "Asia/Kolkata", aircraft: "", originTerminal: "", destinationTerminal: "",
  });
  useEffect(() => {
    setLoading(true);
    Promise.all([api.adminSummary(), api.adminFlights(), api.adminBookings(), api.adminUsers(), api.adminRoutes()])
      .then(([nextSummary, nextFlights, nextBookings, nextUsers, nextRoutes]) => {
        setSummary(nextSummary); setFlights(nextFlights); setBookings(nextBookings); setUsers(nextUsers); setRoutes(nextRoutes);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [refresh]);

  function updateForm(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }
  async function addFlight(event: React.FormEvent) {
    event.preventDefault(); setError(""); setNotice("");
    try {
      const payload = {
        ...form,
        departure: new Date(form.departure).toISOString(),
        arrival: new Date(form.arrival).toISOString(),
        price: Number(form.price),
        capacity: Number(form.capacity),
      };
      if (editingFlightId) await api.updateFlight(editingFlightId, payload);
      else await api.createFlight(payload);
      setNotice(editingFlightId ? "Flight updated." : "Demo flight added; seat inventory will initialize on demand.");
      setEditingFlightId("");
      setForm({ airline: "", flightNumber: "", originCode: "", originCity: "",
        destinationCode: "", destinationCity: "", departure: "", arrival: "", price: "", capacity: "48",
        originTimeZone: "Asia/Kolkata", destinationTimeZone: "Asia/Kolkata",
        aircraft: "", originTerminal: "", destinationTerminal: "" });
      setRefresh((value) => value + 1);
    } catch (e) { setError((e as Error).message); }
  }
  async function setFlightStatus(id: string, status: string) {
    setError(""); setNotice("");
    try { await api.updateFlightStatus(id, status); setRefresh((value) => value + 1); }
    catch (e) { setError((e as Error).message); }
  }
  async function removeFlight(flight: Flight) {
    if (!window.confirm(`Delete ${flight.flightNumber}? Flights with any booking history cannot be deleted.`)) return;
    setError(""); setNotice("");
    try { await api.deleteFlight(flight.id); setNotice("Flight removed."); setRefresh((value) => value + 1); }
    catch (e) { setError((e as Error).message); }
  }
  function editFlight(flight: Flight) {
    if (flight.routeTemplateId) {
      setError("This flight is generated from a recurring route. Edit its route template instead.");
      document.getElementById("route-template-form")?.scrollIntoView({ behavior: "smooth", block: "start" });
      return;
    }
    setEditingFlightId(flight.id);
    setForm({
      airline: flight.airline, flightNumber: flight.flightNumber,
      originCode: flight.originCode, originCity: flight.originCity,
      destinationCode: flight.destinationCode, destinationCity: flight.destinationCity,
      departure: toLocalDateTimeInput(flight.departure), arrival: toLocalDateTimeInput(flight.arrival),
      price: String(flight.price), capacity: String(flight.capacity),
      originTimeZone: flight.originTimeZone ?? "Asia/Kolkata",
      destinationTimeZone: flight.destinationTimeZone ?? "Asia/Kolkata",
      aircraft: flight.aircraft ?? "", originTerminal: flight.originTerminal ?? "",
      destinationTerminal: flight.destinationTerminal ?? "",
    });
    document.getElementById("flight-form")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }
  async function cancelAdminBooking(booking: Booking) {
    if (!window.confirm(`Cancel booking ${booking.pnr}? A demo refund is recorded or a Razorpay Test Mode refund is requested.`)) return;
    setError(""); setNotice("");
    try { await api.cancelAdminBooking(booking.id); setNotice(`Booking ${booking.pnr} cancelled.`); setRefresh((value) => value + 1); }
    catch (e) { setError((e as Error).message); }
  }
  function updateRouteForm(field: keyof typeof routeForm, value: string) {
    setRouteForm((current) => ({ ...current, [field]: value }));
  }
  function editRoute(route: RouteTemplate) {
    setEditingRouteId(route.id);
    setRouteForm({
      airline: route.airline, flightNumber: route.flightNumber,
      originCode: route.originCode, originCity: route.originCity,
      destinationCode: route.destinationCode, destinationCity: route.destinationCity,
      departureLocalTime: route.departureLocalTime, durationMinutes: String(route.durationMinutes),
      operatingDays: route.operatingDays, originTimeZone: route.originTimeZone,
      destinationTimeZone: route.destinationTimeZone, baseFare: String(route.baseFare),
      currency: route.currency, capacity: String(route.capacity), aircraft: route.aircraft ?? "",
    });
    document.getElementById("route-template-form")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }
  async function saveRoute(event: React.FormEvent) {
    event.preventDefault(); setError(""); setNotice("");
    const payload = {
      ...routeForm,
      durationMinutes: Number(routeForm.durationMinutes),
      baseFare: Number(routeForm.baseFare),
      capacity: Number(routeForm.capacity),
    };
    try {
      if (editingRouteId) await api.updateRoute(editingRouteId, payload);
      else await api.createRoute(payload);
      setNotice(editingRouteId
        ? "Route updated. Eligible unbooked future flights were refreshed."
        : "Recurring route saved. Future demo flights are being scheduled.");
      setEditingRouteId("");
      setRefresh((value) => value + 1);
    } catch (e) { setError((e as Error).message); }
  }
  async function toggleRoute(route: RouteTemplate) {
    setError(""); setNotice("");
    try {
      await api.setRouteActive(route.id, !route.active);
      setNotice(route.active ? "Route disabled. Booked flights were preserved." : "Route enabled.");
      setRefresh((value) => value + 1);
    } catch (e) { setError((e as Error).message); }
  }
  if (loading) return <Loading />;
  const visibleAdminFlights = flights.filter((flight) => {
    const term = flightSearch.trim().toLowerCase();
    const matchesText = !term || [flight.flightNumber, flight.originCode, flight.destinationCode,
      flight.airline, flight.originCity, flight.destinationCity].some((value) => value.toLowerCase().includes(term));
    const localDate = new Date(flight.departure).toLocaleDateString("en-CA", {
      timeZone: flight.originTimeZone ?? undefined,
    });
    return matchesText && (!flightDateFilter || localDate === flightDateFilter);
  });
  const visibleAdminBookings = bookings.filter((booking) =>
    !bookingStatusFilter || booking.status === bookingStatusFilter);
  return <section className="content-page admin-page">
    <div className="page-kicker"><span className="eyebrow-line" /> JET SET GO · OPERATIONS</div>
    <div className="page-heading-row"><div><h1>A clear view of the journey.</h1><p>Demo operations dashboard. Changes here apply to the connected database.</p></div><span className="admin-mode"><LayoutDashboard size={14} /> ADMIN</span></div>
    {error && <ErrorNote message={error} />}{notice && <div className="success-note" role="status">{notice}</div>}
    {summary && <div className="admin-metrics">
      <article><span><Plane size={16} /></span><small>DEMO FLIGHTS</small><b>{summary.totalFlights}</b></article>
      <article><span><Plane size={16} /></span><small>ACTIVE · UPCOMING</small><b>{summary.activeFlights}</b></article>
      <article><span><Clock3 size={16} /></span><small>DEPARTURES · 24 HOURS</small><b>{summary.upcomingDepartures}</b></article>
      <article><span><Ticket size={16} /></span><small>ALL BOOKINGS</small><b>{summary.totalBookings}</b></article>
      <article><span><Check size={16} /></span><small>CONFIRMED BOOKINGS</small><b>{summary.confirmedBookings}</b></article>
      <article><span><Users size={16} /></span><small>REGISTERED USERS</small><b>{summary.registeredUsers}</b></article>
      <article><span><BarChart3 size={16} /></span><small>CONFIRMED BOOKING VALUE</small><b>{money(summary.confirmedRevenue)}</b></article>
    </div>}
    {summary && <div className="admin-pulse"><div><span>Seat occupancy · upcoming capacity</span><b>{summary.seatOccupancy.toFixed(1)}%</b></div><div className="admin-bar"><span style={{ width: `${summary.seatOccupancy}%` }} /></div><small>Cancelled bookings · {summary.cancelledBookings} · simulated revenue only</small></div>}
    <section className="admin-panel"><div className="admin-section-heading"><div><span className="eyebrow muted">SCHEDULE MANAGEMENT</span><h2>Flights</h2></div><span className="demo-tag">DEMO DATA</span></div>
      <div className="admin-flight-filters"><label className="admin-search">Find by flight, airline, city, or airport<input value={flightSearch} onChange={(event) => setFlightSearch(event.target.value)} placeholder="e.g. JG 101 or DEL" /></label><label className="admin-search">Departure date<input type="date" value={flightDateFilter} onChange={(event) => setFlightDateFilter(event.target.value)} /></label></div>
      {visibleAdminFlights.length ? <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>FLIGHT</th><th>ROUTE</th><th>DEPARTURE</th><th>FARE</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody>{visibleAdminFlights.map((flight) => <tr key={flight.id}><td><b>{flight.flightNumber}</b><small>{flight.airline} · {flight.aircraft || "Aircraft not configured"}</small></td><td>{flight.originCode} <ArrowRight size={13} /> {flight.destinationCode}<small>{flight.originTerminal || "Terminal not configured"} → {flight.destinationTerminal || "Terminal not configured"}</small></td><td>{day(flight.departure, flight.originTimeZone)} · {clock(flight.departure, flight.originTimeZone)}</td><td>{money(flight.price, flight.currency)}</td><td><select value={flight.status} onChange={(event) => setFlightStatus(flight.id, event.target.value)} aria-label={`Set ${flight.flightNumber} status`}>{Array.from(new Set([flight.status, ...(flight.status === "SCHEDULED" ? ["DELAYED", "CANCELLED"] : flight.status === "DELAYED" ? ["SCHEDULED", "CANCELLED"] : flight.status === "DEPARTED" ? ["COMPLETED"] : [])])).map((status) => <option key={status}>{status}</option>)}</select></td><td><div className="admin-route-actions"><button className="outline-button" type="button" onClick={() => editFlight(flight)}>{flight.routeTemplateId ? "Template" : "Edit"}</button><button className="admin-delete" aria-label={`Delete ${flight.flightNumber}`} disabled={Boolean(flight.routeTemplateId)} onClick={() => removeFlight(flight)}><Trash2 size={15} /></button></div></td></tr>)}</tbody></table></div> : <div className="admin-empty">No flights match this search.</div>}
    </section>
    <section className="admin-panel"><div className="admin-section-heading"><div><span className="eyebrow muted">RECURRING DEMO ROUTES</span><h2>Route templates</h2></div><span className="form-hint">Creates rolling 120-day schedules</span></div>
      {routes.length ? <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>ROUTE</th><th>FLIGHT</th><th>DEPARTURE · TIMEZONE</th><th>OPERATES</th><th>FARE</th><th /></tr></thead><tbody>{routes.map((route) => <tr key={route.id}><td>{route.originCode} → {route.destinationCode}<small>{route.originCity} to {route.destinationCity}</small></td><td><b>{route.flightNumber}</b><small>{route.airline} · {route.aircraft || "Aircraft not configured"}</small></td><td>{route.departureLocalTime}<small>{route.originTimeZone} · {route.durationMinutes} min</small></td><td>{route.operatingDays.length === 7 ? "Daily" : route.operatingDays.join(", ")}</td><td>{money(route.baseFare, route.currency)}</td><td className="admin-route-actions"><button className="outline-button" type="button" onClick={() => editRoute(route)}>Edit</button><button className="outline-button" type="button" onClick={() => toggleRoute(route)}>{route.active ? "Disable" : "Enable"}</button></td></tr>)}</tbody></table></div> : <div className="admin-empty">No recurring route templates.</div>}
      <form className="admin-flight-form" id="route-template-form" onSubmit={saveRoute}>
        <label>Airline<input required maxLength={100} value={routeForm.airline} onChange={(event) => updateRouteForm("airline", event.target.value)} /></label>
        <label>Flight number<input required maxLength={12} value={routeForm.flightNumber} onChange={(event) => updateRouteForm("flightNumber", event.target.value)} /></label>
        <label>Origin IATA<input required pattern="[A-Za-z]{3}" maxLength={3} value={routeForm.originCode} onChange={(event) => updateRouteForm("originCode", event.target.value.toUpperCase())} /></label>
        <label>Origin city<input required maxLength={100} value={routeForm.originCity} onChange={(event) => updateRouteForm("originCity", event.target.value)} /></label>
        <label>Destination IATA<input required pattern="[A-Za-z]{3}" maxLength={3} value={routeForm.destinationCode} onChange={(event) => updateRouteForm("destinationCode", event.target.value.toUpperCase())} /></label>
        <label>Destination city<input required maxLength={100} value={routeForm.destinationCity} onChange={(event) => updateRouteForm("destinationCity", event.target.value)} /></label>
        <label>Local departure<input required type="time" value={routeForm.departureLocalTime} onChange={(event) => updateRouteForm("departureLocalTime", event.target.value)} /></label>
        <label>Duration (minutes)<input required type="number" min="20" max="1800" value={routeForm.durationMinutes} onChange={(event) => updateRouteForm("durationMinutes", event.target.value)} /></label>
        <label>Origin IANA timezone<input required value={routeForm.originTimeZone} onChange={(event) => updateRouteForm("originTimeZone", event.target.value)} placeholder="Asia/Kolkata" /></label>
        <label>Destination IANA timezone<input required value={routeForm.destinationTimeZone} onChange={(event) => updateRouteForm("destinationTimeZone", event.target.value)} placeholder="Asia/Dubai" /></label>
        <label>Base demo fare<input required type="number" min="1" step="1" value={routeForm.baseFare} onChange={(event) => updateRouteForm("baseFare", event.target.value)} /></label>
        <label>Currency<input required pattern="[A-Za-z]{3}" maxLength={3} value={routeForm.currency} onChange={(event) => updateRouteForm("currency", event.target.value.toUpperCase())} /></label>
        <label>Capacity<input required type="number" min="6" max="360" value={routeForm.capacity} onChange={(event) => updateRouteForm("capacity", event.target.value)} /></label>
        <label>Aircraft<input maxLength={100} value={routeForm.aircraft} onChange={(event) => updateRouteForm("aircraft", event.target.value)} /></label>
        <fieldset className="route-days"><legend>Operating days · 1 Monday through 7 Sunday</legend>{[1, 2, 3, 4, 5, 6, 7].map((dayNumber) => <label key={dayNumber}><input type="checkbox" checked={routeForm.operatingDays.includes(dayNumber)} onChange={(event) => setRouteForm((current) => ({ ...current, operatingDays: event.target.checked ? [...current.operatingDays, dayNumber].sort() : current.operatingDays.filter((day) => day !== dayNumber) }))} />{["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][dayNumber - 1]}</label>)}</fieldset>
        <div className="admin-route-actions"><button className="primary-button admin-submit" type="submit"><Plus size={16} /> {editingRouteId ? "Save route changes" : "Create recurring route"}</button>{editingRouteId && <button className="outline-button" type="button" onClick={() => setEditingRouteId("")}>Cancel edit</button>}</div>
      </form>
    </section>
    <section className="admin-panel"><div className="admin-section-heading"><div><span className="eyebrow muted">{editingFlightId ? "EDIT FUTURE FLIGHT" : "ADD TO THE DEMO SCHEDULE"}</span><h2>{editingFlightId ? "Update flight" : "New flight"}</h2></div><span className="form-hint">Times use your browser’s local timezone.</span></div>
      <form className="admin-flight-form" id="flight-form" onSubmit={addFlight}>
        <label>Airline<input required maxLength={100} value={form.airline} onChange={(event) => updateForm("airline", event.target.value)} /></label>
        <label>Flight number<input required maxLength={12} value={form.flightNumber} onChange={(event) => updateForm("flightNumber", event.target.value)} placeholder="e.g. 6E 204" /></label>
        <label>Origin IATA<input required pattern="[A-Za-z]{3}" maxLength={3} value={form.originCode} onChange={(event) => updateForm("originCode", event.target.value.toUpperCase())} placeholder="DEL" /></label>
        <label>Origin city<input required maxLength={100} value={form.originCity} onChange={(event) => updateForm("originCity", event.target.value)} /></label>
        <label>Destination IATA<input required pattern="[A-Za-z]{3}" maxLength={3} value={form.destinationCode} onChange={(event) => updateForm("destinationCode", event.target.value.toUpperCase())} placeholder="BOM" /></label>
        <label>Destination city<input required maxLength={100} value={form.destinationCity} onChange={(event) => updateForm("destinationCity", event.target.value)} /></label>
        <label>Departure<input required type="datetime-local" value={form.departure} onChange={(event) => updateForm("departure", event.target.value)} /></label>
        <label>Arrival<input required type="datetime-local" value={form.arrival} onChange={(event) => updateForm("arrival", event.target.value)} /></label>
        <label>Fare (INR)<input required type="number" min="1" step="1" value={form.price} onChange={(event) => updateForm("price", event.target.value)} /></label>
        <label>Seat capacity<input required type="number" min="6" max="360" step="1" value={form.capacity} onChange={(event) => updateForm("capacity", event.target.value)} /></label>
        <label>Aircraft<input maxLength={100} value={form.aircraft} onChange={(event) => updateForm("aircraft", event.target.value)} /></label>
        <label>Origin terminal<input maxLength={40} value={form.originTerminal} onChange={(event) => updateForm("originTerminal", event.target.value)} /></label>
        <label>Destination terminal<input maxLength={40} value={form.destinationTerminal} onChange={(event) => updateForm("destinationTerminal", event.target.value)} /></label>
        <label>Origin timezone<input value={form.originTimeZone} onChange={(event) => updateForm("originTimeZone", event.target.value)} placeholder="Asia/Kolkata" /></label>
        <label>Destination timezone<input value={form.destinationTimeZone} onChange={(event) => updateForm("destinationTimeZone", event.target.value)} placeholder="Asia/Dubai" /></label>
        <div className="admin-route-actions"><button className="primary-button admin-submit" type="submit"><Plus size={16} /> {editingFlightId ? "Save flight changes" : "Add demo flight"}</button>{editingFlightId && <button className="outline-button" type="button" onClick={() => setEditingFlightId("")}>Cancel edit</button>}</div>
      </form>
    </section>
    <section className="admin-panel"><div className="admin-section-heading"><div><span className="eyebrow muted">BOOKING MANAGEMENT</span><h2>Bookings</h2></div><label className="admin-filter">Status<select value={bookingStatusFilter} onChange={(event) => setBookingStatusFilter(event.target.value)}><option value="">All statuses</option>{["PENDING_PAYMENT", "PAYMENT_PROCESSING", "CONFIRMED", "CANCELLING", "CANCELLED", "EXPIRED"].map((status) => <option key={status}>{status}</option>)}</select></label></div>
      {visibleAdminBookings.length ? <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>REFERENCE</th><th>PASSENGER</th><th>FLIGHT</th><th>VALUE</th><th>STATUS</th><th /></tr></thead><tbody>{visibleAdminBookings.slice(0, 50).map((booking) => <tr key={booking.id}><td><b>{booking.pnr}</b></td><td>{booking.passengerName}<small>{booking.passengerEmail}</small></td><td>{flights.find((flight) => flight.id === booking.flightId)?.flightNumber ?? "—"}</td><td>{money(booking.amount, booking.currency)}</td><td><span className={`status-pill status-${booking.status.toLowerCase()}`}>{booking.status.replace("_", " ")}</span></td><td>{["CONFIRMED", "PENDING_PAYMENT"].includes(booking.status) && <button className="cancel-button" onClick={() => cancelAdminBooking(booking)}>Cancel booking</button>}</td></tr>)}</tbody></table></div> : <div className="admin-empty">No bookings match this status.</div>}
    </section>
    <section className="admin-panel"><div className="admin-section-heading"><div><span className="eyebrow muted">ACCOUNT DIRECTORY</span><h2>Users</h2></div></div>
      {users.length ? <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>NAME</th><th>EMAIL</th><th>ROLE</th></tr></thead><tbody>{users.map((user) => <tr key={user.id}><td>{user.name}</td><td>{user.email}</td><td>{user.role}</td></tr>)}</tbody></table></div> : <div className="admin-empty">No users yet.</div>}
    </section>
  </section>;
}

function LoginPrompt() { return <section className="content-page prompt-page"><UserRound size={31} /><h2>Your journeys, all together.</h2><p>Sign in to see bookings and tickets made with Jet Set Go.</p><Link to="/login" className="primary-button">Sign in <ArrowRight size={16} /></Link></section>; }
function AccessDenied() { return <section className="content-page prompt-page"><LockKeyhole size={31} /><h2>Admin access only.</h2><p>This operations area is available to authorized administrators.</p><Link to="/" className="primary-button">Back to Jet Set Go <ArrowRight size={16} /></Link></section>; }
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
function BrandLockup({ boarding = false }: { boarding?: boolean }) {
  return <>
    <span className="brand-mark"><img src="/jetsetgo-mark.svg" alt="" /></span>
    <span className="brand-copy"><span className="brand-wordmark">jet set <b>go</b></span><small>{boarding ? "BOARDING PASS" : "TRAVEL, BEAUTIFULLY"}</small></span>
  </>;
}
function Footer() { return <footer className="site-footer"><Link to="/" className="brand" aria-label="Jet Set Go home"><BrandLockup /></Link><span>Made for wherever life takes you.</span><span>© {new Date().getFullYear()} Jet Set Go</span></footer>; }
