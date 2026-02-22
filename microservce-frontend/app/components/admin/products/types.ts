"use client";

import type {
  ChangeEvent,
  Dispatch,
  FormEvent,
  KeyboardEvent,
  SetStateAction,
  WheelEvent,
} from "react";

export type ProductType = "SINGLE" | "PARENT" | "VARIATION";

export type SlugStatus = "idle" | "checking" | "available" | "taken" | "invalid";

export type VariationPair = {
  name: string;
  value: string;
};

export type ProductSummary = {
  id: string;
  slug: string;
  name: string;
  shortDescription: string;
  mainImage: string | null;
  sellingPrice: number;
  sku: string;
  productType: ProductType;
  vendorId: string;
  categories: string[];
  active: boolean;
  variations?: VariationPair[];
};

export type Category = {
  id: string;
  name: string;
  slug: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
  deleted?: boolean;
};

export type ProductFormState = {
  id?: string;
  name: string;
  slug: string;
  shortDescription: string;
  description: string;
  images: string[];
  regularPrice: string;
  discountedPrice: string;
  vendorId: string;
  mainCategoryName: string;
  subCategoryNames: string[];
  productType: ProductType;
  sku: string;
  active: boolean;
};

export type VendorSummary = {
  id: string;
  name: string;
  slug: string;
  contactEmail: string;
  active: boolean;
  deleted: boolean;
  status?: string;
};

export type VariationDraft = {
  id: string;
  parentId: string;
  parentLabel: string;
  mainCategoryName: string;
  subCategoryNames: string[];
  payload: {
    name: string;
    slug: string;
    shortDescription: string;
    description: string;
    images: string[];
    regularPrice: number;
    discountedPrice: number | null;
    vendorId: string | null;
    categories: string[];
    productType: "VARIATION";
    variations: VariationPair[];
    sku: string;
    active: boolean;
  };
};

export type ProductEditorPanelState = {
  form: ProductFormState;
  emptyForm: ProductFormState;
  productMutationBusy: boolean;
  isEditingProduct: boolean;
  isEditingVariation: boolean;
  productSlugStatus: SlugStatus;
  parentAttributeNames: string[];
  newParentAttributeName: string;
  uploadingImages: boolean;
  parentCategories: Category[];
  subCategoryOptions: Category[];
  vendors: VendorSummary[];
  loadingVendors: boolean;
  priceValidationMessage: string | null;
  parentSearch: string;
  loadingParentProducts: boolean;
  parentProducts: ProductSummary[];
  filteredParentProducts: ProductSummary[];
  variationParentId: string;
  selectingVariationParentId: string | null;
  selectedVariationParent: ProductSummary | null;
  parentVariationAttributes: string[];
  variationAttributeValues: Record<string, string>;
  canQueueVariation: boolean;
  canAddVariationDraft: boolean;
  canCreateQueuedVariations: boolean;
  variationDrafts: VariationDraft[];
  creatingQueuedVariationBatch: boolean;
  variationDraftBlockedReason: string | null;
  productSlugBlocked: boolean;
  savingProduct: boolean;
};

export type ProductEditorPanelActions = {
  submitProduct: (e: FormEvent<HTMLFormElement>) => void | Promise<void>;
  setForm: Dispatch<SetStateAction<ProductFormState>>;
  setProductSlugTouched: Dispatch<SetStateAction<boolean>>;
  setProductSlugStatus: Dispatch<SetStateAction<SlugStatus>>;
  setParentAttributeNames: Dispatch<SetStateAction<string[]>>;
  setNewParentAttributeName: Dispatch<SetStateAction<string>>;
  setVariationParentId: Dispatch<SetStateAction<string>>;
  setSelectedVariationParent: Dispatch<SetStateAction<ProductSummary | null>>;
  setParentSearch: Dispatch<SetStateAction<string>>;
  setParentVariationAttributes: Dispatch<SetStateAction<string[]>>;
  setVariationAttributeValues: Dispatch<SetStateAction<Record<string, string>>>;
  setVariationDrafts: Dispatch<SetStateAction<VariationDraft[]>>;
  uploadImages: (event: ChangeEvent<HTMLInputElement>) => void | Promise<void>;
  setDragImageIndex: Dispatch<SetStateAction<number | null>>;
  onImageDrop: (index: number) => void;
  removeImage: (index: number) => void;
  addParentAttribute: () => void;
  removeParentAttribute: (name: string) => void;
  refreshVendors: () => void | Promise<void>;
  refreshVariationParents: () => void | Promise<void>;
  onSelectVariationParent: (id: string) => void | Promise<void>;
  addVariationDraft: () => void;
  createQueuedVariations: () => void | Promise<void>;
  loadVariationDraftToForm: (id: string) => void;
  removeVariationDraft: (id: string) => void;
  updateVariationDraftPayload: (id: string, patch: Partial<VariationDraft["payload"]>) => void;
  updateVariationDraftAttributeValue: (id: string, attributeName: string, value: string) => void;
};

