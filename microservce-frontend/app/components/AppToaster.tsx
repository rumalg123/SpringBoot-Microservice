"use client";

import { Toaster } from "react-hot-toast";

export default function AppToaster() {
  return (
    <Toaster
      position="top-right"
      toastOptions={{
        duration: 2600,
        style: {
          border: "1px solid var(--line)",
          background: "var(--surface)",
          color: "var(--ink)",
          boxShadow: "0 10px 24px rgba(30,22,14,0.12)",
        },
      }}
    />
  );
}

