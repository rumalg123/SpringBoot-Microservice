import TableSkeleton from "../components/ui/TableSkeleton";

export default function AdminLoading() {
  return (
    <main className="min-h-screen bg-bg text-ink pt-[100px] px-6 pb-12">
      <div className="max-w-[1280px] mx-auto">
        <div className="skeleton h-[14px] w-[120px] mb-3" />
        <div className="skeleton h-[32px] w-[220px] mb-6" />
        <TableSkeleton rows={8} cols={5} />
      </div>
    </main>
  );
}
