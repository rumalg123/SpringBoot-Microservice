"use client";

type Option = {
  value: string;
  label: string;
  description?: string;
};

type Props = {
  title: string;
  options: Option[];
  selected: string[];
  disabled?: boolean;
  onChange: (next: string[]) => void;
};

export default function PermissionChecklist({
  title,
  options,
  selected,
  disabled = false,
  onChange,
}: Props) {
  const selectedSet = new Set(selected);

  return (
    <div className="space-y-2">
      <label className="block text-xs font-semibold uppercase tracking-[0.12em]" style={{ color: "rgba(255,255,255,0.65)" }}>
        {title}
      </label>
      <div className="grid gap-2">
        {options.map((option) => {
          const checked = selectedSet.has(option.value);
          return (
            <label
              key={option.value}
              className="flex cursor-pointer items-start gap-3 rounded-xl border px-3 py-2 transition"
              style={{
                borderColor: checked ? "rgba(0,212,255,0.28)" : "rgba(255,255,255,0.08)",
                background: checked ? "rgba(0,212,255,0.07)" : "rgba(255,255,255,0.02)",
                opacity: disabled ? 0.65 : 1,
              }}
            >
              <input
                type="checkbox"
                checked={checked}
                disabled={disabled}
                onChange={(e) => {
                  const next = new Set(selectedSet);
                  if (e.target.checked) next.add(option.value);
                  else next.delete(option.value);
                  onChange(Array.from(next));
                }}
                className="mt-1 h-4 w-4"
              />
              <span className="min-w-0">
                <span className="block text-sm font-semibold" style={{ color: "var(--ink)" }}>
                  {option.label}
                </span>
                {option.description && (
                  <span className="block text-xs" style={{ color: "rgba(255,255,255,0.6)" }}>
                    {option.description}
                  </span>
                )}
              </span>
            </label>
          );
        })}
      </div>
    </div>
  );
}
