import type { Metadata } from "next";
import { Inter, Syne } from "next/font/google";
import "./globals.css";
import AppToaster from "./components/AppToaster";
import ScrollToTop from "./components/ScrollToTop";
import BackToTop from "./components/BackToTop";

const bodyFont = Inter({
  variable: "--font-body",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800", "900"],
});

const displayFont = Syne({
  variable: "--font-display",
  subsets: ["latin"],
  weight: ["600", "700", "800"],
});

export const metadata: Metadata = {
  title: "Rumal Store – Shop Online | Best Deals & Discounts",
  description: "Discover amazing deals on top products. Shop with confidence at Rumal Store — your trusted online marketplace with secure payments, fast delivery, and easy returns.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={`${bodyFont.variable} ${displayFont.variable} antialiased`}>
        <ScrollToTop />
        <AppToaster />
        {children}
        <BackToTop />
      </body>
    </html>
  );
}
