"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import { useAuthSession } from "../../../lib/authSession";

type SystemConfig = {
  id: string;
  configKey: string;
  configValue: string;
  description: string | null;
  valueType: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

type FeatureFlag = {
  id: string;
  flagKey: string;
  description: string | null;
  enabled: boolean;
  enabledForRoles: string | null;
  rolloutPercentage: number | null;
  createdAt: string;
  updatedAt: string;
};

type Tab = "config" | "flags";

export default function AdminSettingsPage() {
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus, isAuthenticated, canViewAdmin, profile, logout,
    canManageAdminOrders, canManageAdminProducts, canManageAdminCategories,
    canManageAdminVendors, canManageAdminPosters, apiClient, emailVerified, isSuperAdmin, isVendorAdmin,
  } = session;

  const [tab, setTab] = useState<Tab>("config");

  // System Config state
  const [configs, setConfigs] = useState<SystemConfig[]>([]);
  const [configLoading, setConfigLoading] = useState(true);
  const [editingConfig, setEditingConfig] = useState<SystemConfig | null>(null);
  const [showCreateConfig, setShowCreateConfig] = useState(false);
  const [configForm, setConfigForm] = useState({ configKey: "", configValue: "", description: "", valueType: "STRING", active: true });
  const [savingConfig, setSavingConfig] = useState(false);
  const [deletingConfigId, setDeletingConfigId] = useState<string | null>(null);

  // Feature Flags state
  const [flags, setFlags] = useState<FeatureFlag[]>([]);
  const [flagLoading, setFlagLoading] = useState(true);
  const [editingFlag, setEditingFlag] = useState<FeatureFlag | null>(null);
  const [showCreateFlag, setShowCreateFlag] = useState(false);
  const [flagForm, setFlagForm] = useState({ flagKey: "", description: "", enabled: false, enabledForRoles: "", rolloutPercentage: 100 });
  const [savingFlag, setSavingFlag] = useState(false);
  const [deletingFlagId, setDeletingFlagId] = useState<string | null>(null);

  const loadConfigs = useCallback(async () => {
    if (!apiClient) return;
    setConfigLoading(true);
    try {
      const res = await apiClient.get("/admin/system/config");
      setConfigs((res.data as SystemConfig[]) || []);
    } catch {
      toast.error("Failed to load system config");
    } finally {
      setConfigLoading(false);
    }
  }, [apiClient]);

  const loadFlags = useCallback(async () => {
    if (!apiClient) return;
    setFlagLoading(true);
    try {
      const res = await apiClient.get("/admin/system/feature-flags");
      setFlags((res.data as FeatureFlag[]) || []);
    } catch {
      toast.error("Failed to load feature flags");
    } finally {
      setFlagLoading(false);
    }
  }, [apiClient]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated || !isSuperAdmin) { router.replace("/"); return; }
    void loadConfigs();
    void loadFlags();
  }, [sessionStatus, isAuthenticated, isSuperAdmin, router, loadConfigs, loadFlags]);

  // --- System Config CRUD ---
  const openEditConfig = (c: SystemConfig) => {
    setEditingConfig(c);
    setConfigForm({ configKey: c.configKey, configValue: c.configValue, description: c.description || "", valueType: c.valueType, active: c.active });
    setShowCreateConfig(false);
  };

  const openCreateConfig = () => {
    setEditingConfig(null);
    setConfigForm({ configKey: "", configValue: "", description: "", valueType: "STRING", active: true });
    setShowCreateConfig(true);
  };

  const saveConfig = async () => {
    if (!apiClient || savingConfig || !configForm.configKey.trim()) return;
    setSavingConfig(true);
    try {
      const body = {
        configKey: configForm.configKey.trim(),
        configValue: configForm.configValue,
        description: configForm.description.trim() || null,
        valueType: configForm.valueType,
        active: configForm.active,
      };
      const res = await apiClient.put("/admin/system/config", body);
      const saved = res.data as SystemConfig;
      setConfigs((old) => {
        const idx = old.findIndex((c) => c.configKey === saved.configKey);
        if (idx >= 0) { const copy = [...old]; copy[idx] = saved; return copy; }
        return [...old, saved];
      });
      setEditingConfig(null);
      setShowCreateConfig(false);
      toast.success(editingConfig ? "Config updated" : "Config created");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save config");
    } finally {
      setSavingConfig(false);
    }
  };

  const deleteConfig = async (id: string) => {
    if (!apiClient || deletingConfigId) return;
    setDeletingConfigId(id);
    try {
      await apiClient.delete(`/admin/system/config/${id}`);
      setConfigs((old) => old.filter((c) => c.id !== id));
      toast.success("Config deleted");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to delete config");
    } finally {
      setDeletingConfigId(null);
    }
  };

  // --- Feature Flag CRUD ---
  const openEditFlag = (f: FeatureFlag) => {
    setEditingFlag(f);
    setFlagForm({ flagKey: f.flagKey, description: f.description || "", enabled: f.enabled, enabledForRoles: f.enabledForRoles || "", rolloutPercentage: f.rolloutPercentage ?? 100 });
    setShowCreateFlag(false);
  };

  const openCreateFlag = () => {
    setEditingFlag(null);
    setFlagForm({ flagKey: "", description: "", enabled: false, enabledForRoles: "", rolloutPercentage: 100 });
    setShowCreateFlag(true);
  };

  const saveFlag = async () => {
    if (!apiClient || savingFlag || !flagForm.flagKey.trim()) return;
    setSavingFlag(true);
    try {
      const body = {
        flagKey: flagForm.flagKey.trim(),
        description: flagForm.description.trim() || null,
        enabled: flagForm.enabled,
        enabledForRoles: flagForm.enabledForRoles.trim() || null,
        rolloutPercentage: flagForm.rolloutPercentage,
      };
      const res = await apiClient.put("/admin/system/feature-flags", body);
      const saved = res.data as FeatureFlag;
      setFlags((old) => {
        const idx = old.findIndex((f) => f.flagKey === saved.flagKey);
        if (idx >= 0) { const copy = [...old]; copy[idx] = saved; return copy; }
        return [...old, saved];
      });
      setEditingFlag(null);
      setShowCreateFlag(false);
      toast.success(editingFlag ? "Flag updated" : "Flag created");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save flag");
    } finally {
      setSavingFlag(false);
    }
  };

  const deleteFlag = async (id: string) => {
    if (!apiClient || deletingFlagId) return;
    setDeletingFlagId(id);
    try {
      await apiClient.delete(`/admin/system/feature-flags/${id}`);
      setFlags((old) => old.filter((f) => f.id !== id));
      toast.success("Flag deleted");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to delete flag");
    } finally {
      setDeletingFlagId(null);
    }
  };

  const toggleFlag = async (flag: FeatureFlag) => {
    if (!apiClient) return;
    try {
      const body = { flagKey: flag.flagKey, enabled: !flag.enabled };
      const res = await apiClient.put("/admin/system/feature-flags", body);
      const saved = res.data as FeatureFlag;
      setFlags((old) => old.map((f) => (f.id === saved.id ? saved : f)));
      toast.success(`${flag.flagKey} ${saved.enabled ? "enabled" : "disabled"}`);
    } catch {
      toast.error("Failed to toggle flag");
    }
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}><p style={{ color: "var(--muted)" }}>Loading...</p></div>;
  }

  const tabBtn = (t: Tab, label: string) => (
    <button
      type="button"
      onClick={() => { setTab(t); setEditingConfig(null); setEditingFlag(null); setShowCreateConfig(false); setShowCreateFlag(false); }}
      style={{
        padding: "8px 20px", borderRadius: "8px", fontSize: "0.82rem", fontWeight: 700, cursor: "pointer",
        background: tab === t ? "var(--accent-soft)" : "transparent",
        color: tab === t ? "var(--accent)" : "var(--muted)",
        border: tab === t ? "1px solid var(--accent-glow)" : "1px solid transparent",
      }}
    >
      {label}
    </button>
  );

  const inputStyle: React.CSSProperties = {
    width: "100%", padding: "8px 12px", borderRadius: "8px", fontSize: "0.82rem",
    background: "var(--bg)", border: "1px solid var(--line-bright)", color: "#fff",
  };

  const labelStyle: React.CSSProperties = {
    display: "block", fontSize: "0.72rem", fontWeight: 700, color: "var(--muted)", marginBottom: "4px",
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(profile?.email as string) || ""} isSuperAdmin={isSuperAdmin} isVendorAdmin={isVendorAdmin}
        canViewAdmin={canViewAdmin} canManageAdminOrders={canManageAdminOrders}
        canManageAdminProducts={canManageAdminProducts} canManageAdminCategories={canManageAdminCategories}
        canManageAdminVendors={canManageAdminVendors} canManageAdminPosters={canManageAdminPosters}
        apiClient={apiClient} emailVerified={emailVerified} onLogout={logout}
      />

      <main className="mx-auto max-w-5xl px-4 py-10">
        <div style={{ marginBottom: "24px" }}>
          <h1 className="text-2xl font-bold" style={{ color: "#fff" }}>Settings</h1>
          <p style={{ color: "var(--muted)", fontSize: "0.85rem", marginTop: "4px" }}>System configuration and feature flags</p>
        </div>

        {/* Tabs */}
        <div style={{ display: "flex", gap: "8px", marginBottom: "24px" }}>
          {tabBtn("config", "System Config")}
          {tabBtn("flags", "Feature Flags")}
        </div>

        {/* ===================== SYSTEM CONFIG TAB ===================== */}
        {tab === "config" && (
          <>
            <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: "16px" }}>
              <button
                type="button"
                onClick={showCreateConfig ? () => setShowCreateConfig(false) : openCreateConfig}
                style={{
                  padding: "8px 18px", borderRadius: "10px", fontSize: "0.82rem", fontWeight: 700,
                  background: "var(--gradient-brand)", color: "#fff", border: "none", cursor: "pointer",
                }}
              >
                {showCreateConfig ? "Cancel" : "+ Add Config"}
              </button>
            </div>

            {/* Create/Edit Form */}
            {(showCreateConfig || editingConfig) && (
              <div style={{
                marginBottom: "24px", padding: "20px", borderRadius: "14px",
                background: "var(--card)", border: "1px solid var(--line-bright)",
              }}>
                <h3 style={{ color: "#fff", fontSize: "0.95rem", fontWeight: 700, marginBottom: "16px" }}>
                  {editingConfig ? `Edit: ${editingConfig.configKey}` : "New Configuration"}
                </h3>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", marginBottom: "12px" }}>
                  <div>
                    <label style={labelStyle}>Config Key *</label>
                    <input
                      value={configForm.configKey}
                      onChange={(e) => setConfigForm({ ...configForm, configKey: e.target.value })}
                      placeholder="app.feature.name"
                      maxLength={200}
                      disabled={!!editingConfig}
                      style={{ ...inputStyle, opacity: editingConfig ? 0.6 : 1 }}
                    />
                  </div>
                  <div>
                    <label style={labelStyle}>Value Type</label>
                    <select
                      value={configForm.valueType}
                      onChange={(e) => setConfigForm({ ...configForm, valueType: e.target.value })}
                      style={inputStyle}
                    >
                      {["STRING", "INTEGER", "BOOLEAN", "JSON"].map((t) => <option key={t} value={t}>{t}</option>)}
                    </select>
                  </div>
                </div>
                <div style={{ marginBottom: "12px" }}>
                  <label style={labelStyle}>Value</label>
                  <textarea
                    value={configForm.configValue}
                    onChange={(e) => setConfigForm({ ...configForm, configValue: e.target.value })}
                    rows={3}
                    style={{ ...inputStyle, resize: "vertical" }}
                  />
                </div>
                <div style={{ marginBottom: "12px" }}>
                  <label style={labelStyle}>Description</label>
                  <input
                    value={configForm.description}
                    onChange={(e) => setConfigForm({ ...configForm, description: e.target.value })}
                    placeholder="What this config controls..."
                    maxLength={500}
                    style={inputStyle}
                  />
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: "16px", marginBottom: "16px" }}>
                  <label style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.82rem", color: "#fff", cursor: "pointer" }}>
                    <input
                      type="checkbox"
                      checked={configForm.active}
                      onChange={(e) => setConfigForm({ ...configForm, active: e.target.checked })}
                    />
                    Active
                  </label>
                </div>
                <div style={{ display: "flex", gap: "8px" }}>
                  <button
                    type="button"
                    disabled={savingConfig || !configForm.configKey.trim()}
                    onClick={() => { void saveConfig(); }}
                    style={{
                      padding: "8px 24px", borderRadius: "10px", fontSize: "0.82rem", fontWeight: 700,
                      background: "var(--gradient-brand)", color: "#fff", border: "none",
                      cursor: savingConfig ? "not-allowed" : "pointer", opacity: savingConfig ? 0.6 : 1,
                    }}
                  >
                    {savingConfig ? "Saving..." : editingConfig ? "Update" : "Create"}
                  </button>
                  {editingConfig && (
                    <button type="button" onClick={() => setEditingConfig(null)} style={{ padding: "8px 16px", borderRadius: "10px", fontSize: "0.82rem", fontWeight: 600, background: "transparent", color: "var(--muted)", border: "1px solid var(--line-bright)", cursor: "pointer" }}>
                      Cancel
                    </button>
                  )}
                </div>
              </div>
            )}

            {/* Config List */}
            {configLoading ? (
              <p style={{ color: "var(--muted)", textAlign: "center", padding: "40px 0" }}>Loading configurations...</p>
            ) : configs.length === 0 ? (
              <div style={{ textAlign: "center", padding: "60px 20px", borderRadius: "14px", background: "var(--card)", border: "1px solid var(--line-bright)" }}>
                <p style={{ color: "var(--muted)", fontSize: "0.9rem" }}>No system configurations found.</p>
              </div>
            ) : (
              <div style={{ display: "grid", gap: "10px" }}>
                {configs.map((c) => (
                  <div
                    key={c.id}
                    style={{
                      padding: "16px", borderRadius: "12px", background: "var(--card)", border: "1px solid var(--line-bright)",
                      display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "12px",
                    }}
                  >
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "4px" }}>
                        <span style={{ fontSize: "0.85rem", fontWeight: 700, color: "#fff", fontFamily: "monospace" }}>{c.configKey}</span>
                        <span style={{
                          fontSize: "0.62rem", fontWeight: 700, padding: "1px 6px", borderRadius: "4px",
                          background: c.active ? "var(--success-soft)" : "var(--danger-soft)",
                          color: c.active ? "var(--success)" : "var(--danger)",
                        }}>
                          {c.active ? "ACTIVE" : "INACTIVE"}
                        </span>
                        <span style={{ fontSize: "0.62rem", fontWeight: 600, padding: "1px 6px", borderRadius: "4px", background: "var(--accent-soft)", color: "var(--accent)" }}>
                          {c.valueType}
                        </span>
                      </div>
                      {c.description && <p style={{ fontSize: "0.75rem", color: "var(--muted)", margin: "2px 0 4px" }}>{c.description}</p>}
                      <p style={{ fontSize: "0.78rem", color: "rgba(255,255,255,0.7)", fontFamily: "monospace", wordBreak: "break-all", margin: 0 }}>
                        {c.configValue.length > 120 ? c.configValue.slice(0, 120) + "..." : c.configValue}
                      </p>
                    </div>
                    <div style={{ display: "flex", gap: "6px", flexShrink: 0 }}>
                      <button type="button" onClick={() => openEditConfig(c)} style={{ padding: "4px 10px", borderRadius: "6px", fontSize: "0.72rem", fontWeight: 600, background: "var(--accent-soft)", color: "var(--accent)", border: "1px solid var(--accent-glow)", cursor: "pointer" }}>
                        Edit
                      </button>
                      <button
                        type="button"
                        disabled={deletingConfigId === c.id}
                        onClick={() => { void deleteConfig(c.id); }}
                        style={{ padding: "4px 10px", borderRadius: "6px", fontSize: "0.72rem", fontWeight: 600, background: "var(--danger-soft)", color: "var(--danger)", border: "1px solid var(--danger-glow)", cursor: "pointer", opacity: deletingConfigId === c.id ? 0.6 : 1 }}
                      >
                        {deletingConfigId === c.id ? "..." : "Delete"}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </>
        )}

        {/* ===================== FEATURE FLAGS TAB ===================== */}
        {tab === "flags" && (
          <>
            <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: "16px" }}>
              <button
                type="button"
                onClick={showCreateFlag ? () => setShowCreateFlag(false) : openCreateFlag}
                style={{
                  padding: "8px 18px", borderRadius: "10px", fontSize: "0.82rem", fontWeight: 700,
                  background: "var(--gradient-brand)", color: "#fff", border: "none", cursor: "pointer",
                }}
              >
                {showCreateFlag ? "Cancel" : "+ Add Flag"}
              </button>
            </div>

            {/* Create/Edit Form */}
            {(showCreateFlag || editingFlag) && (
              <div style={{
                marginBottom: "24px", padding: "20px", borderRadius: "14px",
                background: "var(--card)", border: "1px solid var(--line-bright)",
              }}>
                <h3 style={{ color: "#fff", fontSize: "0.95rem", fontWeight: 700, marginBottom: "16px" }}>
                  {editingFlag ? `Edit: ${editingFlag.flagKey}` : "New Feature Flag"}
                </h3>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", marginBottom: "12px" }}>
                  <div>
                    <label style={labelStyle}>Flag Key *</label>
                    <input
                      value={flagForm.flagKey}
                      onChange={(e) => setFlagForm({ ...flagForm, flagKey: e.target.value })}
                      placeholder="feature.dark-mode"
                      maxLength={200}
                      disabled={!!editingFlag}
                      style={{ ...inputStyle, opacity: editingFlag ? 0.6 : 1 }}
                    />
                  </div>
                  <div>
                    <label style={labelStyle}>Rollout %</label>
                    <input
                      type="number"
                      min={0}
                      max={100}
                      value={flagForm.rolloutPercentage}
                      onChange={(e) => setFlagForm({ ...flagForm, rolloutPercentage: Number(e.target.value) })}
                      style={inputStyle}
                    />
                  </div>
                </div>
                <div style={{ marginBottom: "12px" }}>
                  <label style={labelStyle}>Description</label>
                  <input
                    value={flagForm.description}
                    onChange={(e) => setFlagForm({ ...flagForm, description: e.target.value })}
                    placeholder="What this flag controls..."
                    maxLength={500}
                    style={inputStyle}
                  />
                </div>
                <div style={{ marginBottom: "12px" }}>
                  <label style={labelStyle}>Enabled For Roles (comma-separated)</label>
                  <input
                    value={flagForm.enabledForRoles}
                    onChange={(e) => setFlagForm({ ...flagForm, enabledForRoles: e.target.value })}
                    placeholder="super_admin, vendor_admin"
                    maxLength={500}
                    style={inputStyle}
                  />
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: "16px", marginBottom: "16px" }}>
                  <label style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.82rem", color: "#fff", cursor: "pointer" }}>
                    <input
                      type="checkbox"
                      checked={flagForm.enabled}
                      onChange={(e) => setFlagForm({ ...flagForm, enabled: e.target.checked })}
                    />
                    Enabled
                  </label>
                </div>
                <div style={{ display: "flex", gap: "8px" }}>
                  <button
                    type="button"
                    disabled={savingFlag || !flagForm.flagKey.trim()}
                    onClick={() => { void saveFlag(); }}
                    style={{
                      padding: "8px 24px", borderRadius: "10px", fontSize: "0.82rem", fontWeight: 700,
                      background: "var(--gradient-brand)", color: "#fff", border: "none",
                      cursor: savingFlag ? "not-allowed" : "pointer", opacity: savingFlag ? 0.6 : 1,
                    }}
                  >
                    {savingFlag ? "Saving..." : editingFlag ? "Update" : "Create"}
                  </button>
                  {editingFlag && (
                    <button type="button" onClick={() => setEditingFlag(null)} style={{ padding: "8px 16px", borderRadius: "10px", fontSize: "0.82rem", fontWeight: 600, background: "transparent", color: "var(--muted)", border: "1px solid var(--line-bright)", cursor: "pointer" }}>
                      Cancel
                    </button>
                  )}
                </div>
              </div>
            )}

            {/* Flags List */}
            {flagLoading ? (
              <p style={{ color: "var(--muted)", textAlign: "center", padding: "40px 0" }}>Loading feature flags...</p>
            ) : flags.length === 0 ? (
              <div style={{ textAlign: "center", padding: "60px 20px", borderRadius: "14px", background: "var(--card)", border: "1px solid var(--line-bright)" }}>
                <p style={{ color: "var(--muted)", fontSize: "0.9rem" }}>No feature flags found.</p>
              </div>
            ) : (
              <div style={{ display: "grid", gap: "10px" }}>
                {flags.map((f) => (
                  <div
                    key={f.id}
                    style={{
                      padding: "16px", borderRadius: "12px", background: "var(--card)", border: "1px solid var(--line-bright)",
                      display: "flex", alignItems: "center", justifyContent: "space-between", gap: "12px",
                    }}
                  >
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "4px" }}>
                        <span style={{ fontSize: "0.85rem", fontWeight: 700, color: "#fff", fontFamily: "monospace" }}>{f.flagKey}</span>
                        <span style={{
                          fontSize: "0.62rem", fontWeight: 700, padding: "1px 6px", borderRadius: "4px",
                          background: f.enabled ? "var(--success-soft)" : "var(--danger-soft)",
                          color: f.enabled ? "var(--success)" : "var(--danger)",
                        }}>
                          {f.enabled ? "ON" : "OFF"}
                        </span>
                        {f.rolloutPercentage !== null && f.rolloutPercentage < 100 && (
                          <span style={{ fontSize: "0.62rem", fontWeight: 600, padding: "1px 6px", borderRadius: "4px", background: "var(--warning-soft)", color: "var(--warning-text)" }}>
                            {f.rolloutPercentage}%
                          </span>
                        )}
                      </div>
                      {f.description && <p style={{ fontSize: "0.75rem", color: "var(--muted)", margin: "2px 0" }}>{f.description}</p>}
                      {f.enabledForRoles && (
                        <p style={{ fontSize: "0.7rem", color: "rgba(255,255,255,0.5)", margin: "2px 0" }}>
                          Roles: {f.enabledForRoles}
                        </p>
                      )}
                    </div>
                    <div style={{ display: "flex", gap: "6px", flexShrink: 0 }}>
                      <button
                        type="button"
                        onClick={() => { void toggleFlag(f); }}
                        style={{
                          padding: "4px 10px", borderRadius: "6px", fontSize: "0.72rem", fontWeight: 600, cursor: "pointer",
                          background: f.enabled ? "var(--danger-soft)" : "var(--success-soft)",
                          color: f.enabled ? "var(--danger)" : "var(--success)",
                          border: `1px solid ${f.enabled ? "var(--danger-glow)" : "var(--success-glow)"}`,
                        }}
                      >
                        {f.enabled ? "Disable" : "Enable"}
                      </button>
                      <button type="button" onClick={() => openEditFlag(f)} style={{ padding: "4px 10px", borderRadius: "6px", fontSize: "0.72rem", fontWeight: 600, background: "var(--accent-soft)", color: "var(--accent)", border: "1px solid var(--accent-glow)", cursor: "pointer" }}>
                        Edit
                      </button>
                      <button
                        type="button"
                        disabled={deletingFlagId === f.id}
                        onClick={() => { void deleteFlag(f.id); }}
                        style={{ padding: "4px 10px", borderRadius: "6px", fontSize: "0.72rem", fontWeight: 600, background: "var(--danger-soft)", color: "var(--danger)", border: "1px solid var(--danger-glow)", cursor: "pointer", opacity: deletingFlagId === f.id ? 0.6 : 1 }}
                      >
                        {deletingFlagId === f.id ? "..." : "Delete"}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </main>

      <Footer />
    </div>
  );
}
