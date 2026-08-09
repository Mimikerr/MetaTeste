import type { VoiceCommandMapping } from "./api";
import { STEP_LABELS } from "./steps";

interface Props {
  commands: VoiceCommandMapping[];
  onEdit: (command: VoiceCommandMapping) => void;
  onDelete: (id: string) => void;
}

export function CommandList({ commands, onEdit, onDelete }: Props) {
  if (commands.length === 0) {
    return <p className="empty">Nenhum comando configurado ainda.</p>;
  }

  return (
    <table>
      <thead>
        <tr>
          <th>Gatilho</th>
          <th>Passos</th>
          <th />
        </tr>
      </thead>
      <tbody>
        {commands.map((command) => (
          <tr key={command.id}>
            <td>{command.trigger}</td>
            <td>{command.steps.map((step) => STEP_LABELS[step.type]).join(" → ")}</td>
            <td className="row-actions">
              <button type="button" onClick={() => onEdit(command)}>
                Editar
              </button>
              <button type="button" onClick={() => onDelete(command.id)}>
                Remover
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
