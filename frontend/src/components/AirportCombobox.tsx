import { useEffect, useId, useState } from "react";
import { MapPin } from "lucide-react";
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
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [results, setResults] = useState<Airport[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!open) return;
    let active = true;
    const timeout = window.setTimeout(() => {
      setLoading(true);
      api.airports(query, countryCode).then((items) => {
        if (active) { setResults(items.filter((airport) => airport.code !== excludeCode)); setError(""); }
      }).catch((failure: Error) => {
        if (active) setError(failure.message);
      }).finally(() => {
        if (active) setLoading(false);
      });
    }, 140);
    return () => { active = false; window.clearTimeout(timeout); };
  }, [countryCode, excludeCode, open, query]);

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
        role="combobox"
        aria-label={`${label} airport`}
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls={listId}
        aria-activedescendant={open && results[activeIndex] ? `${listId}-${results[activeIndex].code}` : undefined}
        autoComplete="off"
        placeholder="City, airport or code"
        value={selected ? `${selected.city} · ${selected.code}` : query}
        onFocus={() => {
          if (selected) {
            onChange(null);
            setQuery("");
          }
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
      {selected && <small className="airport-picked">{selected.name}</small>}
      {!selected && !query && <small className="airport-hint">Search city, airport or IATA code</small>}
      {open && (results.length > 0 || loading || error) && (
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
          {loading && <div className="airport-message">Searching airports…</div>}
          {!loading && !error && results.length === 0 && <div className="airport-message">No matching airports.</div>}
          {error && <div className="airport-message airport-error" role="status">Airport search is unavailable. Check your connection.</div>}
        </div>
      )}
    </div>
  );
}
