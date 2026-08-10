// Types mirror the Kotlin sealed interface in host/commands/CommandStep.kt.
// The "type" string literals must match the @SerialName annotations there exactly.
export type CommandStep =
  | { type: "launch_app"; executablePath: string }
  | { type: "type_text"; text: string }
  | { type: "key_shortcut"; keys: string[] }
  | { type: "delay"; milliseconds: number };

export interface VoiceCommandMapping {
  id: string;
  trigger: string;
  steps: CommandStep[];
}

export interface UpsertCommandRequest {
  trigger: string;
  steps: CommandStep[];
}

export interface ApiError {
  error: string;
}

async function parseJsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => null) as ApiError | null;
    throw new Error(body?.error ?? `HTTP ${response.status}`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function listCommands(): Promise<VoiceCommandMapping[]> {
  return fetch("/api/commands").then((r) => parseJsonOrThrow(r));
}

export function createCommand(req: UpsertCommandRequest): Promise<VoiceCommandMapping> {
  return fetch("/api/commands", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  }).then((r) => parseJsonOrThrow(r));
}

export function updateCommand(id: string, req: UpsertCommandRequest): Promise<VoiceCommandMapping> {
  return fetch(`/api/commands/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  }).then((r) => parseJsonOrThrow(r));
}

export function deleteCommand(id: string): Promise<void> {
  return fetch(`/api/commands/${id}`, { method: "DELETE" }).then((r) => parseJsonOrThrow(r));
}

export type ServiceState = "STOPPED" | "STARTING" | "RUNNING" | "STOPPING" | "FAILED";

export interface ServiceStatus {
  id: string;
  name: string;
  state: ServiceState;
  pid: number | null;
  lastError: string | null;
  recentLog: string[];
}

export function listServices(): Promise<ServiceStatus[]> {
  return fetch("/api/services").then((r) => parseJsonOrThrow(r));
}

export function startService(id: string): Promise<ServiceStatus> {
  return fetch(`/api/services/${id}/start`, { method: "POST" }).then((r) => parseJsonOrThrow(r));
}

export function stopService(id: string): Promise<ServiceStatus> {
  return fetch(`/api/services/${id}/stop`, { method: "POST" }).then((r) => parseJsonOrThrow(r));
}

// Mirrors the Kotlin sealed interface in host/chat/ChatEvent.kt — "type" literals must match
// the @SerialName annotations there exactly.
export type ChatEvent =
  | { type: "user_message"; timestamp: number; text: string }
  | { type: "tool_call"; timestamp: number; tool: string; input: Record<string, string> }
  | { type: "tool_result"; timestamp: number; tool: string; output: string }
  | { type: "assistant_message"; timestamp: number; text: string; awaitingConfirmation: boolean }
  | { type: "command_result"; timestamp: number; success: boolean; detail: string | null }
  | { type: "system_note"; timestamp: number; text: string };
