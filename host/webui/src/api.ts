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
