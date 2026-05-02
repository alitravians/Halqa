import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatNumber(n: number, locale: string = "ar"): string {
  // Use ASCII (Western) digits everywhere so "1.5ك" and "500" share the same
  // digit script. ar-SA via Intl.NumberFormat would otherwise emit
  // Arabic-Indic digits (٠-٩) only below 1,000, producing inconsistent runs.
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace(/\.0$/, "") + (locale === "ar" ? "م" : "M");
  if (n >= 1_000) return (n / 1_000).toFixed(1).replace(/\.0$/, "") + (locale === "ar" ? "ك" : "K");
  return n.toString();
}

export function formatCoins(n: number, locale: string = "ar"): string {
  return formatNumber(n, locale);
}

export function diamondsToUSD(diamonds: number, ratePercent: number = 45): number {
  return (diamonds * 0.10 * ratePercent) / 100;
}

export function generateHandle(name: string): string {
  return (
    name
      .toLowerCase()
      .replace(/[^a-z0-9\u0600-\u06FF]+/g, "")
      .slice(0, 20) + Math.floor(1000 + Math.random() * 9000)
  );
}

export function timeAgo(date: Date | string, locale: string = "ar"): string {
  const d = typeof date === "string" ? new Date(date) : date;
  const seconds = Math.floor((Date.now() - d.getTime()) / 1000);
  const ar = locale === "ar";
  if (seconds < 60) return ar ? "الآن" : "now";
  const m = Math.floor(seconds / 60);
  if (m < 60) return ar ? `قبل ${m} د` : `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return ar ? `قبل ${h} س` : `${h}h ago`;
  const days = Math.floor(h / 24);
  if (days < 7) return ar ? `قبل ${days} ي` : `${days}d ago`;
  return d.toLocaleDateString(ar ? "ar-SA" : "en-US");
}
