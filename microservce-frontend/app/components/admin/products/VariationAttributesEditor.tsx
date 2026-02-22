"use client";

import type { VariationAttributesEditorProps } from "./types";

export default function VariationAttributesEditor({
  parentAttributeNames,
  newParentAttributeName,
  setNewParentAttributeName,
  addParentAttribute,
  removeParentAttribute,
  productMutationBusy,
}: VariationAttributesEditorProps) {
  return (
    <div className="rounded-lg border border-[var(--line)] p-3">
      <p className="text-xs font-semibold text-[var(--ink)]">Variation Attributes</p>
      <p className="mt-1 text-[11px] text-[var(--muted)]">
        Add attribute names for this parent product (example: color, size, material).
      </p>
      <div className="mt-2 flex gap-2">
        <input
          value={newParentAttributeName}
          onChange={(e) => setNewParentAttributeName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              addParentAttribute();
            }
          }}
          placeholder="Attribute name"
          className="flex-1 rounded-lg border border-[var(--line)] px-3 py-2"
          disabled={productMutationBusy}
        />
        <button
          type="button"
          onClick={addParentAttribute}
          disabled={productMutationBusy}
          className="rounded-lg border border-[var(--line)] px-3 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-60"
          style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
        >
          Add
        </button>
      </div>
      <div className="mt-2 flex flex-wrap gap-2">
        {parentAttributeNames.length === 0 && (
          <p className="text-xs text-[var(--muted)]">No attributes added yet.</p>
        )}
        {parentAttributeNames.map((name) => (
          <span
            key={name}
            className="inline-flex items-center gap-2 rounded-full border border-[var(--line)] bg-[var(--bg)] px-3 py-1 text-xs text-[var(--ink)]"
          >
            {name}
            <button
              type="button"
              onClick={() => removeParentAttribute(name)}
              disabled={productMutationBusy}
              className="rounded-full border border-[var(--line)] px-1.5 text-[10px] leading-4 disabled:cursor-not-allowed disabled:opacity-60"
              style={{ background: "var(--surface-2)", color: "var(--muted)" }}
            >
              x
            </button>
          </span>
        ))}
      </div>
    </div>
  );
}
