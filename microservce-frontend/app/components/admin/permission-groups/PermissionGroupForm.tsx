"use client";

type FormState = {
  name: string;
  description: string;
  scope: "PLATFORM" | "VENDOR";
  permissionsText: string;
};

type PermissionGroupFormProps = {
  form: FormState;
  editing: boolean;
  saving: boolean;
  onFormChange: (updater: (prev: FormState) => FormState) => void;
  onSave: () => void;
  onCancel: () => void;
};

export default function PermissionGroupForm({
  form,
  editing,
  saving,
  onFormChange,
  onSave,
  onCancel,
}: PermissionGroupFormProps) {
  return (
    <section className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6 mb-6">
      <h2 className="text-lg font-bold text-ink mb-1">
        {editing ? "Edit Permission Group" : "Create Permission Group"}
      </h2>
      <p className="text-[0.75rem] text-muted mb-5">
        {editing
          ? "Update the permission group details below."
          : "Define a new permission group with a name, scope, and list of permissions."}
      </p>

      <div className="grid gap-4 max-w-[560px]">
        {/* Name */}
        <label className="block">
          <span className="block text-[0.72rem] font-bold uppercase tracking-[0.06em] text-muted mb-1.5">
            Name
          </span>
          <input
            type="text"
            value={form.name}
            onChange={(e) => onFormChange((s) => ({ ...s, name: e.target.value }))}
            placeholder="e.g. Order Managers"
            disabled={saving}
            className="w-full py-2.5 px-3.5 rounded-[12px] border border-line bg-[rgba(255,255,255,0.03)] text-ink text-base outline-none"
          />
        </label>

        {/* Description */}
        <label className="block">
          <span className="block text-[0.72rem] font-bold uppercase tracking-[0.06em] text-muted mb-1.5">
            Description
          </span>
          <textarea
            value={form.description}
            onChange={(e) => onFormChange((s) => ({ ...s, description: e.target.value }))}
            placeholder="Optional description of this group"
            rows={2}
            disabled={saving}
            className="w-full py-2.5 px-3.5 rounded-[12px] border border-line bg-[rgba(255,255,255,0.03)] text-ink text-base resize-y outline-none"
          />
        </label>

        {/* Scope radio */}
        <fieldset className="border-none m-0 p-0">
          <legend className="text-[0.72rem] font-bold uppercase tracking-[0.06em] text-muted mb-2">
            Scope
          </legend>
          <div className="flex gap-4">
            {(["PLATFORM", "VENDOR"] as const).map((s) => (
              <label
                key={s}
                className="flex items-center gap-1.5 cursor-pointer text-[0.82rem] text-ink"
              >
                <input
                  type="radio"
                  name="scope"
                  value={s}
                  checked={form.scope === s}
                  onChange={() => onFormChange((f) => ({ ...f, scope: s }))}
                  disabled={saving}
                  style={{ accentColor: "var(--brand)" }}
                />
                {s}
              </label>
            ))}
          </div>
        </fieldset>

        {/* Permissions textarea */}
        <label className="block">
          <span className="block text-[0.72rem] font-bold uppercase tracking-[0.06em] text-muted mb-1.5">
            Permissions (one per line)
          </span>
          <textarea
            value={form.permissionsText}
            onChange={(e) => onFormChange((s) => ({ ...s, permissionsText: e.target.value }))}
            placeholder={"platform.orders.manage\nplatform.products.read\nplatform.categories.manage"}
            rows={6}
            disabled={saving}
            className="w-full py-2.5 px-3.5 rounded-[12px] border border-line bg-[rgba(255,255,255,0.03)] text-ink text-[0.82rem] font-mono resize-y outline-none leading-[1.7]"
          />
        </label>
      </div>

      {/* Save / Cancel */}
      <div className="flex gap-2.5 mt-5">
        <button
          type="button"
          onClick={() => { void onSave(); }}
          disabled={saving}
          className="btn-brand py-[9px] px-[22px] rounded-[12px] text-[0.82rem] font-bold"
          style={{
            cursor: saving ? "not-allowed" : "pointer",
            opacity: saving ? 0.6 : 1,
          }}
        >
          {saving ? "Saving..." : editing ? "Update Group" : "Create Group"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={saving}
          className="btn-ghost py-[9px] px-[18px] rounded-[12px] text-[0.82rem] font-semibold cursor-pointer"
        >
          Cancel
        </button>
      </div>
    </section>
  );
}
