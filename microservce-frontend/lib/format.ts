export function money(value: number): string {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

export function calcDiscount(regular: number, selling: number): number | null {
  if (regular > selling && regular > 0) return Math.round(((regular - selling) / regular) * 100);
  return null;
}
