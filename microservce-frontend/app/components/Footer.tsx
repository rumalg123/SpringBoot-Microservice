import Link from "next/link";

export default function Footer() {
    return (
        <footer className="mt-12 bg-[var(--footer-bg)] text-[var(--footer-text)]">
            {/* Main Footer */}
            <div className="mx-auto grid max-w-7xl gap-8 px-6 py-10 sm:grid-cols-2 lg:grid-cols-4">
                {/* Column 1 - About */}
                <div>
                    <h3 className="mb-4 flex items-center gap-2 text-lg font-bold text-white">
                        🛒 Rumal Store
                    </h3>
                    <p className="text-sm leading-relaxed text-gray-400">
                        Your trusted online marketplace. Discover amazing deals on top products with fast delivery and secure payments.
                    </p>
                </div>

                {/* Column 2 - Customer Service */}
                <div>
                    <h4 className="mb-4 text-sm font-bold uppercase tracking-wider text-white">
                        Customer Service
                    </h4>
                    <ul className="space-y-2 text-sm">
                        <li>
                            <Link href="/products" className="text-gray-400 no-underline transition hover:text-[var(--brand)]">
                                Help Center
                            </Link>
                        </li>
                        <li>
                            <Link href="/orders" className="text-gray-400 no-underline transition hover:text-[var(--brand)]">
                                Track Order
                            </Link>
                        </li>
                        <li>
                            <Link href="/products" className="text-gray-400 no-underline transition hover:text-[var(--brand)]">
                                Returns & Refunds
                            </Link>
                        </li>
                        <li>
                            <Link href="/products" className="text-gray-400 no-underline transition hover:text-[var(--brand)]">
                                Shipping Info
                            </Link>
                        </li>
                    </ul>
                </div>

                {/* Column 3 - Quick Links */}
                <div>
                    <h4 className="mb-4 text-sm font-bold uppercase tracking-wider text-white">
                        Quick Links
                    </h4>
                    <ul className="space-y-2 text-sm">
                        <li>
                            <Link href="/products" className="text-gray-400 no-underline transition hover:text-[var(--brand)]">
                                Shop All
                            </Link>
                        </li>
                        <li>
                            <Link href="/products" className="text-gray-400 no-underline transition hover:text-[var(--brand)]">
                                New Arrivals
                            </Link>
                        </li>
                        <li>
                            <Link href="/products" className="text-gray-400 no-underline transition hover:text-[var(--brand)]">
                                Best Sellers
                            </Link>
                        </li>
                        <li>
                            <Link href="/products" className="text-gray-400 no-underline transition hover:text-[var(--brand)]">
                                Flash Deals
                            </Link>
                        </li>
                    </ul>
                </div>

                {/* Column 4 - My Account */}
                <div>
                    <h4 className="mb-4 text-sm font-bold uppercase tracking-wider text-white">
                        My Account
                    </h4>
                    <ul className="space-y-2 text-sm">
                        <li>
                            <Link href="/profile" className="text-gray-400 no-underline transition hover:text-[var(--brand)]">
                                My Profile
                            </Link>
                        </li>
                        <li>
                            <Link href="/orders" className="text-gray-400 no-underline transition hover:text-[var(--brand)]">
                                My Orders
                            </Link>
                        </li>
                        <li>
                            <Link href="/" className="text-gray-400 no-underline transition hover:text-[var(--brand)]">
                                Sign In
                            </Link>
                        </li>
                    </ul>

                    <div className="mt-5">
                        <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-white">We Accept</p>
                        <div className="flex gap-2 text-lg">
                            <span title="Visa">💳</span>
                            <span title="MasterCard">💳</span>
                            <span title="PayPal">🅿️</span>
                            <span title="Cash on Delivery">💵</span>
                        </div>
                    </div>
                </div>
            </div>

            {/* Bottom Bar */}
            <div className="border-t border-white/10 bg-[#111128]">
                <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-6 py-4">
                    <p className="text-xs text-gray-500">
                        © {new Date().getFullYear()} Rumal Store. All rights reserved.
                    </p>
                    <div className="flex items-center gap-4 text-lg">
                        <span title="Facebook" className="cursor-pointer transition hover:opacity-80">📘</span>
                        <span title="Twitter" className="cursor-pointer transition hover:opacity-80">🐦</span>
                        <span title="Instagram" className="cursor-pointer transition hover:opacity-80">📷</span>
                        <span title="YouTube" className="cursor-pointer transition hover:opacity-80">▶️</span>
                    </div>
                </div>
            </div>
        </footer>
    );
}
