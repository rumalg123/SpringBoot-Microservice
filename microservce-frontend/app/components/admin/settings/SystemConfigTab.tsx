"use client";

import TableSkeleton from "../../ui/TableSkeleton";
import type { SystemConfig } from "./types";

type ConfigForm = {
  configKey: string;
  configValue: string;
  description: string;
  valueType: string;
  active: boolean;
};

type Props = {
  configs: SystemConfig[];
  configLoading: boolean;
  editingConfig: SystemConfig | null;
  showCreateConfig: boolean;
  configForm: ConfigForm;
  onConfigFormChange: (form: ConfigForm) => void;
  savingConfig: boolean;
  deletingConfigId: string | null;
  onOpenCreate: () => void;
  onOpenEdit: (c: SystemConfig) => void;
  onSave: () => void;
  onDelete: (id: string) => void;
  onCancelEdit: () => void;
  onToggleCreate: () => void;
};

export default function SystemConfigTab({
  configs,
  configLoading,
  editingConfig,
  showCreateConfig,
  configForm,
  onConfigFormChange,
  savingConfig,
  deletingConfigId,
  onOpenCreate,
  onOpenEdit,
  onSave,
  onDelete,
  onCancelEdit,
  onToggleCreate,
}: Props) {
  return (
    <>
      <div className="mb-4 flex justify-end">
        <button
          type="button"
          onClick={showCreateConfig ? onToggleCreate : onOpenCreate}
          className="rounded-md bg-[image:var(--gradient-brand)] px-[18px] py-2 text-sm font-bold text-white border-none cursor-pointer"
        >
          {showCreateConfig ? "Cancel" : "+ Add Config"}
        </button>
      </div>

      {/* Create/Edit Form */}
      {(showCreateConfig || editingConfig) && (
        <div className="mb-6 rounded-[14px] border border-line-bright bg-[var(--card)] p-5">
          <h3 className="mb-4 text-[0.95rem] font-bold text-white">
            {editingConfig ? `Edit: ${editingConfig.configKey}` : "New Configuration"}
          </h3>
          <div className="mb-3 grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-[0.72rem] font-bold text-muted">Config Key *</label>
              <input
                value={configForm.configKey}
                onChange={(e) => onConfigFormChange({ ...configForm, configKey: e.target.value })}
                placeholder="app.feature.name"
                maxLength={200}
                disabled={!!editingConfig}
                className="w-full rounded-lg bg-bg border border-line-bright px-3 py-2 text-sm text-white"
                style={{ opacity: editingConfig ? 0.6 : 1 }}
              />
            </div>
            <div>
              <label className="mb-1 block text-[0.72rem] font-bold text-muted">Value Type</label>
              <select
                value={configForm.valueType}
                onChange={(e) => onConfigFormChange({ ...configForm, valueType: e.target.value })}
                className="w-full rounded-lg bg-bg border border-line-bright px-3 py-2 text-sm text-white"
              >
                {["STRING", "INTEGER", "BOOLEAN", "JSON"].map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
          </div>
          <div className="mb-3">
            <label className="mb-1 block text-[0.72rem] font-bold text-muted">Value</label>
            <textarea
              value={configForm.configValue}
              onChange={(e) => onConfigFormChange({ ...configForm, configValue: e.target.value })}
              rows={3}
              className="w-full rounded-lg bg-bg border border-line-bright px-3 py-2 text-sm text-white resize-y"
            />
          </div>
          <div className="mb-3">
            <label className="mb-1 block text-[0.72rem] font-bold text-muted">Description</label>
            <input
              value={configForm.description}
              onChange={(e) => onConfigFormChange({ ...configForm, description: e.target.value })}
              placeholder="What this config controls..."
              maxLength={500}
              className="w-full rounded-lg bg-bg border border-line-bright px-3 py-2 text-sm text-white"
            />
          </div>
          <div className="mb-4 flex items-center gap-4">
            <label className="flex items-center gap-1.5 text-sm text-white cursor-pointer">
              <input
                type="checkbox"
                checked={configForm.active}
                onChange={(e) => onConfigFormChange({ ...configForm, active: e.target.checked })}
              />
              Active
            </label>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={savingConfig || !configForm.configKey.trim()}
              onClick={onSave}
              className="rounded-md bg-[image:var(--gradient-brand)] px-6 py-2 text-sm font-bold text-white border-none"
              style={{ cursor: savingConfig ? "not-allowed" : "pointer", opacity: savingConfig ? 0.6 : 1 }}
            >
              {savingConfig ? "Saving..." : editingConfig ? "Update" : "Create"}
            </button>
            {editingConfig && (
              <button type="button" onClick={onCancelEdit} className="rounded-md border border-line-bright bg-transparent px-4 py-2 text-sm font-semibold text-muted cursor-pointer">
                Cancel
              </button>
            )}
          </div>
        </div>
      )}

      {/* Config List */}
      {configLoading ? (
        <TableSkeleton rows={4} cols={3} />
      ) : configs.length === 0 ? (
        <div className="rounded-[14px] border border-line-bright bg-[var(--card)] px-5 py-[60px] text-center">
          <p className="text-[0.9rem] text-muted">No system configurations found.</p>
        </div>
      ) : (
        <div className="grid gap-2.5">
          {configs.map((c) => (
            <div
              key={c.id}
              className="flex items-start justify-between gap-3 rounded-xl border border-line-bright bg-[var(--card)] p-4"
            >
              <div className="min-w-0 flex-1">
                <div className="mb-1 flex items-center gap-2">
                  <span className="text-[0.85rem] font-bold text-white font-mono">{c.configKey}</span>
                  <span
                    className={`rounded px-1.5 py-px text-[0.62rem] font-bold ${c.active ? "bg-success-soft text-success" : "bg-danger-soft text-danger"}`}
                  >
                    {c.active ? "ACTIVE" : "INACTIVE"}
                  </span>
                  <span className="rounded bg-accent-soft px-1.5 py-px text-[0.62rem] font-semibold text-accent">
                    {c.valueType}
                  </span>
                </div>
                {c.description && <p className="mx-0 mb-1 mt-0.5 text-xs text-muted">{c.description}</p>}
                <p className="m-0 break-all font-mono text-[0.78rem] text-white/70">
                  {c.configValue.length > 120 ? c.configValue.slice(0, 120) + "..." : c.configValue}
                </p>
              </div>
              <div className="flex shrink-0 gap-1.5">
                <button type="button" onClick={() => onOpenEdit(c)} className="rounded-sm border border-accent-glow bg-accent-soft px-2.5 py-1 text-[0.72rem] font-semibold text-accent cursor-pointer">
                  Edit
                </button>
                <button
                  type="button"
                  disabled={deletingConfigId === c.id}
                  onClick={() => onDelete(c.id)}
                  className="rounded-sm border border-danger-glow bg-danger-soft px-2.5 py-1 text-[0.72rem] font-semibold text-danger cursor-pointer"
                  style={{ opacity: deletingConfigId === c.id ? 0.6 : 1 }}
                >
                  {deletingConfigId === c.id ? "..." : "Delete"}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
