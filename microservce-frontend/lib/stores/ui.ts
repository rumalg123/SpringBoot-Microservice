import { create } from "zustand";

type UIState = {
  mobileMenuOpen: boolean;
  cartDropdownOpen: boolean;
  wishlistDropdownOpen: boolean;

  toggleMobileMenu: () => void;
  closeMobileMenu: () => void;
  setCartDropdownOpen: (open: boolean) => void;
  setWishlistDropdownOpen: (open: boolean) => void;
  closeAllDropdowns: () => void;
};

export const useUIStore = create<UIState>((set) => ({
  mobileMenuOpen: false,
  cartDropdownOpen: false,
  wishlistDropdownOpen: false,

  toggleMobileMenu: () => set((s) => ({ mobileMenuOpen: !s.mobileMenuOpen })),
  closeMobileMenu: () => set({ mobileMenuOpen: false }),
  setCartDropdownOpen: (open) => set({ cartDropdownOpen: open, wishlistDropdownOpen: false }),
  setWishlistDropdownOpen: (open) => set({ wishlistDropdownOpen: open, cartDropdownOpen: false }),
  closeAllDropdowns: () => set({ cartDropdownOpen: false, wishlistDropdownOpen: false, mobileMenuOpen: false }),
}));
