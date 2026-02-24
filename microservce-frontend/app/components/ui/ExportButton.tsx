"use client";
import { useState } from "react";
import type { AxiosInstance } from "axios";

type Props = {
  apiClient: AxiosInstance | null;
  endpoint: string;
  filename: string;
  label?: string;
  params?: Record<string, string>;
};

export default function ExportButton({ apiClient, endpoint, filename, label = "Export CSV", params }: Props) {
  const [loading, setLoading] = useState(false);

  const handleExport = async () => {
    if (!apiClient || loading) return;
    setLoading(true);
    try {
      const res = await apiClient.get(endpoint, { params, responseType: "blob" });
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch {
      // error handled by apiClient interceptor
    } finally {
      setLoading(false);
    }
  };

  return (
    <button type="button" onClick={handleExport} disabled={loading || !apiClient} className="btn-outline" style={{ fontSize: "0.78rem", padding: "7px 14px", display: "inline-flex", alignItems: "center", gap: 6 }}>
      {loading ? (
        <span style={{ display: "inline-block", width: 14, height: 14, border: "2px solid var(--brand)", borderTopColor: "transparent", borderRadius: "50%", animation: "spin 0.6s linear infinite" }} />
      ) : (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
      )}
      {label}
    </button>
  );
}
