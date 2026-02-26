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
      <label className="block text-xs font-semibold uppercase tracking-[0.12em] text-white/65">
        {title}
      </label>
      <div className="grid gap-2">
        {options.map((option) => {
          const checked = selectedSet.has(option.value);
          return (
            <label
              key={option.value}
              className={`flex cursor-pointer items-start gap-3 rounded-xl border px-3 py-2 transition ${
                checked
                  ? "border-[rgba(0,212,255,0.28)] bg-[rgba(0,212,255,0.07)]"
                  : "border-white/[0.08] bg-white/[0.02]"
              } ${disabled ? "opacity-65" : "opacity-100"}`}
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
                <span className="block text-sm font-semibold text-ink">
                  {option.label}
                </span>
                {option.description && (
                  <span className="block text-xs text-white/60">
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
