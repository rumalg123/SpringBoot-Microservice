"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import VendorPageShell from "../../components/ui/VendorPageShell";

type BankAccount = {
  id: string;
  vendorId: string;
  bankName: string;
  branchName: string | null;
  branchCode: string | null;
  accountNumber: string;
  accountHolderName: string;
  swiftCode: string | null;
  primary: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

type FormData = {
  bankName: string;
  branchName: string;
  branchCode: string;
  accountNumber: string;
  accountHolderName: string;
  swiftCode: string;
};

const emptyForm: FormData = { bankName: "", branchName: "", branchCode: "", accountNumber: "", accountHolderName: "", swiftCode: "" };

export default function VendorBankAccountsPage() {
  const session = useAuthSession();
  const { status: sessionStatus, isAuthenticated, isVendorAdmin, apiClient } = session;
  const queryClient = useQueryClient();

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<FormData>(emptyForm);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [settingPrimaryId, setSettingPrimaryId] = useState<string | null>(null);

  const ready = sessionStatus === "ready" && isAuthenticated && isVendorAdmin && !!apiClient;

  const { data: accounts = [], isLoading: loading } = useQuery({
    queryKey: ["vendor-bank-accounts"],
    queryFn: async () => {
      const res = await apiClient!.get("/admin/payments/bank-accounts?size=100");
      const data = res.data as { content: BankAccount[] };
      return data.content || [];
    },
    enabled: ready,
  });

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (editingId) {
        const body: Record<string, string | null> = {};
        if (form.bankName.trim()) body.bankName = form.bankName.trim();
        if (form.branchName.trim()) body.branchName = form.branchName.trim();
        if (form.branchCode.trim()) body.branchCode = form.branchCode.trim();
        if (form.accountNumber.trim()) body.accountNumber = form.accountNumber.trim();
        if (form.accountHolderName.trim()) body.accountHolderName = form.accountHolderName.trim();
        if (form.swiftCode.trim()) body.swiftCode = form.swiftCode.trim();
        await apiClient!.put(`/admin/payments/bank-accounts/${editingId}`, body);
      } else {
        const body = {
          bankName: form.bankName.trim(),
          branchName: form.branchName.trim() || null,
          branchCode: form.branchCode.trim() || null,
          accountNumber: form.accountNumber.trim(),
          accountHolderName: form.accountHolderName.trim(),
          swiftCode: form.swiftCode.trim() || null,
        };
        await apiClient!.post("/admin/payments/bank-accounts", body);
      }
    },
    onSuccess: () => {
      toast.success(editingId ? "Bank account updated" : "Bank account added");
      setShowForm(false);
      setEditingId(null);
      void queryClient.invalidateQueries({ queryKey: ["vendor-bank-accounts"] });
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to save bank account");
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient!.delete(`/admin/payments/bank-accounts/${id}`);
      return id;
    },
    onMutate: (id) => { setDeletingId(id); },
    onSuccess: () => {
      toast.success("Bank account deactivated");
      void queryClient.invalidateQueries({ queryKey: ["vendor-bank-accounts"] });
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to deactivate");
    },
    onSettled: () => { setDeletingId(null); },
  });

  const setPrimaryMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient!.post(`/admin/payments/bank-accounts/${id}/set-primary`);
      return id;
    },
    onMutate: (id) => { setSettingPrimaryId(id); },
    onSuccess: () => {
      toast.success("Set as primary account");
      void queryClient.invalidateQueries({ queryKey: ["vendor-bank-accounts"] });
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to set primary");
    },
    onSettled: () => { setSettingPrimaryId(null); },
  });

  const saving = saveMutation.isPending;

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm);
    setShowForm(true);
  };

  const openEdit = (a: BankAccount) => {
    setEditingId(a.id);
    setForm({
      bankName: a.bankName,
      branchName: a.branchName || "",
      branchCode: a.branchCode || "",
      accountNumber: a.accountNumber,
      accountHolderName: a.accountHolderName,
      swiftCode: a.swiftCode || "",
    });
    setShowForm(true);
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <VendorPageShell title="Bank Accounts" breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Bank Accounts" }]}>
        <p className="text-muted text-center py-10">Loading...</p>
      </VendorPageShell>
    );
  }

  return (
      <VendorPageShell
        title="Bank Accounts"
        breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Bank Accounts" }]}
        actions={
          <button
            type="button"
            onClick={showForm ? () => { setShowForm(false); setEditingId(null); } : openCreate}
            className="px-4.5 py-2 rounded-md text-[0.82rem] font-bold bg-[var(--gradient-brand)] text-white border-none cursor-pointer"
          >
            {showForm ? "Cancel" : "+ Add Account"}
          </button>
        }
      >

        {/* Form */}
        {showForm && (
          <div className="mb-6 p-5 rounded-[14px] bg-[var(--card)] border border-line-bright">
            <h3 className="text-white text-[0.95rem] font-bold mb-4">
              {editingId ? "Edit Bank Account" : "Add Bank Account"}
            </h3>
            <div className="grid grid-cols-2 gap-3 mb-3">
              <div>
                <label className="block text-xs font-bold text-muted mb-1">Bank Name *</label>
                <input value={form.bankName} onChange={(e) => setForm({ ...form, bankName: e.target.value })} placeholder="Bank of Ceylon" maxLength={200} className="form-input w-full" />
              </div>
              <div>
                <label className="block text-xs font-bold text-muted mb-1">Account Holder Name *</label>
                <input value={form.accountHolderName} onChange={(e) => setForm({ ...form, accountHolderName: e.target.value })} placeholder="John Doe" maxLength={200} className="form-input w-full" />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3 mb-3">
              <div>
                <label className="block text-xs font-bold text-muted mb-1">Account Number *</label>
                <input value={form.accountNumber} onChange={(e) => setForm({ ...form, accountNumber: e.target.value })} placeholder="123456789" maxLength={100} className="form-input w-full" />
              </div>
              <div>
                <label className="block text-xs font-bold text-muted mb-1">Branch Name</label>
                <input value={form.branchName} onChange={(e) => setForm({ ...form, branchName: e.target.value })} placeholder="Main Branch" maxLength={200} className="form-input w-full" />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3 mb-4">
              <div>
                <label className="block text-xs font-bold text-muted mb-1">Branch Code</label>
                <input value={form.branchCode} onChange={(e) => setForm({ ...form, branchCode: e.target.value })} placeholder="001" maxLength={50} className="form-input w-full" />
              </div>
              <div>
                <label className="block text-xs font-bold text-muted mb-1">SWIFT Code</label>
                <input value={form.swiftCode} onChange={(e) => setForm({ ...form, swiftCode: e.target.value })} placeholder="BABORKLK" maxLength={20} className="form-input w-full" />
              </div>
            </div>
            <button
              type="button"
              disabled={saving || !form.bankName.trim() || !form.accountNumber.trim() || !form.accountHolderName.trim()}
              onClick={() => saveMutation.mutate()}
              className="px-6 py-2 rounded-md text-[0.82rem] font-bold bg-[var(--gradient-brand)] text-white border-none"
              style={{
                cursor: saving ? "not-allowed" : "pointer",
                opacity: saving ? 0.6 : 1,
              }}
            >
              {saving ? "Saving..." : editingId ? "Update" : "Add Account"}
            </button>
          </div>
        )}

        {/* Accounts List */}
        {loading ? (
          <p className="text-muted text-center py-10">Loading bank accounts...</p>
        ) : accounts.length === 0 ? (
          <div className="text-center px-5 py-[60px] rounded-[14px] bg-[var(--card)] border border-line-bright">
            <p className="text-muted text-[0.9rem]">No bank accounts found. Add one to receive payouts.</p>
          </div>
        ) : (
          <div className="grid gap-3">
            {accounts.map((a) => (
              <div
                key={a.id}
                className="p-4.5 rounded-[14px] bg-[var(--card)]"
                style={{
                  border: a.primary ? "1px solid var(--success-glow)" : "1px solid var(--line-bright)",
                  opacity: a.active ? 1 : 0.5,
                }}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="text-[0.95rem] font-bold text-white">{a.bankName}</span>
                      {a.primary && (
                        <span className="text-[0.62rem] font-bold px-1.5 py-px rounded bg-success-soft text-success">
                          PRIMARY
                        </span>
                      )}
                      {!a.active && (
                        <span className="text-[0.62rem] font-bold px-1.5 py-px rounded bg-danger-soft text-danger">
                          INACTIVE
                        </span>
                      )}
                    </div>
                    <div className="grid grid-cols-2 gap-y-1.5 gap-x-5">
                      <p className="text-[0.78rem] text-muted m-0">
                        <strong className="text-[rgba(255,255,255,0.6)]">Account:</strong> {a.accountNumber}
                      </p>
                      <p className="text-[0.78rem] text-muted m-0">
                        <strong className="text-[rgba(255,255,255,0.6)]">Holder:</strong> {a.accountHolderName}
                      </p>
                      {a.branchName && (
                        <p className="text-[0.78rem] text-muted m-0">
                          <strong className="text-[rgba(255,255,255,0.6)]">Branch:</strong> {a.branchName} {a.branchCode ? `(${a.branchCode})` : ""}
                        </p>
                      )}
                      {a.swiftCode && (
                        <p className="text-[0.78rem] text-muted m-0">
                          <strong className="text-[rgba(255,255,255,0.6)]">SWIFT:</strong> {a.swiftCode}
                        </p>
                      )}
                    </div>
                  </div>
                  {a.active && (
                    <div className="flex gap-1.5 shrink-0">
                      {!a.primary && (
                        <button
                          type="button"
                          disabled={settingPrimaryId === a.id}
                          onClick={() => setPrimaryMutation.mutate(a.id)}
                          className="px-2.5 py-1 rounded-sm text-xs font-semibold bg-success-soft text-success border border-[var(--success-glow)] cursor-pointer"
                          style={{ opacity: settingPrimaryId === a.id ? 0.6 : 1 }}
                        >
                          Set Primary
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => openEdit(a)}
                        className="px-2.5 py-1 rounded-sm text-xs font-semibold bg-accent-soft text-accent border border-[var(--accent-glow)] cursor-pointer"
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        disabled={deletingId === a.id}
                        onClick={() => deactivateMutation.mutate(a.id)}
                        className="px-2.5 py-1 rounded-sm text-xs font-semibold bg-danger-soft text-danger border border-[var(--danger-glow)] cursor-pointer"
                        style={{ opacity: deletingId === a.id ? 0.6 : 1 }}
                      >
                        {deletingId === a.id ? "..." : "Deactivate"}
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </VendorPageShell>
  );
}
