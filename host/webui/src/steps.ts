import type { CommandStep } from "./api";

export const STEP_TYPES: CommandStep["type"][] = ["launch_app", "type_text", "key_shortcut", "delay"];

// Mirrors host/commands/MacroExecutor.kt's stepKindLabel() — keep in sync manually.
export const STEP_LABELS: Record<CommandStep["type"], string> = {
  launch_app: "Abrir aplicativo",
  type_text: "Digitar texto",
  key_shortcut: "Atalho de teclado",
  delay: "Espera",
};

export function defaultStepFor(type: CommandStep["type"]): CommandStep {
  switch (type) {
    case "launch_app":
      return { type, executablePath: "" };
    case "type_text":
      return { type, text: "" };
    case "key_shortcut":
      return { type, keys: [] };
    case "delay":
      return { type, milliseconds: 500 };
  }
}
