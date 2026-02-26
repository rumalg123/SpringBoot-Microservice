"use client";

import type { CSSProperties } from "react";

export type TabItem = {
  key: string;
  label: string;
  /** Optional badge/count displayed after the label. */
  badge?: string | number;
};

type Props = {
  tabs: TabItem[];
  activeKey: string;
  onChange: (key: string) => void;
  /** "pill" = rounded buttons (default), "underline" = bottom-border tabs */
  variant?: "pill" | "underline";
  className?: string;
  style?: CSSProperties;
};

const pillBase: CSSProperties = {
  padding: "8px 18px",
  borderRadius: 10,
  fontSize: "0.82rem",
  fontWeight: 600,
  cursor: "pointer",
  border: "1px solid transparent",
  background: "transparent",
  transition: "all 0.2s",
};

const pillActive: CSSProperties = {
  background: "rgba(0,212,255,0.12)",
  color: "var(--brand)",
  borderColor: "rgba(0,212,255,0.25)",
};

const pillInactive: CSSProperties = {
  color: "var(--muted)",
};

const underlineBase: CSSProperties = {
  padding: "10px 20px",
  background: "transparent",
  border: "none",
  borderBottom: "2px solid transparent",
  cursor: "pointer",
  fontSize: "0.85rem",
  fontWeight: 600,
  transition: "color 0.15s, border-color 0.15s",
};

const underlineActive: CSSProperties = {
  color: "var(--brand)",
  borderBottomColor: "var(--brand)",
};

const underlineInactive: CSSProperties = {
  color: "var(--muted)",
};

export default function Tabs({
  tabs,
  activeKey,
  onChange,
  variant = "pill",
  className,
  style,
}: Props) {
  const isPill = variant === "pill";

  const wrapperStyle: CSSProperties = isPill
    ? { display: "flex", gap: 6, flexWrap: "wrap", ...style }
    : { display: "flex", gap: 0, borderBottom: "1px solid var(--line)", ...style };

  return (
    <div className={className} style={wrapperStyle} role="tablist">
      {tabs.map((tab) => {
        const isActive = tab.key === activeKey;
        const base = isPill ? pillBase : underlineBase;
        const state = isActive
          ? (isPill ? pillActive : underlineActive)
          : (isPill ? pillInactive : underlineInactive);

        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange(tab.key)}
            style={{ ...base, ...state }}
          >
            {tab.label}
            {tab.badge != null && (
              <span style={{ marginLeft: 6, opacity: 0.7 }}>({tab.badge})</span>
            )}
          </button>
        );
      })}
    </div>
  );
}
