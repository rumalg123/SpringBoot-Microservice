"use client";

import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useAuthSession } from "../../lib/authSession";
import ConnectedAppNav from "../components/ConnectedAppNav";
import Footer from "../components/Footer";
import { VendorVerificationProvider } from "../components/vendor/VendorVerificationContext";

export default function VendorLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { status, isAuthenticated, isVendorAdmin, isVendorStaff } =
    useAuthSession();

  const isVendor = isVendorAdmin || isVendorStaff;

  useEffect(() => {
    if (status !== "ready") return;
    if (!isAuthenticated || !isVendor) {
      router.replace("/");
    }
  }, [status, isAuthenticated, isVendor, router]);

  /* Still initializing auth */
  if (status !== "ready") {
    return (
      <div className="grid min-h-screen place-items-center bg-bg">
        <div className="spinner-lg" />
      </div>
    );
  }

  /* Unauthorized — render nothing while redirect fires */
  if (!isAuthenticated || !isVendor) {
    return null;
  }

  return (
    <>
      <ConnectedAppNav />
      <VendorVerificationProvider>
        {children}
      </VendorVerificationProvider>
      <Footer />
    </>
  );
}
