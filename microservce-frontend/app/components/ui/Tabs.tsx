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

export default function Tabs({
  tabs,
  activeKey,
  onChange,
  variant = "pill",
  className,
  style,
}: Props) {
  const isPill = variant === "pill";

  return (
    <div
      className={`flex ${isPill ? "gap-1.5 flex-wrap" : "gap-0 border-b border-line"} ${className || ""}`}
      style={style}
      role="tablist"
    >
      {tabs.map((tab) => {
        const isActive = tab.key === activeKey;

        const pillClasses = isActive
          ? "bg-[rgba(0,212,255,0.12)] text-brand border-[rgba(0,212,255,0.25)]"
          : "text-muted border-transparent bg-transparent";

        const underlineClasses = isActive
          ? "text-brand border-b-brand"
          : "text-muted border-b-transparent";

        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange(tab.key)}
            className={
              isPill
                ? `py-2 px-[18px] rounded-md text-sm font-semibold cursor-pointer border transition-all duration-200 ${pillClasses}`
                : `py-2.5 px-5 bg-transparent border-none border-b-2 cursor-pointer text-sm font-semibold transition-colors duration-150 ${underlineClasses}`
            }
          >
            {tab.label}
            {tab.badge != null && (
              <span className="ml-1.5 opacity-70">({tab.badge})</span>
            )}
          </button>
        );
      })}
    </div>
  );
}
