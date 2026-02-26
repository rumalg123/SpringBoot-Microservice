import { useEffect, useState } from "react";

export function useDebounce<T>(value: T, delay: number): T {
  const debounced = useState(value);
  const [debouncedValue, setDebouncedValue] = debounced;

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedValue(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay, setDebouncedValue]);

  return debouncedValue;
}
