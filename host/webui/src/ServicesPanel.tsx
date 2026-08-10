import { useEffect, useState } from "react";
import type { ServiceStatus } from "./api";
import { listServices, startService, stopService } from "./api";

const STATE_LABELS: Record<ServiceStatus["state"], string> = {
  STOPPED: "Parado",
  STARTING: "Iniciando…",
  RUNNING: "Rodando",
  STOPPING: "Parando…",
  FAILED: "Falhou",
};

export function ServicesPanel() {
  const [services, setServices] = useState<ServiceStatus[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function refresh() {
    listServices()
      .then(setServices)
      .catch((e: unknown) => setError(String(e)));
  }

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 2000);
    return () => clearInterval(interval);
  }, []);

  async function handleToggle(service: ServiceStatus) {
    setBusyId(service.id);
    setError(null);
    try {
      if (service.state === "RUNNING" || service.state === "STARTING") {
        await stopService(service.id);
      } else {
        await startService(service.id);
      }
      refresh();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className="services">
      <h2>Serviços locais</h2>
      {error && <p className="error">{error}</p>}
      {services.map((service) => (
        <div key={service.id} className={`service-row service-${service.state.toLowerCase()}`}>
          <div className="service-info">
            <span className="service-name">{service.name}</span>
            <span className="service-state">{STATE_LABELS[service.state]}</span>
            {service.lastError && <span className="service-error">{service.lastError}</span>}
          </div>
          <button
            type="button"
            disabled={busyId === service.id || service.state === "STARTING" || service.state === "STOPPING"}
            onClick={() => handleToggle(service)}
          >
            {service.state === "RUNNING" ? "Parar" : "Iniciar"}
          </button>
        </div>
      ))}
    </section>
  );
}
