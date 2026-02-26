export default function TableSkeleton({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="rounded-[14px] overflow-hidden border border-line-bright">
      <table className="w-full border-collapse">
        <thead>
          <tr className="bg-[var(--card)]">
            {Array.from({ length: cols }, (_, i) => (
              <th key={i} className="py-3 px-3.5">
                <div className="skeleton h-2.5 w-[60%]" />
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rows }, (_, r) => (
            <tr key={r} className="border-t border-line-bright">
              {Array.from({ length: cols }, (_, c) => (
                <td key={c} className="p-3.5">
                  <div className={`skeleton h-3 ${c === 0 ? "w-[80%]" : "w-[50%]"}`} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
