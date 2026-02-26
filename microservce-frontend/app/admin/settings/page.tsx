"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import AdminPageShell from "../../components/ui/AdminPageShell";
import SystemConfigTab from "../../components/admin/settings/SystemConfigTab";
import FeatureFlagsTab from "../../components/admin/settings/FeatureFlagsTab";
import type { SystemConfig, FeatureFlag } from "../../components/admin/settings/types";

type Tab = "config" | "flags";

export default function AdminSettingsPage() {
  const session = useAuthSession();
  const { status: sessionStatus, apiClient } = session;
  const queryClient = useQueryClient();

  const [tab, setTab] = useState<Tab>("config");

  // System Config UI state
  const [editingConfig, setEditingConfig] = useState<SystemConfig | null>(null);
  const [showCreateConfig, setShowCreateConfig] = useState(false);
  const [configForm, setConfigForm] = useState({ configKey: "", configValue: "", description: "", valueType: "STRING", active: true });

  // Feature Flags UI state
  const [editingFlag, setEditingFlag] = useState<FeatureFlag | null>(null);
  const [showCreateFlag, setShowCreateFlag] = useState(false);
  const [flagForm, setFlagForm] = useState({ flagKey: "", description: "", enabled: false, enabledForRoles: "", rolloutPercentage: 100 });

  const ready = sessionStatus === "ready" && !!apiClient;

  // --- System Config query ---
  const { data: configs = [], isLoading: configLoading } = useQuery({
    queryKey: ["admin-settings-configs"],
    queryFn: async () => {
      const res = await apiClient!.get("/admin/system/config");
      return (res.data as SystemConfig[]) || [];
    },
    enabled: ready,
  });

  // --- Feature Flags query ---
  const { data: flags = [], isLoading: flagLoading } = useQuery({
    queryKey: ["admin-settings-flags"],
    queryFn: async () => {
      const res = await apiClient!.get("/admin/system/feature-flags");
      return (res.data as FeatureFlag[]) || [];
    },
    enabled: ready,
  });

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

  const saveConfigMutation = useMutation({
    mutationFn: async () => {
      if (!configForm.configKey.trim()) throw new Error("Config key is required");
      const body = {
        configKey: configForm.configKey.trim(),
        configValue: configForm.configValue,
        description: configForm.description.trim() || null,
        valueType: configForm.valueType,
        active: configForm.active,
      };
      await apiClient!.put("/admin/system/config", body);
    },
    onSuccess: () => {
      setEditingConfig(null);
      setShowCreateConfig(false);
      toast.success(editingConfig ? "Config updated" : "Config created");
      void queryClient.invalidateQueries({ queryKey: ["admin-settings-configs"] });
    },
    onError: (err) => toast.error(err instanceof Error ? err.message : "Failed to save config"),
  });

  const deleteConfigMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient!.delete(`/admin/system/config/${id}`);
    },
    onSuccess: () => {
      toast.success("Config deleted");
      void queryClient.invalidateQueries({ queryKey: ["admin-settings-configs"] });
    },
    onError: (err) => toast.error(err instanceof Error ? err.message : "Failed to delete config"),
  });

  const savingConfig = saveConfigMutation.isPending;
  const deletingConfigId = deleteConfigMutation.isPending ? (deleteConfigMutation.variables as string | undefined) ?? null : null;

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

  const saveFlagMutation = useMutation({
    mutationFn: async () => {
      if (!flagForm.flagKey.trim()) throw new Error("Flag key is required");
      const body = {
        flagKey: flagForm.flagKey.trim(),
        description: flagForm.description.trim() || null,
        enabled: flagForm.enabled,
        enabledForRoles: flagForm.enabledForRoles.trim() || null,
        rolloutPercentage: flagForm.rolloutPercentage,
      };
      await apiClient!.put("/admin/system/feature-flags", body);
    },
    onSuccess: () => {
      setEditingFlag(null);
      setShowCreateFlag(false);
      toast.success(editingFlag ? "Flag updated" : "Flag created");
      void queryClient.invalidateQueries({ queryKey: ["admin-settings-flags"] });
    },
    onError: (err) => toast.error(err instanceof Error ? err.message : "Failed to save flag"),
  });

  const deleteFlagMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient!.delete(`/admin/system/feature-flags/${id}`);
    },
    onSuccess: () => {
      toast.success("Flag deleted");
      void queryClient.invalidateQueries({ queryKey: ["admin-settings-flags"] });
    },
    onError: (err) => toast.error(err instanceof Error ? err.message : "Failed to delete flag"),
  });

  const toggleFlagMutation = useMutation({
    mutationFn: async (flag: FeatureFlag) => {
      const body = { flagKey: flag.flagKey, enabled: !flag.enabled };
      const res = await apiClient!.put("/admin/system/feature-flags", body);
      return res.data as FeatureFlag;
    },
    onSuccess: (saved) => {
      toast.success(`${saved.flagKey} ${saved.enabled ? "enabled" : "disabled"}`);
      void queryClient.invalidateQueries({ queryKey: ["admin-settings-flags"] });
    },
    onError: () => toast.error("Failed to toggle flag"),
  });

  const savingFlag = saveFlagMutation.isPending;
  const deletingFlagId = deleteFlagMutation.isPending ? (deleteFlagMutation.variables as string | undefined) ?? null : null;

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <div className="min-h-screen bg-bg grid place-items-center"><p className="text-muted">Loading...</p></div>;
  }

  const tabBtn = (t: Tab, label: string) => (
    <button
      type="button"
      onClick={() => { setTab(t); setEditingConfig(null); setEditingFlag(null); setShowCreateConfig(false); setShowCreateFlag(false); }}
      className={`px-5 py-2 rounded-sm text-base font-bold cursor-pointer border ${tab === t ? "bg-accent-soft text-accent border-accent-glow" : "bg-transparent text-muted border-transparent"}`}
    >
      {label}
    </button>
  );

  return (
    <AdminPageShell
      title="Settings"
      breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Settings" }]}
    >
      {/* Tabs */}
      <div className="flex gap-2 mb-6">
        {tabBtn("config", "System Config")}
        {tabBtn("flags", "Feature Flags")}
      </div>

      {/* System Config Tab */}
      {tab === "config" && (
        <SystemConfigTab
          configs={configs}
          configLoading={configLoading}
          editingConfig={editingConfig}
          showCreateConfig={showCreateConfig}
          configForm={configForm}
          onConfigFormChange={setConfigForm}
          savingConfig={savingConfig}
          deletingConfigId={deletingConfigId}
          onOpenCreate={openCreateConfig}
          onOpenEdit={openEditConfig}
          onSave={() => saveConfigMutation.mutate()}
          onDelete={(id) => deleteConfigMutation.mutate(id)}
          onCancelEdit={() => setEditingConfig(null)}
          onToggleCreate={() => setShowCreateConfig(false)}
        />
      )}

      {/* Feature Flags Tab */}
      {tab === "flags" && (
        <FeatureFlagsTab
          flags={flags}
          flagLoading={flagLoading}
          editingFlag={editingFlag}
          showCreateFlag={showCreateFlag}
          flagForm={flagForm}
          onFlagFormChange={setFlagForm}
          savingFlag={savingFlag}
          deletingFlagId={deletingFlagId}
          onOpenCreate={openCreateFlag}
          onOpenEdit={openEditFlag}
          onSave={() => saveFlagMutation.mutate()}
          onDelete={(id) => deleteFlagMutation.mutate(id)}
          onToggle={(flag) => toggleFlagMutation.mutate(flag)}
          onCancelEdit={() => setEditingFlag(null)}
          onToggleCreate={() => setShowCreateFlag(false)}
        />
      )}
    </AdminPageShell>
  );
}
