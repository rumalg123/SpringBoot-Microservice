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
    <div style={{ marginBottom: 14, ...style }}>
      <label
        htmlFor={htmlFor}
        style={{ display: "block", marginBottom: 5, fontSize: "0.78rem", fontWeight: 600, color: "var(--ink-light)" }}
      >
        {label}
        {required && <span style={{ color: "var(--danger)", marginLeft: 3 }}>*</span>}
      </label>
      {children}
      {error && <p style={{ marginTop: 4, fontSize: "0.72rem", color: "var(--danger)" }}>{error}</p>}
      {helpText && !error && <p style={{ marginTop: 4, fontSize: "0.72rem", color: "var(--muted)" }}>{helpText}</p>}
    </div>
  );
}
