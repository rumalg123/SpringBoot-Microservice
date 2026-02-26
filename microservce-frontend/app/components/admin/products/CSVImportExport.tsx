"use client";

import { ChangeEvent, useRef, useState } from "react";
import type { AxiosInstance } from "axios";
import toast from "react-hot-toast";
import ExportButton from "../../ui/ExportButton";
import type { ImportResult } from "./types";

type CSVImportExportProps = {
  apiClient: AxiosInstance | null;
  onImportComplete: () => void;
};

export default function CSVImportExport({ apiClient, onImportComplete }: CSVImportExportProps) {
  const importFileRef = useRef<HTMLInputElement>(null);
  const [importingCsv, setImportingCsv] = useState(false);

  const handleCsvImport = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !apiClient) return;
    setImportingCsv(true);
    try {
      const formData = new FormData();
      formData.append("file", file);
      const res = await apiClient.post("/admin/products/import", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      const result = res.data as ImportResult;
      const successMsg = `Import complete: ${result.successCount}/${result.totalRows} succeeded`;
      if (result.failureCount > 0) {
        toast.error(`${successMsg}, ${result.failureCount} failed`);
        if (result.errors?.length) {
          result.errors.slice(0, 5).forEach((err) => toast.error(err, { duration: 6000 }));
        }
      } else {
        toast.success(successMsg);
      }
      onImportComplete();
    } catch (err) {
      const message = err instanceof Error ? err.message : "CSV import failed";
      toast.error(message);
    } finally {
      setImportingCsv(false);
      if (importFileRef.current) importFileRef.current.value = "";
    }
  };

  return (
    <div className="flex flex-wrap items-center gap-2.5 mb-1">
      <ExportButton
        apiClient={apiClient}
        endpoint="/admin/products/export"
        filename={`products-export-${new Date().toISOString().slice(0, 10)}.csv`}
        label="Export CSV"
        params={{ format: "csv" }}
      />

      <input
        ref={importFileRef}
        type="file"
        accept=".csv,text/csv"
        onChange={(e) => { void handleCsvImport(e); }}
        className="hidden"
      />
      <button
        type="button"
        onClick={() => importFileRef.current?.click()}
        disabled={importingCsv || !apiClient}
        className="btn-outline text-sm px-3.5 py-[7px] inline-flex items-center gap-1.5"
      >
        {importingCsv ? (
          <span className="inline-block w-3.5 h-3.5 border-2 border-brand border-t-transparent rounded-full animate-spin" />
        ) : (
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
        )}
        Import CSV
      </button>
    </div>
  );
}
