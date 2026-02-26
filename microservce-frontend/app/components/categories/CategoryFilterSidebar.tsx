"use client";

import { FormEvent } from "react";
import CatalogFiltersSidebar from "../catalog/CatalogFiltersSidebar";
import type { Category } from "../../../lib/types/category";

type CategoryFilterSidebarProps = {
  parents: Category[];
  subsByParent: Map<string, Category[]>;
  selectedParentNames: string[];
  selectedSubNames: string[];
  expandedParentIds: Record<string, boolean>;
  minPriceInput: string;
  maxPriceInput: string;
  loading: boolean;
  onMinPriceChange: (value: string) => void;
  onMaxPriceChange: (value: string) => void;
  onApplyPriceFilter: (e: FormEvent) => void;
  onClearPriceFilter: () => void;
  onToggleParent: (parent: Category) => void;
  onToggleSub: (sub: Category) => void;
  onToggleParentExpanded: (parentId: string) => void;
};

export default function CategoryFilterSidebar({
  parents,
  subsByParent,
  selectedParentNames,
  selectedSubNames,
  expandedParentIds,
  minPriceInput,
  maxPriceInput,
  loading,
  onMinPriceChange,
  onMaxPriceChange,
  onApplyPriceFilter,
  onClearPriceFilter,
  onToggleParent,
  onToggleSub,
  onToggleParentExpanded,
}: CategoryFilterSidebarProps) {
  return (
    <CatalogFiltersSidebar
      parents={parents}
      subsByParent={subsByParent}
      selectedParentNames={selectedParentNames}
      selectedSubNames={selectedSubNames}
      expandedParentIds={expandedParentIds}
      minPriceInput={minPriceInput}
      maxPriceInput={maxPriceInput}
      loading={loading}
      onMinPriceChange={onMinPriceChange}
      onMaxPriceChange={onMaxPriceChange}
      onApplyPriceFilter={onApplyPriceFilter}
      onClearPriceFilter={onClearPriceFilter}
      onToggleParent={onToggleParent}
      onToggleSub={onToggleSub}
      onToggleParentExpanded={onToggleParentExpanded}
    />
  );
}
