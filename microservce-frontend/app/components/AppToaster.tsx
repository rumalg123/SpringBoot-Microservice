"use client";

import { Toaster } from "react-hot-toast";

export default function AppToaster() {
  return (
    <Toaster
      position="top-center"
      reverseOrder={false}
      gutter={8}
      toastOptions={{
        duration: 3000,
        style: {
          border: "1px solid var(--line)",
          background: "#fff",
          color: "var(--ink)",
          boxShadow: "0 8px 32px rgba(0, 0, 0, 0.12)",
          borderRadius: "12px",
          padding: "12px 16px",
          fontSize: "0.88rem",
          fontWeight: 500,
          maxWidth: "420px",
        },
        success: {
          iconTheme: {
            primary: "var(--success)",
            secondary: "#fff",
          },
        },
        error: {
          iconTheme: {
            primary: "var(--brand)",
            secondary: "#fff",
          },
          duration: 4000,
        },
      }}
    />
  );
}
