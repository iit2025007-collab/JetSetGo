import { useEffect, useId, useRef, useState } from "react";
import { MapPin, X } from "lucide-react";
import { api } from "../api";
import type { Airport } from "../types";

type Props = {
  label: string;
  selected: Airport | null;
  countryCode?: string;
  excludeCode?: string;
  onChange: (airport: Airport | null) => void;
};

export default function AirportCombobox({ label, selected, countryCode, excludeCode, onChange }: Props) {
  const listId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [requestVersion, setRequestVersion] = useState(0);
  const [results, setResults] = useState<Airport[]>([]);
  const [loading, setLoading] = useState(false);
  const [warming, setWarming] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!open) return;
    let active = true;
    const controller = new AbortController();
    setLoading(true);
    setWarming(false);
    setError("");
    setResults([]);
    setActiveIndex(-1);
    const warmingTimeout = window.setTimeout(() => {
      if (active) setWarming(true);
    }, 2500);
    const requestTimeout = window.setTimeout(() => {
      controller.abort();
      if (active) {
        setLoading(false);
        setError("Airport search timed out while connecting to the flight service. Please retry.");
      }
    }, 90_000);
    const debounce = window.setTimeout(() => {
      api.airports(query, countryCode, controller.signal).then((items) => {
        if (active) { setResults(items.filter((airport) => airport.code !== excludeCode)); setError(""); }
      }).catch((failure: Error) => {
        if (active && !controller.signal.aborted) {
          setError(failure.message || "Airport search is unavailable. Check your connection and try again.");
        }
      }).finally(() => {
        window.clearTimeout(warmingTimeout);
        window.clearTimeout(requestTimeout);
        if (active && !controller.signal.aborted) {
          setLoading(false);
          setWarming(false);
        }
      });
    }, 250);
    return () => {
      active = false;
      window.clearTimeout(debounce);
      window.clearTimeout(warmingTimeout);
      window.clearTimeout(requestTimeout);
      controller.abort();
    };
  }, [countryCode, excludeCode, open, query, requestVersion]);

  function choose(airport: Airport) {
    onChange(airport);
    setQuery("");
    setActiveIndex(-1);
    setOpen(false);
  }

  return (
    <div className="search-field airport-combobox">
      <span>{label}</span>
      <input
        ref={inputRef}
        role="combobox"
        aria-label={`${label} airport`}
        aria-autocomplete="list"
        aria-expanded={open}
        aria-busy={loading}
        aria-controls={listId}
        aria-activedescendant={open && results[activeIndex] ? `${listId}-${results[activeIndex].code}` : undefined}
        autoComplete="off"
        placeholder="Type a city, airport or IATA code"
        value={selected ? `${selected.city} · ${selected.code}` : query}
        onFocus={(event) => {
          if (selected) event.currentTarget.select();
          setOpen(true);
        }}
        onChange={(event) => {
          onChange(null);
          setQuery(event.target.value);
          setActiveIndex(-1);
          setOpen(true);
        }}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown") {
            event.preventDefault();
            setOpen(true);
            setActiveIndex((index) => Math.min(index + 1, results.length - 1));
          } else if (event.key === "ArrowUp") {
            event.preventDefault();
            setActiveIndex((index) => index <= 0 ? results.length - 1 : index - 1);
          } else if (event.key === "Enter" && open && results[activeIndex]) {
            event.preventDefault();
            choose(results[activeIndex]);
          } else if (event.key === "Escape") {
            setOpen(false);
          }
        }}
        onBlur={() => window.setTimeout(() => setOpen(false), 120)}
      />
      {selected && (
        <button
          type="button"
          className="airport-clear"
          aria-label={`Clear ${label.toLowerCase()} airport`}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => {
            onChange(null);
            setQuery("");
            setOpen(true);
            inputRef.current?.focus();
          }}
        >
          <X size={14} />
        </button>
      )}
      {selected && <small className="airport-picked">{selected.name}</small>}
      {!selected && !query && <small className="airport-hint">Search by city, airport name, or IATA code</small>}
      {open && (
        <div className="airport-options" id={listId} role="listbox" aria-label={`${label} airport suggestions`}>
          {results.map((airport, index) => (
            <button
              type="button"
              role="option"
              aria-selected={index === activeIndex}
              id={`${listId}-${airport.code}`}
              className={`airport-option ${index === activeIndex ? "active" : ""}`}
              key={airport.code}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => choose(airport)}
            >
              <span className="airport-option-icon"><MapPin size={14} /></span>
              <span className="airport-option-copy">
                <b>{airport.city}</b>
                <small>{airport.name} · {[airport.stateOrRegion, airport.country].filter(Boolean).join(", ")}</small>
                <small className="airport-service-label">
                  {airport.airportType ? `${airport.airportType.replace("_", " ")} · ` : ""}
                  {airport.scheduledService === true ? "Scheduled service listed"
                    : airport.scheduledService === false ? "No scheduled service listed" : "Service data unavailable"}
                  {airport.dataSource === "OURAIRPORTS" ? " · OurAirports" : ""}
                </small>
              </span>
              <strong>{airport.code}</strong>
            </button>
          ))}
          {loading && <div className="airport-message" role="status">
            {warming ? "Waking the flight service; this may take a moment…" : "Searching airports…"}
          </div>}
          {!loading && !error && results.length === 0 && <div className="airport-message" role="status">No matching airports.</div>}
          {error && (
            <div className="airport-message airport-error" role="status">
              <span>{error}</span>
              <button
                type="button"
                className="airport-retry"
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => setRequestVersion((version) => version + 1)}
              >
                Retry
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
