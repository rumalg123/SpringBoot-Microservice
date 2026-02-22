"use client";

import type { VendorUser } from "./types";

type VendorUsersListProps = {
  containerId?: string;
  vendorUsers: VendorUser[];
  selectedVendorId: string;
  loadingUsers: boolean;
  onRefresh: () => void;
  onRemoveUser: (user: VendorUser) => void;
  removingMembershipId?: string | null;
};

export default function VendorUsersList({
  containerId,
  vendorUsers,
  selectedVendorId,
  loadingUsers,
  onRefresh,
  onRemoveUser,
  removingMembershipId = null,
}: VendorUsersListProps) {
  return (
    <div id={containerId} className="mt-4 rounded-lg border border-[var(--line)] p-3">
      <div className="mb-2 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-[var(--ink)]">Vendor Users</h3>
        <button
          type="button"
          onClick={onRefresh}
          disabled={!selectedVendorId || loadingUsers}
          className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
          style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
        >
          {loadingUsers ? "Loading..." : "Refresh"}
        </button>
      </div>

      {vendorUsers.length === 0 ? (
        <p className="text-xs text-[var(--muted)]">
          {selectedVendorId ? "No vendor users yet." : "Select a vendor to view users."}
        </p>
      ) : (
        <div className="space-y-2">
          {vendorUsers.map((user) => (
            <div key={user.id} className="rounded-md border border-[var(--line)] p-2 text-xs">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-semibold text-[var(--ink)]">{user.displayName || user.email}</p>
                <div className="flex items-center gap-2">
                  <span
                    className="rounded px-2 py-0.5 text-[10px]"
                    style={{
                      background: "rgba(255,255,255,0.04)",
                      color: "var(--ink-light)",
                      border: "1px solid rgba(255,255,255,0.08)",
                    }}
                  >
                    {user.role}
                  </span>
                  <button
                    type="button"
                    onClick={() => onRemoveUser(user)}
                    disabled={removingMembershipId === user.id}
                    className="rounded-md border border-red-500/40 px-2 py-1 text-[10px] disabled:cursor-not-allowed disabled:opacity-60"
                    style={{ background: "rgba(239,68,68,0.08)", color: "#fca5a5" }}
                  >
                    {removingMembershipId === user.id ? "Removing..." : "Remove"}
                  </button>
                </div>
              </div>
              <p className="mt-1 text-[var(--muted)]">{user.email}</p>
              <p className="mt-1 font-mono text-[10px] text-[var(--muted)]">{user.keycloakUserId}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
