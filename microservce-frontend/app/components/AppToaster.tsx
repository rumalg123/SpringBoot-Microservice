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
          background: "rgba(17, 17, 40, 0.95)",
          color: "var(--ink)",
          boxShadow: "0 16px 38px rgba(2, 6, 23, 0.45)",
          borderRadius: "12px",
          padding: "12px 16px",
          fontSize: "0.88rem",
          fontWeight: 500,
          maxWidth: "420px",
          backdropFilter: "blur(10px)",
        },
        success: {
          style: {
            borderColor: "rgba(16, 185, 129, 0.28)",
            background: "rgba(6, 32, 28, 0.94)",
          },
          iconTheme: {
            primary: "var(--success)",
            secondary: "rgba(6, 32, 28, 1)",
          },
        },
        error: {
          style: {
            borderColor: "rgba(244, 63, 94, 0.28)",
            background: "rgba(43, 16, 26, 0.95)",
          },
          iconTheme: {
            primary: "#fb7185",
            secondary: "rgba(43, 16, 26, 1)",
          },
          duration: 4000,
        },
      }}
    />
  );
}
