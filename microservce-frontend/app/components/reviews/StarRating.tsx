"use client";

type Props = {
  value: number;
  onChange?: (value: number) => void;
  size?: number;
  interactive?: boolean;
};

export default function StarRating({ value, onChange, size = 16, interactive = false }: Props) {
  return (
    <div style={{ display: "inline-flex", gap: "2px", cursor: interactive ? "pointer" : "default" }}>
      {[1, 2, 3, 4, 5].map((star) => (
        <svg
          key={star}
          width={size}
          height={size}
          viewBox="0 0 24 24"
          fill={star <= value ? "#facc15" : "none"}
          stroke="#facc15"
          strokeWidth="2"
          onClick={() => interactive && onChange?.(star)}
          style={{ transition: "transform 0.1s", ...(interactive ? { cursor: "pointer" } : {}) }}
          onMouseEnter={(e) => interactive && (e.currentTarget.style.transform = "scale(1.2)")}
          onMouseLeave={(e) => interactive && (e.currentTarget.style.transform = "scale(1)")}
        >
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
        </svg>
      ))}
    </div>
  );
}
