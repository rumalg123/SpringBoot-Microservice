"use client";

import type { CommunicationPreferences, LinkedAccounts } from "../../../lib/types/customer";

type CommunicationPrefsTabProps = {
  commPrefs: CommunicationPreferences | null;
  commPrefsLoading: boolean;
  commPrefsSaving: string | null;
  onToggle: (key: keyof Pick<CommunicationPreferences, "emailMarketing" | "smsMarketing" | "pushNotifications" | "orderUpdates" | "promotionalAlerts">) => void;
  linkedAccounts: LinkedAccounts | null;
  linkedAccountsLoading: boolean;
};

export default function CommunicationPrefsTab({
  commPrefs,
  commPrefsLoading,
  commPrefsSaving,
  onToggle,
  linkedAccounts,
  linkedAccountsLoading,
}: CommunicationPrefsTabProps) {
  return (
    <>
      {/* Communication Preferences */}
      <article className="glass-card p-6 mb-5">
        <div className="flex items-center gap-3 mb-5">
          <div className="w-[48px] h-[48px] rounded-full shrink-0 bg-[image:var(--gradient-brand)] grid place-items-center">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
              <polyline points="22,6 12,13 2,6" />
            </svg>
          </div>
          <div>
            <h2 className="font-[Syne,sans-serif] font-extrabold text-[1.1rem] text-white mb-1 mt-0">Communication Preferences</h2>
            <p className="text-[0.75rem] text-muted m-0">Control how we reach out to you.</p>
          </div>
        </div>

        {commPrefsLoading && !commPrefs && (
          <div className="text-center py-6">
            <div className="spinner-lg" />
            <p className="mt-3 text-muted text-[0.82rem]">Loading preferences...</p>
          </div>
        )}

        {commPrefs && (
          <div className="flex flex-col gap-3">
            {([
              { key: "emailMarketing" as const, label: "Email Marketing", desc: "Receive promotional emails about new products and offers" },
              { key: "smsMarketing" as const, label: "SMS Marketing", desc: "Get text messages with deals and updates" },
              { key: "pushNotifications" as const, label: "Push Notifications", desc: "Browser and mobile push notifications" },
              { key: "orderUpdates" as const, label: "Order Updates", desc: "Notifications about your order status changes" },
              { key: "promotionalAlerts" as const, label: "Promotional Alerts", desc: "Alerts for flash sales and limited-time promotions" },
            ]).map(({ key, label, desc }) => (
              <div
                key={key}
                className="flex items-center justify-between gap-4 rounded-[12px] bg-brand-soft border border-brand-soft px-4 py-[14px]"
              >
                <div className="flex-1">
                  <p className="text-[0.85rem] font-bold text-white mb-[2px]">{label}</p>
                  <p className="text-[0.72rem] text-muted m-0">{desc}</p>
                </div>
                <button
                  onClick={() => { onToggle(key); }}
                  disabled={commPrefsSaving !== null}
                  className={`relative w-[48px] h-[26px] rounded-[13px] border-none shrink-0 transition-colors duration-200 ${commPrefsSaving !== null ? "cursor-not-allowed" : "cursor-pointer"}`}
                  style={{
                    background: commPrefs[key] ? "var(--brand)" : "var(--line-bright)",
                    opacity: commPrefsSaving === key ? 0.6 : 1,
                  }}
                >
                  <span
                    className="absolute top-[3px] w-5 h-5 rounded-full bg-white transition-[left] duration-200 shadow-[0_1px_3px_rgba(0,0,0,0.3)]"
                    style={{ left: commPrefs[key] ? "25px" : "3px" }}
                  />
                </button>
              </div>
            ))}
          </div>
        )}
      </article>

      {/* Linked Accounts */}
      <article className="glass-card p-6">
        <div className="flex items-center gap-3 mb-5">
          <div className="w-[48px] h-[48px] rounded-full shrink-0 bg-[rgba(124,58,237,0.2)] border border-[rgba(124,58,237,0.35)] grid place-items-center">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#a78bfa" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
              <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
            </svg>
          </div>
          <div>
            <h2 className="font-[Syne,sans-serif] font-extrabold text-[1.1rem] text-white mb-1 mt-0">Linked Accounts</h2>
            <p className="text-[0.75rem] text-muted m-0">External accounts connected to your profile.</p>
          </div>
        </div>

        {linkedAccountsLoading && !linkedAccounts && (
          <div className="text-center py-6">
            <div className="spinner-lg" />
            <p className="mt-3 text-muted text-[0.82rem]">Loading linked accounts...</p>
          </div>
        )}

        {linkedAccounts && (
          <>
            {linkedAccounts.providers.length === 0 && (
              <p className="text-[0.82rem] text-muted">No linked accounts found.</p>
            )}
            <div className="flex flex-col gap-[10px]">
              {linkedAccounts.providers.map((provider) => (
                <div
                  key={provider}
                  className="flex items-center gap-3 rounded-[12px] bg-brand-soft border border-brand-soft px-4 py-3"
                >
                  <div className="w-9 h-9 rounded-full shrink-0 bg-[rgba(34,197,94,0.1)] border border-[rgba(34,197,94,0.25)] grid place-items-center">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#4ade80" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="20 6 9 17 4 12" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-[0.85rem] font-bold text-white m-0 capitalize">{provider}</p>
                    <p className="text-[0.7rem] text-muted m-0">Connected</p>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </article>
    </>
  );
}
