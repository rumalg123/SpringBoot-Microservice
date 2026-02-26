import { useEffect, useState } from "react";

export type CountdownResult = {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  expired: boolean;
  total: number;
};

export function useCountdown(endTime: string | Date | null): CountdownResult {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!endTime) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [endTime]);

  if (!endTime) return { days: 0, hours: 0, minutes: 0, seconds: 0, expired: true, total: 0 };

  const end = new Date(endTime).getTime();
  const diff = Math.max(0, end - now);

  if (diff <= 0) return { days: 0, hours: 0, minutes: 0, seconds: 0, expired: true, total: 0 };

  return {
    days: Math.floor(diff / 86400000),
    hours: Math.floor((diff % 86400000) / 3600000),
    minutes: Math.floor((diff % 3600000) / 60000),
    seconds: Math.floor((diff % 60000) / 1000),
    expired: false,
    total: diff,
  };
}
