import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import type { ChatEvent } from "./api";

const TOOL_LABELS: Record<string, string> = {
  read_file: "lendo arquivo",
  run_command: "preparando comando",
  run_macro: "rodando macro",
};

function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

function formatInput(input: Record<string, string>): string {
  return Object.entries(input)
    .map(([key, value]) => `${key}=${value}`)
    .join(", ");
}

export function ChatPanel() {
  const [events, setEvents] = useState<ChatEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const protocol = location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${protocol}://${location.host}/api/chat`);
    socket.onopen = () => setConnected(true);
    socket.onclose = () => setConnected(false);
    socket.onmessage = (msg) => {
      const event = JSON.parse(msg.data as string) as ChatEvent;
      setEvents((prev) => [...prev, event]);
    };
    return () => socket.close();
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [events]);

  return (
    <section className="chat">
      <h2>
        Chat com o Jarvis{" "}
        {connected ? <span className="chat-live">● ao vivo</span> : <span className="chat-offline">○ desconectado</span>}
      </h2>
      <div className="chat-log">
        {events.length === 0 && <p className="empty">Nenhuma conversa ainda — fale com o Jarvis pelo Quest.</p>}
        {events.map((event, index) => (
          <ChatLine key={index} event={event} />
        ))}
        <div ref={bottomRef} />
      </div>
    </section>
  );
}

function ChatLine({ event }: { event: ChatEvent }) {
  const time = formatTime(event.timestamp);

  switch (event.type) {
    case "user_message":
      return (
        <Line time={time} className="chat-user">
          🎙️ Você: {event.text}
        </Line>
      );
    case "tool_call":
      return (
        <Line time={time} className="chat-thinking">
          🧠 {TOOL_LABELS[event.tool] ?? event.tool}
          {Object.keys(event.input).length > 0 && ` (${formatInput(event.input)})`}
        </Line>
      );
    case "tool_result":
      return (
        <Line time={time} className="chat-thinking">
          ↳ {event.output}
        </Line>
      );
    case "assistant_message":
      return (
        <Line time={time} className={event.awaitingConfirmation ? "chat-assistant chat-confirming" : "chat-assistant"}>
          🤖 Jarvis: {event.text}
        </Line>
      );
    case "command_result":
      return (
        <Line time={time} className={event.success ? "chat-command chat-success" : "chat-command chat-failure"}>
          {event.success ? "✔" : "✘"} {event.detail ?? (event.success ? "comando executado" : "comando falhou")}
        </Line>
      );
    case "system_note":
      return (
        <Line time={time} className="chat-system">
          ⚠ {event.text}
        </Line>
      );
  }
}

function Line({ time, className, children }: { time: string; className: string; children: ReactNode }) {
  return (
    <div className={`chat-line ${className}`}>
      <span className="chat-time">{time}</span>
      <span className="chat-bubble">{children}</span>
    </div>
  );
}
