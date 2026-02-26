export default function ProductDetailLoading() {
  return (
    <div className="min-h-screen bg-bg pt-[80px] px-4 pb-12">
      <div className="max-w-[1200px] mx-auto py-8">
        {/* Breadcrumb skeleton */}
        <div className="skeleton h-[12px] w-[200px] mb-6" />

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {/* Image gallery skeleton */}
          <div>
            <div className="skeleton aspect-square rounded-xl mb-3" />
            <div className="flex gap-2">
              {Array.from({ length: 4 }, (_, i) => (
                <div key={i} className="skeleton h-16 w-16 rounded-lg" />
              ))}
            </div>
          </div>

          {/* Product info skeleton */}
          <div>
            <div className="skeleton h-[24px] w-[80%] mb-3" />
            <div className="skeleton h-[14px] w-[40%] mb-4" />
            <div className="skeleton h-[32px] w-[120px] mb-6" />
            <div className="skeleton h-[14px] w-full mb-2" />
            <div className="skeleton h-[14px] w-[90%] mb-2" />
            <div className="skeleton h-[14px] w-[70%] mb-6" />
            <div className="skeleton h-[48px] w-[180px] rounded-lg" />
          </div>
        </div>
      </div>
    </div>
  );
}
