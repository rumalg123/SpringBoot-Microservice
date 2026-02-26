"use client";

import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useAuthSession } from "../../lib/authSession";
import ConnectedAppNav from "../components/ConnectedAppNav";
import Footer from "../components/Footer";

export default function AdminLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { status, isAuthenticated, canViewAdmin } = useAuthSession();

  useEffect(() => {
    if (status !== "ready") return;
    if (!isAuthenticated || !canViewAdmin) {
      router.replace("/");
    }
  }, [status, isAuthenticated, canViewAdmin, router]);

  /* Still initializing auth */
  if (status !== "ready") {
    return (
      <div className="grid min-h-screen place-items-center bg-bg">
        <div className="spinner-lg" />
      </div>
    );
  }

  /* Unauthorized — render nothing while redirect fires */
  if (!isAuthenticated || !canViewAdmin) {
    return null;
  }

  return (
    <>
      <ConnectedAppNav />
      {children}
      <Footer />
    </>
  );
}
