"use client";

import StatusBadge, { ORDER_STATUS_COLORS } from "../../../components/ui/StatusBadge";
import VendorOrderDetailPanel from "./VendorOrderDetailPanel";
import type { VendorOrder, VendorOrderDetail } from "./types";
import { truncateId, formatCurrency } from "./types";

type Props = {
  order: VendorOrder;
  isExpanded: boolean;
  onToggle: () => void;
  orderDetail: VendorOrderDetail | null;
  detailLoading: boolean;
};

export default function VendorOrderRow({ order, isExpanded, onToggle, orderDetail, detailLoading }: Props) {
  return (
    <tr className="cursor-default">
      <td className="px-3 py-2.5 text-sm text-ink border-b border-line whitespace-nowrap" colSpan={8}>
        {/* Row content + optional detail panel */}
        <div>
          {/* Order row */}
          <div className="grid grid-cols-[1.4fr_0.6fr_0.5fr_1fr_1fr_1.1fr_1fr_0.8fr] items-center -mx-3 -my-2.5 px-3 py-2.5">
            <span
              className="font-mono text-[0.78rem] text-brand"
              title={order.orderId}
            >
              {truncateId(order.orderId)}
            </span>
            <span className="text-sm">{order.itemCount}</span>
            <span className="text-sm">{order.quantity}</span>
            <span className="text-sm font-semibold">
              {formatCurrency(order.orderTotal)}
            </span>
            <span className="text-sm font-semibold text-success">
              {formatCurrency(order.payoutAmount)}
            </span>
            <span>
              <StatusBadge value={order.status} colorMap={ORDER_STATUS_COLORS} />
            </span>
            <span className="text-[0.75rem] text-muted">
              {new Date(order.createdAt).toLocaleDateString()}
            </span>
            <span className="text-center">
              <button
                onClick={onToggle}
                className={`px-3.5 py-[5px] rounded-[8px] text-[0.75rem] font-bold cursor-pointer border border-line-bright transition-opacity duration-150 ${
                  isExpanded
                    ? "bg-brand text-white"
                    : "bg-brand-soft text-brand"
                }`}
              >
                {isExpanded ? "Close" : "View"}
              </button>
            </span>
          </div>

          {/* ── Detail panel ── */}
          {isExpanded && (
            <VendorOrderDetailPanel
              orderDetail={orderDetail}
              detailLoading={detailLoading}
            />
          )}
        </div>
      </td>
    </tr>
  );
}
