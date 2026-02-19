"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";

/**
 * Scrolls the window to the top whenever the Next.js pathname changes.
 * Place this once inside your root layout so every route change starts at top.
 */
export default function ScrollToTop() {
    const pathname = usePathname();

    useEffect(() => {
        window.scrollTo({ top: 0, behavior: "smooth" });
    }, [pathname]);

    return null;
}
