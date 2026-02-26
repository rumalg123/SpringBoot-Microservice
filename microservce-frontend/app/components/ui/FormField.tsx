"use client";
import { ReactNode } from "react";

type Props = {
  label: string;
  htmlFor?: string;
  required?: boolean;
  error?: string;
  helpText?: string;
  children: ReactNode;
  style?: React.CSSProperties;
};

export default function FormField({ label, htmlFor, required, error, helpText, children, style }: Props) {
  return (
    <div className="mb-3.5" style={style}>
      <label
        htmlFor={htmlFor}
        className="block mb-1 text-sm font-semibold text-ink-light"
      >
        {label}
        {required && <span className="text-danger ml-0.5">*</span>}
      </label>
      {children}
      {error && <p className="mt-1 text-xs text-danger">{error}</p>}
      {helpText && !error && <p className="mt-1 text-xs text-muted">{helpText}</p>}
    </div>
  );
}
