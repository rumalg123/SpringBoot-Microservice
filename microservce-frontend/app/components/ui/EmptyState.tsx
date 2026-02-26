"use client";
import React, { ReactNode } from "react";

type Props = {
  icon?: ReactNode;
  title: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
};

function EmptyStateInner({ icon, title, description, actionLabel, onAction }: Props) {
  return (
    <div className="text-center py-[60px] px-6 text-muted">
      {icon && <div className="text-[2.5rem] mb-3">{icon}</div>}
      <h3 className="text-lg font-bold text-ink-light mb-1.5">{title}</h3>
      {description && <p className="text-sm max-w-[400px] mx-auto mb-4 leading-relaxed">{description}</p>}
      {actionLabel && onAction && (
        <button type="button" onClick={onAction} className="btn-primary text-sm py-2 px-5">
          {actionLabel}
        </button>
      )}
    </div>
  );
}

export default React.memo(EmptyStateInner);
