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
          className="m-0 text-[1.75rem] font-extrabold text-white"
          style={{ fontFamily: "'Syne', sans-serif" }}
        >
          {title}
        </h1>
        {subtitle && (
          <p className="m-0 mt-1 text-sm text-muted">
            {subtitle}
          </p>
        )}
      </div>
      {actions && <div className="flex gap-2.5">{actions}</div>}
    </div>
  );
}
