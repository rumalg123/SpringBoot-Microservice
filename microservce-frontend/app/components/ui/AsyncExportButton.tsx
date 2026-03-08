"use client";

import { useState } from "react";
import type { AxiosInstance } from "axios";
import toast from "react-hot-toast";

type ExportJobResponse = {
  jobId: string;
  status: string;
  fileName?: string | null;
  failureMessage?: string | null;
};

type Props = {
  apiClient: AxiosInstance | null;
  createEndpoint: string;
  label?: string;
  payload?: Record<string, unknown>;
  fallbackFilename: string;
  pollIntervalMs?: number;
  pollTimeoutMs?: number;
};

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

export default function AsyncExportButton({
  apiClient,
  createEndpoint,
  label = "Export CSV",
  payload,
  fallbackFilename,
  pollIntervalMs = 2000,
  pollTimeoutMs = 180000,
}: Props) {
  const [loading, setLoading] = useState(false);

  const downloadBlob = (blob: Blob, fileName: string) => {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
  };

  const waitForJob = async (jobId: string) => {
    const startedAt = Date.now();
    while (Date.now() - startedAt < pollTimeoutMs) {
      const statusRes = await apiClient!.get<ExportJobResponse>(`${createEndpoint}/${jobId}`);
      const job = statusRes.data;
      if (job.status === "COMPLETED") {
        return job;
      }
      if (job.status === "FAILED" || job.status === "EXPIRED") {
        throw new Error(job.failureMessage || `Export ${job.status.toLowerCase()}`);
      }
      await sleep(pollIntervalMs);
    }
    throw new Error("Export is taking too long. Please check again later.");
  };

  const handleExport = async () => {
    if (!apiClient || loading) return;
    setLoading(true);
    try {
      const createRes = await apiClient.post<ExportJobResponse>(createEndpoint, payload ?? {});
      const jobId = createRes.data.jobId;
      if (!jobId) {
        throw new Error("Export job was not created");
      }
      toast.success("Export queued. Preparing file...");
      const completedJob = await waitForJob(jobId);
      const downloadRes = await apiClient.get(`${createEndpoint}/${jobId}/download`, { responseType: "blob" });
      downloadBlob(new Blob([downloadRes.data]), completedJob.fileName || fallbackFilename);
      toast.success("Export ready");
    } catch (error) {
      const message = error instanceof Error ? error.message : "Export failed";
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <button
      type="button"
      onClick={handleExport}
      disabled={loading || !apiClient}
      className="btn-outline text-sm py-[7px] px-3.5 inline-flex items-center gap-1.5"
    >
      {loading ? (
        <span className="inline-block w-3.5 h-3.5 border-2 border-brand border-t-transparent rounded-full animate-spin" />
      ) : (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
      )}
      {loading ? "Preparing..." : label}
    </button>
  );
}
