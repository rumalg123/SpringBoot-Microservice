import PageSkeleton from "./components/ui/PageSkeleton";

export default function RootLoading() {
  return (
    <div className="min-h-screen bg-bg pt-[80px]">
      <PageSkeleton />
    </div>
  );
}
