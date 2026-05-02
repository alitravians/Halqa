import type { Metadata } from "next";
import { Inter, Cairo } from "next/font/google";
import "./globals.css";
import { BRAND } from "@/lib/brand";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  display: "swap",
});

const cairo = Cairo({
  variable: "--font-cairo",
  subsets: ["arabic", "latin"],
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: `${BRAND.name} | ${BRAND.nameAr} — ${BRAND.tagline.ar}`,
    template: `%s · ${BRAND.name}`,
  },
  description: BRAND.description.ar,
  applicationName: BRAND.name,
  keywords: [
    "بث مباشر",
    "live streaming",
    "PK Arena",
    "Halqa",
    "حلقة",
    "كوينز",
    "هدايا",
    "BIGO LIVE",
    "TikTok Live",
  ],
  openGraph: {
    title: `${BRAND.name} | ${BRAND.nameAr}`,
    description: BRAND.description.ar,
    locale: "ar_SA",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: `${BRAND.name} | ${BRAND.nameAr}`,
    description: BRAND.description.ar,
  },
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html
      lang="ar"
      dir="rtl"
      className={`${inter.variable} ${cairo.variable}`}
      suppressHydrationWarning
    >
      <body className="min-h-screen antialiased">{children}</body>
    </html>
  );
}
