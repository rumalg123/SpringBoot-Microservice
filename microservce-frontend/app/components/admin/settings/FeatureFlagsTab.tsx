"use client";

import TableSkeleton from "../../ui/TableSkeleton";
import type { FeatureFlag } from "./types";

type FlagForm = {
  flagKey: string;
  description: string;
  enabled: boolean;
  enabledForRoles: string;
  rolloutPercentage: number;
};

type Props = {
  flags: FeatureFlag[];
  flagLoading: boolean;
  editingFlag: FeatureFlag | null;
  showCreateFlag: boolean;
  flagForm: FlagForm;
  onFlagFormChange: (form: FlagForm) => void;
  savingFlag: boolean;
  deletingFlagId: string | null;
  onOpenCreate: () => void;
  onOpenEdit: (f: FeatureFlag) => void;
  onSave: () => void;
  onDelete: (id: string) => void;
  onToggle: (flag: FeatureFlag) => void;
  onCancelEdit: () => void;
  onToggleCreate: () => void;
};

export default function FeatureFlagsTab({
  flags,
  flagLoading,
  editingFlag,
  showCreateFlag,
  flagForm,
  onFlagFormChange,
  savingFlag,
  deletingFlagId,
  onOpenCreate,
  onOpenEdit,
  onSave,
  onDelete,
  onToggle,
  onCancelEdit,
  onToggleCreate,
}: Props) {
  return (
    <>
      <div className="mb-4 flex justify-end">
        <button
          type="button"
          onClick={showCreateFlag ? onToggleCreate : onOpenCreate}
          className="cursor-pointer rounded-md border-none bg-[var(--gradient-brand)] px-4.5 py-2 text-sm font-bold text-white"
        >
          {showCreateFlag ? "Cancel" : "+ Add Flag"}
        </button>
      </div>

      {/* Create/Edit Form */}
      {(showCreateFlag || editingFlag) && (
        <div className="mb-6 rounded-[14px] border border-line-bright bg-[var(--card)] p-5">
          <h3 className="mb-4 text-[0.95rem] font-bold text-white">
            {editingFlag ? `Edit: ${editingFlag.flagKey}` : "New Feature Flag"}
          </h3>
          <div className="mb-3 grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-bold text-muted">Flag Key *</label>
              <input
                value={flagForm.flagKey}
                onChange={(e) => onFlagFormChange({ ...flagForm, flagKey: e.target.value })}
                placeholder="feature.dark-mode"
                maxLength={200}
                disabled={!!editingFlag}
                className={`w-full rounded-md border border-line-bright bg-bg px-3 py-2 text-sm text-white ${editingFlag ? "opacity-60" : "opacity-100"}`}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-bold text-muted">Rollout %</label>
              <input
                type="number"
                min={0}
                max={100}
                value={flagForm.rolloutPercentage}
                onChange={(e) => onFlagFormChange({ ...flagForm, rolloutPercentage: Number(e.target.value) })}
                className="w-full rounded-md border border-line-bright bg-bg px-3 py-2 text-sm text-white"
              />
            </div>
          </div>
          <div className="mb-3">
            <label className="mb-1 block text-xs font-bold text-muted">Description</label>
            <input
              value={flagForm.description}
              onChange={(e) => onFlagFormChange({ ...flagForm, description: e.target.value })}
              placeholder="What this flag controls..."
              maxLength={500}
              className="w-full rounded-md border border-line-bright bg-bg px-3 py-2 text-sm text-white"
            />
          </div>
          <div className="mb-3">
            <label className="mb-1 block text-xs font-bold text-muted">Enabled For Roles (comma-separated)</label>
            <input
              value={flagForm.enabledForRoles}
              onChange={(e) => onFlagFormChange({ ...flagForm, enabledForRoles: e.target.value })}
              placeholder="super_admin, vendor_admin"
              maxLength={500}
              className="w-full rounded-md border border-line-bright bg-bg px-3 py-2 text-sm text-white"
            />
          </div>
          <div className="mb-4 flex items-center gap-4">
            <label className="flex cursor-pointer items-center gap-1.5 text-sm text-white">
              <input
                type="checkbox"
                checked={flagForm.enabled}
                onChange={(e) => onFlagFormChange({ ...flagForm, enabled: e.target.checked })}
              />
              Enabled
            </label>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={savingFlag || !flagForm.flagKey.trim()}
              onClick={onSave}
              className={`rounded-md border-none bg-[var(--gradient-brand)] px-6 py-2 text-sm font-bold text-white ${
                savingFlag ? "cursor-not-allowed opacity-60" : "cursor-pointer opacity-100"
              }`}
            >
              {savingFlag ? "Saving..." : editingFlag ? "Update" : "Create"}
            </button>
            {editingFlag && (
              <button type="button" onClick={onCancelEdit} className="cursor-pointer rounded-md border border-line-bright bg-transparent px-4 py-2 text-sm font-semibold text-muted">
                Cancel
              </button>
            )}
          </div>
        </div>
      )}

      {/* Flags List */}
      {flagLoading ? (
        <TableSkeleton rows={4} cols={3} />
      ) : flags.length === 0 ? (
        <div className="rounded-[14px] border border-line-bright bg-[var(--card)] px-5 py-[60px] text-center">
          <p className="text-[0.9rem] text-muted">No feature flags found.</p>
        </div>
      ) : (
        <div className="grid gap-2.5">
          {flags.map((f) => (
            <div
              key={f.id}
              className="flex items-center justify-between gap-3 rounded-xl border border-line-bright bg-[var(--card)] p-4"
            >
              <div className="min-w-0 flex-1">
                <div className="mb-1 flex items-center gap-2">
                  <span className="font-mono text-base font-bold text-white">{f.flagKey}</span>
                  <span className={`rounded px-1.5 py-px text-[0.62rem] font-bold ${
                    f.enabled ? "bg-success-soft text-success" : "bg-danger-soft text-danger"
                  }`}>
                    {f.enabled ? "ON" : "OFF"}
                  </span>
                  {f.rolloutPercentage !== null && f.rolloutPercentage < 100 && (
                    <span className="rounded px-1.5 py-px text-[0.62rem] font-semibold bg-warning-soft text-warning-text">
                      {f.rolloutPercentage}%
                    </span>
                  )}
                </div>
                {f.description && <p className="my-0.5 text-xs text-muted">{f.description}</p>}
                {f.enabledForRoles && (
                  <p className="my-0.5 text-[0.7rem] text-white/50">
                    Roles: {f.enabledForRoles}
                  </p>
                )}
              </div>
              <div className="flex shrink-0 gap-1.5">
                <button
                  type="button"
                  onClick={() => onToggle(f)}
                  className={`cursor-pointer rounded-sm border px-2.5 py-1 text-xs font-semibold ${
                    f.enabled
                      ? "border-danger-glow bg-danger-soft text-danger"
                      : "border-success-glow bg-success-soft text-success"
                  }`}
                >
                  {f.enabled ? "Disable" : "Enable"}
                </button>
                <button type="button" onClick={() => onOpenEdit(f)} className="cursor-pointer rounded-sm border border-accent-glow bg-accent-soft px-2.5 py-1 text-xs font-semibold text-accent">
                  Edit
                </button>
                <button
                  type="button"
                  disabled={deletingFlagId === f.id}
                  onClick={() => onDelete(f.id)}
                  className={`cursor-pointer rounded-sm border border-danger-glow bg-danger-soft px-2.5 py-1 text-xs font-semibold text-danger ${deletingFlagId === f.id ? "opacity-60" : "opacity-100"}`}
                >
                  {deletingFlagId === f.id ? "..." : "Delete"}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
