"use client";

import { useEffect, useState } from "react";

/**
 * Countdown timer hook.
 * Returns a human-readable label like "2d 5h 30m 12s" or "Ended".
 */
export function useCountdown(endIso: string | null): string {
  const [label, setLabel] = useState("");

  useEffect(() => {
    if (!endIso) {
      setLabel("");
      return;
    }

    const tick = () => {
      const diff = new Date(endIso).getTime() - Date.now();
      if (diff <= 0) {
        setLabel("Ended");
        return;
      }
      const d = Math.floor(diff / 86_400_000);
      const h = Math.floor((diff % 86_400_000) / 3_600_000);
      const m = Math.floor((diff % 3_600_000) / 60_000);
      const s = Math.floor((diff % 60_000) / 1_000);
      if (d > 0) setLabel(`${d}d ${h}h ${m}m ${s}s`);
      else if (h > 0) setLabel(`${h}h ${m}m ${s}s`);
      else setLabel(`${m}m ${s}s`);
    };

    tick();
    const id = setInterval(tick, 1_000);
    return () => clearInterval(id);
  }, [endIso]);

  return label;
}
