"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useAuthSession } from "../../../lib/authSession";

type VendorVerificationState = {
  verificationStatus: string | null;
  loading: boolean;
};

const VendorVerificationCtx = createContext<VendorVerificationState>({
  verificationStatus: null,
  loading: true,
});

export function useVendorVerification() {
  return useContext(VendorVerificationCtx);
}

export function VendorVerificationProvider({ children }: { children: ReactNode }) {
  const { status, isVendorAdmin, isVendorStaff, apiClient } = useAuthSession();
  const isVendor = isVendorAdmin || isVendorStaff;

  const [verificationStatus, setVerificationStatus] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (status !== "ready" || !isVendor || !apiClient) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await apiClient.get("/vendors/me");
        if (!cancelled) {
          setVerificationStatus(
            (res.data as { verificationStatus?: string }).verificationStatus ?? null,
          );
        }
      } catch {
        /* non-critical */
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [status, isVendor, apiClient]);

  return (
    <VendorVerificationCtx.Provider value={{ verificationStatus, loading }}>
      {children}
    </VendorVerificationCtx.Provider>
  );
}
