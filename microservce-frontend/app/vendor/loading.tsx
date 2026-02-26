import TableSkeleton from "../components/ui/TableSkeleton";

export default function VendorLoading() {
  return (
    <main className="min-h-screen bg-bg text-ink pt-[100px] px-6 pb-12">
      <div className="max-w-[1280px] mx-auto">
        <div className="skeleton h-[14px] w-[100px] mb-3" />
        <div className="skeleton h-[32px] w-[200px] mb-6" />
        <TableSkeleton rows={6} cols={4} />
      </div>
    </main>
  );
}
