"use client";

export default function PosterFormField({
  label,
  children,
  hint,
}: {
  label: string;
  children: React.ReactNode;
  hint?: string;
}) {
  return (
    <div className="form-group">
      <label className="form-label">{label}</label>
      {children}
      {hint ? <div className="text-xs text-muted">{hint}</div> : null}
    </div>
  );
}
