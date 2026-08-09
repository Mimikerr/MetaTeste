import { useState } from "react";
import type { CommandStep, VoiceCommandMapping } from "./api";
import { STEP_LABELS, STEP_TYPES, defaultStepFor } from "./steps";

interface Props {
  initial?: VoiceCommandMapping;
  onSave: (trigger: string, steps: CommandStep[]) => void;
  onCancel: () => void;
}

export function CommandEditor({ initial, onSave, onCancel }: Props) {
  const [trigger, setTrigger] = useState(initial?.trigger ?? "");
  const [steps, setSteps] = useState<CommandStep[]>(initial?.steps ?? []);

  function updateStep(index: number, step: CommandStep) {
    setSteps((prev) => prev.map((s, i) => (i === index ? step : s)));
  }

  function removeStep(index: number) {
    setSteps((prev) => prev.filter((_, i) => i !== index));
  }

  function moveStep(index: number, delta: number) {
    setSteps((prev) => {
      const target = index + delta;
      if (target < 0 || target >= prev.length) return prev;
      const next = [...prev];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  function addStep(type: CommandStep["type"]) {
    setSteps((prev) => [...prev, defaultStepFor(type)]);
  }

  return (
    <div className="editor">
      <label className="trigger-field">
        Gatilho (o que você diz)
        <input value={trigger} onChange={(e) => setTrigger(e.target.value)} placeholder="ex: abrir calculadora" />
      </label>

      <div className="steps">
        {steps.length === 0 && <p className="empty">Nenhum passo ainda — adicione um abaixo.</p>}
        {steps.map((step, index) => (
          <div className="step" key={index}>
            <div className="step-header">
              <span className="step-index">{index + 1}.</span>
              <span className="step-kind">{STEP_LABELS[step.type]}</span>
              <div className="step-actions">
                <button type="button" onClick={() => moveStep(index, -1)} disabled={index === 0}>
                  ↑
                </button>
                <button type="button" onClick={() => moveStep(index, 1)} disabled={index === steps.length - 1}>
                  ↓
                </button>
                <button type="button" onClick={() => removeStep(index)}>
                  Remover
                </button>
              </div>
            </div>
            <StepFields step={step} onChange={(s) => updateStep(index, s)} />
          </div>
        ))}
      </div>

      <div className="add-step">
        {STEP_TYPES.map((type) => (
          <button type="button" key={type} onClick={() => addStep(type)}>
            + {STEP_LABELS[type]}
          </button>
        ))}
      </div>

      <div className="editor-actions">
        <button
          type="button"
          className="primary"
          onClick={() => onSave(trigger, steps)}
          disabled={!trigger.trim() || steps.length === 0}
        >
          Salvar
        </button>
        <button type="button" onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </div>
  );
}

function StepFields({ step, onChange }: { step: CommandStep; onChange: (step: CommandStep) => void }) {
  switch (step.type) {
    case "launch_app":
      return (
        <input
          value={step.executablePath}
          onChange={(e) => onChange({ ...step, executablePath: e.target.value })}
          placeholder="ex: chrome ou C:\Apps\app.exe"
        />
      );
    case "type_text":
      return (
        <input
          value={step.text}
          onChange={(e) => onChange({ ...step, text: e.target.value })}
          placeholder="texto a digitar"
        />
      );
    case "key_shortcut":
      return (
        <input
          value={step.keys.join("+")}
          onChange={(e) =>
            onChange({
              ...step,
              keys: e.target.value
                .split("+")
                .map((k) => k.trim())
                .filter(Boolean),
            })
          }
          placeholder="ex: ALT+F4 ou CONTROL+C"
        />
      );
    case "delay":
      return (
        <input
          type="number"
          min={0}
          max={10000}
          value={step.milliseconds}
          onChange={(e) => onChange({ ...step, milliseconds: Number(e.target.value) })}
        />
      );
  }
}
