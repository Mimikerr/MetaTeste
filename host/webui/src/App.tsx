import { useEffect, useState } from "react";
import type { CommandStep, VoiceCommandMapping } from "./api";
import { createCommand, deleteCommand, listCommands, updateCommand } from "./api";
import { CommandEditor } from "./CommandEditor";
import { CommandList } from "./CommandList";

type EditorState = { mode: "closed" } | { mode: "creating" } | { mode: "editing"; command: VoiceCommandMapping };

export default function App() {
  const [commands, setCommands] = useState<VoiceCommandMapping[]>([]);
  const [editor, setEditor] = useState<EditorState>({ mode: "closed" });
  const [error, setError] = useState<string | null>(null);

  function refresh() {
    listCommands()
      .then(setCommands)
      .catch((e: unknown) => setError(String(e)));
  }

  useEffect(refresh, []);

  async function handleSave(trigger: string, steps: CommandStep[]) {
    try {
      if (editor.mode === "editing") {
        await updateCommand(editor.command.id, { trigger, steps });
      } else {
        await createCommand({ trigger, steps });
      }
      setError(null);
      setEditor({ mode: "closed" });
      refresh();
    } catch (e) {
      setError(String(e));
    }
  }

  async function handleDelete(id: string) {
    await deleteCommand(id);
    refresh();
  }

  return (
    <main>
      <h1>Nexus Command — Comandos de voz</h1>
      <p className="hint">
        Quando a frase reconhecida contiver o gatilho (sem diferenciar maiúsculas/minúsculas), os passos configurados
        rodam em vez de digitar o texto no terminal focado.
      </p>

      {error && <p className="error">{error}</p>}

      {editor.mode === "closed" ? (
        <>
          <CommandList
            commands={commands}
            onEdit={(command) => setEditor({ mode: "editing", command })}
            onDelete={handleDelete}
          />
          <button type="button" className="primary" onClick={() => setEditor({ mode: "creating" })}>
            + Novo comando
          </button>
        </>
      ) : (
        <CommandEditor
          initial={editor.mode === "editing" ? editor.command : undefined}
          onSave={handleSave}
          onCancel={() => setEditor({ mode: "closed" })}
        />
      )}
    </main>
  );
}
