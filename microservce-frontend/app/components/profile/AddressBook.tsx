"use client";

import type { CustomerAddress, AddressForm } from "../../../lib/types/customer";

type DefaultAction = { addressId: string; type: "shipping" | "billing" };

type AddressBookProps = {
  addresses: CustomerAddress[];
  addressForm: AddressForm;
  onAddressFormChange: (updater: (old: AddressForm) => AddressForm) => void;
  savingAddress: boolean;
  onSave: () => void;
  onReset: () => void;
  onStartEdit: (address: CustomerAddress) => void;
  onDelete: (addressId: string) => void;
  onSetDefault: (addressId: string, type: "shipping" | "billing") => void;
  addressLoading: boolean;
  deletingAddressId: string | null;
  settingDefaultAddress: DefaultAction | null;
  emailVerified: boolean | null;
};

export default function AddressBook({
  addresses,
  addressForm,
  onAddressFormChange,
  savingAddress,
  onSave,
  onReset,
  onStartEdit,
  onDelete,
  onSetDefault,
  addressLoading,
  deletingAddressId,
  settingDefaultAddress,
  emailVerified,
}: AddressBookProps) {
  const formDisabled = savingAddress || emailVerified === false;

  return (
    <section className="glass-card p-6">
      <div className="flex flex-wrap items-end justify-between gap-3 mb-5">
        <div>
          <h2 className="font-[Syne,sans-serif] font-extrabold text-[1.1rem] text-white mb-1 mt-0">Address Book</h2>
          <p className="text-[0.75rem] text-muted m-0">Manage shipping and billing addresses.</p>
        </div>
        <span className="bg-[image:var(--gradient-brand)] text-white px-3 py-[3px] rounded-full text-[0.72rem] font-extrabold">
          {addresses.length} active
        </span>
      </div>

      <div className="grid gap-5 grid-cols-[0.9fr_1.1fr]">
        {/* Address Form */}
        <div className="rounded-[12px] border border-line-bright p-4">
          <p className="text-[0.65rem] font-bold uppercase tracking-[0.1em] text-brand mb-3">
            {addressForm.id ? "Edit Address" : "Add Address"}
          </p>
          <div className="flex flex-col gap-2">
            {[
              { key: "label", placeholder: "Label (Home, Office)", required: false },
              { key: "recipientName", placeholder: "Recipient name *", required: true },
              { key: "phone", placeholder: "Phone number *", required: true },
              { key: "line1", placeholder: "Address line 1 *", required: true },
              { key: "line2", placeholder: "Address line 2 (optional)", required: false },
            ].map(({ key, placeholder }) => (
              <input
                key={key}
                value={(addressForm as unknown as Record<string, string>)[key]}
                onChange={(e) => onAddressFormChange((old) => ({ ...old, [key]: e.target.value }))}
                placeholder={placeholder}
                disabled={formDisabled}
                className="form-input"
              />
            ))}
            <div className="grid grid-cols-2 gap-2">
              {[
                { key: "city", placeholder: "City *" },
                { key: "state", placeholder: "State *" },
              ].map(({ key, placeholder }) => (
                <input
                  key={key}
                  value={(addressForm as unknown as Record<string, string>)[key]}
                  onChange={(e) => onAddressFormChange((old) => ({ ...old, [key]: e.target.value }))}
                  placeholder={placeholder}
                  disabled={formDisabled}
                  className="form-input"
                />
              ))}
            </div>
            <div className="grid grid-cols-2 gap-2">
              <input
                value={addressForm.postalCode}
                onChange={(e) => onAddressFormChange((old) => ({ ...old, postalCode: e.target.value }))}
                placeholder="Postal code *"
                disabled={formDisabled}
                className="form-input"
              />
              <input
                value={addressForm.countryCode}
                onChange={(e) => onAddressFormChange((old) => ({ ...old, countryCode: e.target.value.toUpperCase() }))}
                placeholder="Country (US)"
                maxLength={2}
                disabled={formDisabled}
                className="form-input"
              />
            </div>
            <div className="flex flex-wrap gap-3 py-1">
              {[
                { key: "defaultShipping", label: "Default Shipping" },
                { key: "defaultBilling", label: "Default Billing" },
              ].map(({ key, label }) => (
                <label key={key} className="inline-flex items-center gap-[6px] text-[0.78rem] text-muted cursor-pointer">
                  <input
                    type="checkbox"
                    checked={(addressForm as unknown as Record<string, boolean>)[key]}
                    onChange={(e) => onAddressFormChange((old) => ({ ...old, [key]: e.target.checked }))}
                    disabled={formDisabled}
                    className="accent-brand w-[14px] h-[14px]"
                  />
                  {label}
                </label>
              ))}
            </div>
            <div className="flex gap-2 mt-1">
              <button
                onClick={onSave}
                disabled={formDisabled}
                className={`flex-1 p-[10px] rounded-md border-none text-white text-[0.82rem] font-bold inline-flex items-center justify-center gap-[6px] ${formDisabled ? "bg-line-bright cursor-not-allowed" : "bg-[image:var(--gradient-brand)] cursor-pointer"}`}
              >
                {savingAddress && <span className="spinner-sm" />}
                {savingAddress ? "Saving..." : addressForm.id ? "Update Address" : "Add Address"}
              </button>
              {addressForm.id && (
                <button
                  onClick={onReset}
                  disabled={savingAddress}
                  className="px-[14px] py-[10px] rounded-md border border-line-bright bg-transparent text-muted text-[0.82rem] cursor-pointer"
                >
                  Cancel
                </button>
              )}
            </div>
          </div>
        </div>

        {/* Saved Addresses */}
        <div className="rounded-[12px] border border-brand-soft p-4">
          <p className="text-[0.65rem] font-bold uppercase tracking-[0.1em] text-accent mb-3">Saved Addresses</p>
          {addressLoading && <p className="text-[0.82rem] text-muted">Loading addresses...</p>}
          {!addressLoading && addresses.length === 0 && (
            <p className="text-[0.82rem] text-muted">No addresses added yet.</p>
          )}
          <div className="flex flex-col gap-[10px]">
            {addresses.map((address) => {
              const isSettingShipping = settingDefaultAddress?.addressId === address.id && settingDefaultAddress.type === "shipping";
              const isSettingBilling = settingDefaultAddress?.addressId === address.id && settingDefaultAddress.type === "billing";
              const isSingle = addresses.length === 1;
              return (
                <article
                  key={address.id}
                  className="rounded-[12px] border border-brand-soft bg-brand-soft px-[14px] py-3"
                >
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="flex-1 min-w-[140px]">
                      <p className="font-bold text-white text-[0.85rem] mb-[2px]">
                        {address.label ? `${address.label} \u00b7 ` : ""}{address.recipientName}
                      </p>
                      <p className="text-[0.72rem] text-muted mb-1">{address.phone}</p>
                      <p className="text-[0.7rem] text-[rgba(200,200,232,0.7)] mb-[6px] leading-[1.5]">
                        {address.line1}{address.line2 ? `, ${address.line2}` : ""}, {address.city}, {address.state} {address.postalCode}, {address.countryCode}
                      </p>
                      <div className="flex flex-wrap gap-1">
                        {address.defaultShipping && (
                          <span className="text-[0.6rem] font-bold px-2 py-[2px] rounded-full bg-[rgba(34,197,94,0.1)] border border-[rgba(34,197,94,0.2)] text-[#4ade80]">
                            Default Shipping
                          </span>
                        )}
                        {address.defaultBilling && (
                          <span className="text-[0.6rem] font-bold px-2 py-[2px] rounded-full bg-line-bright border border-line-bright text-brand">
                            Default Billing
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-1">
                      {/* Edit */}
                      <button
                        onClick={() => onStartEdit(address)}
                        disabled={formDisabled}
                        className="px-[10px] py-1 rounded-[7px] border border-line-bright bg-brand-soft text-brand text-[0.62rem] font-bold cursor-pointer"
                      >
                        Edit
                      </button>
                      {/* Set Shipping */}
                      <button
                        onClick={() => { onSetDefault(address.id, "shipping"); }}
                        disabled={isSingle || address.defaultShipping || settingDefaultAddress !== null || formDisabled}
                        className={`px-[10px] py-1 rounded-[7px] border border-[rgba(34,197,94,0.2)] bg-[rgba(34,197,94,0.06)] text-[#4ade80] text-[0.62rem] font-bold cursor-pointer ${isSingle || address.defaultShipping ? "opacity-40" : ""}`}
                      >
                        {isSettingShipping ? "Saving..." : address.defaultShipping ? "\u2713 Shipping" : "Set Shipping"}
                      </button>
                      {/* Set Billing */}
                      <button
                        onClick={() => { onSetDefault(address.id, "billing"); }}
                        disabled={isSingle || address.defaultBilling || settingDefaultAddress !== null || formDisabled}
                        className={`px-[10px] py-1 rounded-[7px] border border-line-bright bg-brand-soft text-brand text-[0.62rem] font-bold cursor-pointer ${isSingle || address.defaultBilling ? "opacity-40" : ""}`}
                      >
                        {isSettingBilling ? "Saving..." : address.defaultBilling ? "\u2713 Billing" : "Set Billing"}
                      </button>
                      {/* Delete */}
                      <button
                        onClick={() => { onDelete(address.id); }}
                        disabled={deletingAddressId !== null || formDisabled}
                        className={`px-[10px] py-1 rounded-[7px] border border-[rgba(239,68,68,0.2)] bg-[rgba(239,68,68,0.05)] text-danger text-[0.62rem] font-bold ${deletingAddressId ? "cursor-not-allowed" : "cursor-pointer"}`}
                      >
                        {deletingAddressId === address.id ? "Deleting..." : "Delete"}
                      </button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
}