export type ProductEditorPanelHelpers = {
  slugify: (value: string) => string;
  resolveImageUrl: (imageName: string) => string | null;
  MAX_IMAGE_COUNT: number;
  canSelectVendor: boolean;
  preventNumberInputScroll: (e: WheelEvent<HTMLInputElement>) => void;
  preventNumberInputArrows: (e: KeyboardEvent<HTMLInputElement>) => void;
};

export type ProductEditorPanelProps = {
  state: ProductEditorPanelState;
  actions: ProductEditorPanelActions;
  helpers: ProductEditorPanelHelpers;
};

export type ProductImagesEditorProps = {
  form: Pick<ProductFormState, "images">;
  maxImageCount: number;
  resolveImageUrl: ProductEditorPanelHelpers["resolveImageUrl"];
  uploadingImages: boolean;
  productMutationBusy: boolean;
  uploadImages: ProductEditorPanelActions["uploadImages"];
  setDragImageIndex: ProductEditorPanelActions["setDragImageIndex"];
  onImageDrop: ProductEditorPanelActions["onImageDrop"];
  removeImage: ProductEditorPanelActions["removeImage"];
};

export type VariationAttributesEditorProps = {
  parentAttributeNames: string[];
  newParentAttributeName: string;
  setNewParentAttributeName: ProductEditorPanelActions["setNewParentAttributeName"];
  addParentAttribute: ProductEditorPanelActions["addParentAttribute"];
  removeParentAttribute: ProductEditorPanelActions["removeParentAttribute"];
  productMutationBusy: boolean;
};

export type VariationRowsEditorProps = {
  canQueueVariation: boolean;
  canAddVariationDraft: boolean;
  canCreateQueuedVariations: boolean;
  variationDrafts: VariationDraft[];
  creatingQueuedVariationBatch: boolean;
  variationDraftBlockedReason: string | null;
  savingProduct: boolean;
  setVariationDrafts: ProductEditorPanelActions["setVariationDrafts"];
  addVariationDraft: ProductEditorPanelActions["addVariationDraft"];
  createQueuedVariations: ProductEditorPanelActions["createQueuedVariations"];
  loadVariationDraftToForm: ProductEditorPanelActions["loadVariationDraftToForm"];
  removeVariationDraft: ProductEditorPanelActions["removeVariationDraft"];
  updateVariationDraftPayload: ProductEditorPanelActions["updateVariationDraftPayload"];
  updateVariationDraftAttributeValue: ProductEditorPanelActions["updateVariationDraftAttributeValue"];
  slugify: ProductEditorPanelHelpers["slugify"];
  preventNumberInputScroll: ProductEditorPanelHelpers["preventNumberInputScroll"];
  preventNumberInputArrows: ProductEditorPanelHelpers["preventNumberInputArrows"];
};

export type VariationParentSelectorPanelProps = ProductEditorPanelProps;

export type VendorSelectorFieldProps = {
  form: ProductFormState;
  setForm: ProductEditorPanelActions["setForm"];
  productMutationBusy: boolean;
  canSelectVendor: boolean;
  vendors: VendorSummary[];
  loadingVendors: boolean;
  refreshVendors: ProductEditorPanelActions["refreshVendors"];
  selectedVariationParent: ProductSummary | null;
};
