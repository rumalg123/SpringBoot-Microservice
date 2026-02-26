export default function PageSkeleton() {
  return (
    <div className="max-w-[1200px] mx-auto py-10 px-4">
      <div className="skeleton h-[28px] w-[200px] mb-2" />
      <div className="skeleton h-[14px] w-[300px] mb-8" />
      <div className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-4">
        {Array.from({ length: 8 }, (_, i) => (
          <div key={i} className="rounded-[14px] overflow-hidden border border-line-bright bg-[var(--card)]">
            <div className="skeleton h-[180px] rounded-none" />
            <div className="p-3">
              <div className="skeleton h-[14px] w-[70%] mb-2" />
              <div className="skeleton h-[12px] w-[40%]" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
