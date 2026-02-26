export default function CardSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-4">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="rounded-[14px] overflow-hidden border border-line-bright bg-[var(--card)]">
          <div className="skeleton h-[200px] rounded-none" />
          <div className="p-[14px]">
            <div className="skeleton h-[14px] w-[70%] mb-2.5" />
            <div className="skeleton h-[12px] w-[40%] mb-2" />
            <div className="skeleton h-[16px] w-[30%]" />
          </div>
        </div>
      ))}
    </div>
  );
}
