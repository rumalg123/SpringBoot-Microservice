"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import { useAuthSession } from "../../../lib/authSession";

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
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus, isAuthenticated, canViewAdmin, profile, logout,
    canManageAdminOrders, canManageAdminProducts, canManageAdminCategories,
    canManageAdminVendors, canManageAdminPosters, apiClient, emailVerified, isSuperAdmin, isVendorAdmin,
  } = session;

  const [accounts, setAccounts] = useState<BankAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<FormData>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [settingPrimaryId, setSettingPrimaryId] = useState<string | null>(null);

  const loadAccounts = useCallback(async () => {
    if (!apiClient) return;
    setLoading(true);
    try {
      const res = await apiClient.get("/admin/payments/bank-accounts?size=100");
      const data = res.data as { content: BankAccount[] };
      setAccounts(data.content || []);
    } catch {
      toast.error("Failed to load bank accounts");
    } finally {
      setLoading(false);
    }
  }, [apiClient]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated || !isVendorAdmin) { router.replace("/"); return; }
    void loadAccounts();
  }, [sessionStatus, isAuthenticated, isVendorAdmin, router, loadAccounts]);

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

  const save = async () => {
    if (!apiClient || saving || !form.bankName.trim() || !form.accountNumber.trim() || !form.accountHolderName.trim()) return;
    setSaving(true);
    try {
      if (editingId) {
        const body: Record<string, string | null> = {};
        if (form.bankName.trim()) body.bankName = form.bankName.trim();
        if (form.branchName.trim()) body.branchName = form.branchName.trim();
        if (form.branchCode.trim()) body.branchCode = form.branchCode.trim();
        if (form.accountNumber.trim()) body.accountNumber = form.accountNumber.trim();
        if (form.accountHolderName.trim()) body.accountHolderName = form.accountHolderName.trim();
        if (form.swiftCode.trim()) body.swiftCode = form.swiftCode.trim();
        const res = await apiClient.put(`/admin/payments/bank-accounts/${editingId}`, body);
        const updated = res.data as BankAccount;
        setAccounts((old) => old.map((a) => (a.id === editingId ? updated : a)));
        toast.success("Bank account updated");
      } else {
        const body = {
          bankName: form.bankName.trim(),
          branchName: form.branchName.trim() || null,
          branchCode: form.branchCode.trim() || null,
          accountNumber: form.accountNumber.trim(),
          accountHolderName: form.accountHolderName.trim(),
          swiftCode: form.swiftCode.trim() || null,
        };
        const res = await apiClient.post("/admin/payments/bank-accounts", body);
        const created = res.data as BankAccount;
        setAccounts((old) => [...old, created]);
        toast.success("Bank account added");
      }
      setShowForm(false);
      setEditingId(null);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save bank account");
    } finally {
      setSaving(false);
    }
  };

  const deactivate = async (id: string) => {
    if (!apiClient || deletingId) return;
    setDeletingId(id);
    try {
      await apiClient.delete(`/admin/payments/bank-accounts/${id}`);
      setAccounts((old) => old.map((a) => (a.id === id ? { ...a, active: false } : a)));
      toast.success("Bank account deactivated");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to deactivate");
    } finally {
      setDeletingId(null);
    }
  };

  const setPrimary = async (id: string) => {
    if (!apiClient || settingPrimaryId) return;
    setSettingPrimaryId(id);
    try {
      await apiClient.post(`/admin/payments/bank-accounts/${id}/set-primary`);
      setAccounts((old) => old.map((a) => ({ ...a, primary: a.id === id })));
      toast.success("Set as primary account");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to set primary");
    } finally {
      setSettingPrimaryId(null);
    }
  };

  const inputStyle: React.CSSProperties = {
    width: "100%", padding: "8px 12px", borderRadius: "8px", fontSize: "0.82rem",
    background: "var(--bg)", border: "1px solid var(--line-bright)", color: "#fff",
  };
  const labelStyle: React.CSSProperties = {
    display: "block", fontSize: "0.72rem", fontWeight: 700, color: "var(--muted)", marginBottom: "4px",
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}><p style={{ color: "var(--muted)" }}>Loading...</p></div>;
  }

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(profile?.email as string) || ""} isSuperAdmin={isSuperAdmin} isVendorAdmin={isVendorAdmin}
        canViewAdmin={canViewAdmin} canManageAdminOrders={canManageAdminOrders}
        canManageAdminProducts={canManageAdminProducts} canManageAdminCategories={canManageAdminCategories}
        canManageAdminVendors={canManageAdminVendors} canManageAdminPosters={canManageAdminPosters}
        apiClient={apiClient} emailVerified={emailVerified} onLogout={logout}
      />

      <main className="mx-auto max-w-4xl px-4 py-10">
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "24px" }}>
          <div>
            <h1 className="text-2xl font-bold" style={{ color: "#fff" }}>Bank Accounts</h1>
            <p style={{ color: "var(--muted)", fontSize: "0.85rem", marginTop: "4px" }}>Manage your bank accounts for payouts</p>
          </div>
          <button
            type="button"
            onClick={showForm ? () => { setShowForm(false); setEditingId(null); } : openCreate}
            style={{
              padding: "8px 18px", borderRadius: "10px", fontSize: "0.82rem", fontWeight: 700,
              background: "var(--gradient-brand)", color: "#fff", border: "none", cursor: "pointer",
            }}
          >
            {showForm ? "Cancel" : "+ Add Account"}
          </button>
        </div>

        {/* Form */}
        {showForm && (
          <div style={{
            marginBottom: "24px", padding: "20px", borderRadius: "14px",
            background: "var(--card)", border: "1px solid var(--line-bright)",
          }}>
            <h3 style={{ color: "#fff", fontSize: "0.95rem", fontWeight: 700, marginBottom: "16px" }}>
              {editingId ? "Edit Bank Account" : "Add Bank Account"}
            </h3>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", marginBottom: "12px" }}>
              <div>
                <label style={labelStyle}>Bank Name *</label>
                <input value={form.bankName} onChange={(e) => setForm({ ...form, bankName: e.target.value })} placeholder="Bank of Ceylon" maxLength={200} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Account Holder Name *</label>
                <input value={form.accountHolderName} onChange={(e) => setForm({ ...form, accountHolderName: e.target.value })} placeholder="John Doe" maxLength={200} style={inputStyle} />
              </div>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", marginBottom: "12px" }}>
              <div>
                <label style={labelStyle}>Account Number *</label>
                <input value={form.accountNumber} onChange={(e) => setForm({ ...form, accountNumber: e.target.value })} placeholder="123456789" maxLength={100} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Branch Name</label>
                <input value={form.branchName} onChange={(e) => setForm({ ...form, branchName: e.target.value })} placeholder="Main Branch" maxLength={200} style={inputStyle} />
              </div>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", marginBottom: "16px" }}>
              <div>
                <label style={labelStyle}>Branch Code</label>
                <input value={form.branchCode} onChange={(e) => setForm({ ...form, branchCode: e.target.value })} placeholder="001" maxLength={50} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>SWIFT Code</label>
                <input value={form.swiftCode} onChange={(e) => setForm({ ...form, swiftCode: e.target.value })} placeholder="BABORKLK" maxLength={20} style={inputStyle} />
              </div>
            </div>
            <button
              type="button"
              disabled={saving || !form.bankName.trim() || !form.accountNumber.trim() || !form.accountHolderName.trim()}
              onClick={() => { void save(); }}
              style={{
                padding: "8px 24px", borderRadius: "10px", fontSize: "0.82rem", fontWeight: 700,
                background: "var(--gradient-brand)", color: "#fff", border: "none",
                cursor: saving ? "not-allowed" : "pointer", opacity: saving ? 0.6 : 1,
              }}
            >
              {saving ? "Saving..." : editingId ? "Update" : "Add Account"}
            </button>
          </div>
        )}

        {/* Accounts List */}
        {loading ? (
          <p style={{ color: "var(--muted)", textAlign: "center", padding: "40px 0" }}>Loading bank accounts...</p>
        ) : accounts.length === 0 ? (
          <div style={{ textAlign: "center", padding: "60px 20px", borderRadius: "14px", background: "var(--card)", border: "1px solid var(--line-bright)" }}>
            <p style={{ color: "var(--muted)", fontSize: "0.9rem" }}>No bank accounts found. Add one to receive payouts.</p>
          </div>
        ) : (
          <div style={{ display: "grid", gap: "12px" }}>
            {accounts.map((a) => (
              <div
                key={a.id}
                style={{
                  padding: "18px", borderRadius: "14px", background: "var(--card)",
                  border: a.primary ? "1px solid var(--success-glow)" : "1px solid var(--line-bright)",
                  opacity: a.active ? 1 : 0.5,
                }}
              >
                <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "12px" }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
                      <span style={{ fontSize: "0.95rem", fontWeight: 700, color: "#fff" }}>{a.bankName}</span>
                      {a.primary && (
                        <span style={{ fontSize: "0.62rem", fontWeight: 700, padding: "1px 6px", borderRadius: "4px", background: "var(--success-soft)", color: "var(--success)" }}>
                          PRIMARY
                        </span>
                      )}
                      {!a.active && (
                        <span style={{ fontSize: "0.62rem", fontWeight: 700, padding: "1px 6px", borderRadius: "4px", background: "var(--danger-soft)", color: "var(--danger)" }}>
                          INACTIVE
                        </span>
                      )}
                    </div>
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "6px 20px" }}>
                      <p style={{ fontSize: "0.78rem", color: "var(--muted)", margin: 0 }}>
                        <strong style={{ color: "rgba(255,255,255,0.6)" }}>Account:</strong> {a.accountNumber}
                      </p>
                      <p style={{ fontSize: "0.78rem", color: "var(--muted)", margin: 0 }}>
                        <strong style={{ color: "rgba(255,255,255,0.6)" }}>Holder:</strong> {a.accountHolderName}
                      </p>
                      {a.branchName && (
                        <p style={{ fontSize: "0.78rem", color: "var(--muted)", margin: 0 }}>
                          <strong style={{ color: "rgba(255,255,255,0.6)" }}>Branch:</strong> {a.branchName} {a.branchCode ? `(${a.branchCode})` : ""}
                        </p>
                      )}
                      {a.swiftCode && (
                        <p style={{ fontSize: "0.78rem", color: "var(--muted)", margin: 0 }}>
                          <strong style={{ color: "rgba(255,255,255,0.6)" }}>SWIFT:</strong> {a.swiftCode}
                        </p>
                      )}
                    </div>
                  </div>
                  {a.active && (
                    <div style={{ display: "flex", gap: "6px", flexShrink: 0 }}>
                      {!a.primary && (
                        <button
                          type="button"
                          disabled={settingPrimaryId === a.id}
                          onClick={() => { void setPrimary(a.id); }}
                          style={{
                            padding: "4px 10px", borderRadius: "6px", fontSize: "0.72rem", fontWeight: 600,
                            background: "var(--success-soft)", color: "var(--success)",
                            border: "1px solid var(--success-glow)", cursor: "pointer",
                            opacity: settingPrimaryId === a.id ? 0.6 : 1,
                          }}
                        >
                          Set Primary
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => openEdit(a)}
                        style={{
                          padding: "4px 10px", borderRadius: "6px", fontSize: "0.72rem", fontWeight: 600,
                          background: "var(--accent-soft)", color: "var(--accent)",
                          border: "1px solid var(--accent-glow)", cursor: "pointer",
                        }}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        disabled={deletingId === a.id}
                        onClick={() => { void deactivate(a.id); }}
                        style={{
                          padding: "4px 10px", borderRadius: "6px", fontSize: "0.72rem", fontWeight: 600,
                          background: "var(--danger-soft)", color: "var(--danger)",
                          border: "1px solid var(--danger-glow)", cursor: "pointer",
                          opacity: deletingId === a.id ? 0.6 : 1,
                        }}
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
      </main>

      <Footer />
    </div>
  );
}
