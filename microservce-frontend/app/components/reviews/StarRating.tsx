"use client";

import React from "react";

type Props = {
  value: number;
  onChange?: (value: number) => void;
  size?: number;
  interactive?: boolean;
};

function StarRatingInner({ value, onChange, size = 16, interactive = false }: Props) {
  return (
    <div className={`inline-flex gap-[2px] ${interactive ? "cursor-pointer" : "cursor-default"}`} role="group" aria-label="Star rating">
      {[1, 2, 3, 4, 5].map((star) => (
        <svg
          key={star}
          width={size}
          height={size}
          viewBox="0 0 24 24"
          fill={star <= value ? "#facc15" : "none"}
          stroke="#facc15"
          strokeWidth="2"
          role={interactive ? "button" : "img"}
          aria-label={`Rate ${star} star${star > 1 ? "s" : ""}`}
          tabIndex={interactive ? 0 : undefined}
          onClick={() => interactive && onChange?.(star)}
          onKeyDown={(e) => {
            if (interactive && (e.key === "Enter" || e.key === " ")) {
              e.preventDefault();
              onChange?.(star);
            }
          }}
          className={`transition-transform duration-100 ${interactive ? "cursor-pointer hover:scale-[1.2]" : ""}`}
        >
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
        </svg>
      ))}
    </div>
  );
}

export default React.memo(StarRatingInner);
