"use client";

type Props = { count?: number };

export default function ProductCardSkeleton({ count = 4 }: Props) {
  return (
    <>
      {Array.from({ length: count }, (_, i) => (
        <div
          key={i}
          className="rounded-lg overflow-hidden bg-surface border border-line"
        >
          <div className="skeleton h-[220px] w-full rounded-none" />
          <div className="px-4 py-3.5 flex flex-col gap-2">
            <div className="skeleton h-[13px] w-[80%]" />
            <div className="skeleton h-[13px] w-[60%]" />
            <div className="skeleton h-[18px] w-[45%]" />
          </div>
        </div>
      ))}
    </>
  );
}
