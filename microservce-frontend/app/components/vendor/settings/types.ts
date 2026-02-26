"use client";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

export type VendorProfile = {
  id?: string;
  name: string;
  slug?: string;
  contactEmail: string;
  supportEmail: string;
  contactPhone: string;
  contactPersonName: string;
  logoImage: string;
  bannerImage: string;
  websiteUrl: string;
  description: string;
  returnPolicy: string;
  shippingPolicy: string;
  processingTimeDays: number | "";
  acceptsReturns: boolean;
  returnWindowDays: number | "";
  freeShippingThreshold: number | "";
  primaryCategory: string;
  specializations: string;
  verificationStatus?: string;
  acceptingOrders?: boolean;
};

export type PayoutConfig = {
  payoutCurrency: string;
  payoutSchedule: string;
  payoutMinimum: number | "";
  bankAccountHolder: string;
  bankName: string;
  bankRoutingCode: string;
  bankAccountNumberMasked: string;
  taxId: string;
};

export type Tab = "profile" | "payout" | "actions";

/* ------------------------------------------------------------------ */
/*  Constants                                                          */
/* ------------------------------------------------------------------ */

export const EMPTY_VENDOR: VendorProfile = {
  name: "",
  contactEmail: "",
  supportEmail: "",
  contactPhone: "",
  contactPersonName: "",
  logoImage: "",
  bannerImage: "",
  websiteUrl: "",
  description: "",
  returnPolicy: "",
  shippingPolicy: "",
  processingTimeDays: "",
  acceptsReturns: false,
  returnWindowDays: "",
  freeShippingThreshold: "",
  primaryCategory: "",
  specializations: "",
};

export const EMPTY_PAYOUT: PayoutConfig = {
  payoutCurrency: "USD",
  payoutSchedule: "MONTHLY",
  payoutMinimum: "",
  bankAccountHolder: "",
  bankName: "",
  bankRoutingCode: "",
  bankAccountNumberMasked: "",
  taxId: "",
};
