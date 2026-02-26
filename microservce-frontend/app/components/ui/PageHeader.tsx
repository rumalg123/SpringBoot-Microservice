import type { ReactNode } from "react";

type Props = {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
};

export default function PageHeader({ title, subtitle, actions }: Props) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1
          className="m-0"
          style={{
            fontFamily: "'Syne', sans-serif",
            fontSize: "1.75rem",
            fontWeight: 800,
            color: "#fff",
          }}
        >
          {title}
        </h1>
        {subtitle && (
          <p className="m-0" style={{ marginTop: 4, fontSize: "0.8rem", color: "var(--muted)" }}>
            {subtitle}
          </p>
        )}
      </div>
      {actions && <div className="flex gap-2.5">{actions}</div>}
    </div>
  );
}
