// PR-J — Reserved name/handle list.
//
// Purpose
// -------
// Block users from registering or changing their handle / displayName to
// names that impersonate Halqa staff, system roles, brands, or KSA
// institutions. Stops "@halqa", "@admin", "@support" impersonation that
// would scam other users into trusting a malicious account.
//
// How matching works
// ------------------
// `isReservedHandle(s)` lower-cases + strips diacritics + strips leading
// '@' + collapses runs of underscores → matches if the cleaned form is
// in the set OR the cleaned form starts with `halqa` / `admin` (prefix
// catch for "halqa_official", "admin_hassan", etc.).
//
// `isReservedDisplayName(s)` lower-cases + strips Arabic diacritics + NFC
// normalizes → matches if any reserved keyword appears as a whole word
// in the cleaned form. Arabic display-name policy is stricter than
// handle policy because display names render visually next to gifts /
// chat and impersonation is higher-impact.
//
// Why a static list (not a regex pattern alone)
// ---------------------------------------------
// A regex like /^(halqa|admin|.*)$/ is fragile (no Arabic support, no
// transliteration catches, hard to audit) and Layla needs a single
// auditable list she can extend without code review. The list lives in
// this file; PRs against it are tracked in git.
//
// Audit / extension
// -----------------
// Owner: Layla (T&S). Adding entries: PR against this file with one-line
// rationale per addition. Removing entries: requires Ali sign-off.

const ARABIC_DIACRITICS = /[\u064B-\u065F\u0670\u06D6-\u06ED]/g;

function clean(s: string): string {
  return s
    .normalize("NFC")
    .replace(ARABIC_DIACRITICS, "")
    .toLowerCase()
    .trim();
}

function cleanHandle(s: string): string {
  return clean(s.replace(/^@/, "").replace(/_+/g, "_"));
}

// Handles that cannot be registered AT ALL (exact match, post-clean).
// Also catches any handle that starts with one of HANDLE_PREFIXES below.
const RESERVED_HANDLES = new Set<string>([
  // Halqa brand
  "halqa",
  "halqaapp",
  "halqa_app",
  "halqa_official",
  "halqaofficial",

  // System / staff roles
  "admin",
  "administrator",
  "root",
  "system",
  "support",
  "help",
  "helpdesk",
  "service",
  "moderator",
  "mod",
  "staff",
  "team",
  "team_halqa",

  // Common impersonation targets
  "ali",
  "ali_halqa",
  "founder",
  "ceo",
  "owner",

  // KSA institutions (extend per Layla legal review)
  "nca",
  "communications",
  "citc",
  "saudi",
  "saudia",
  "ksa",

  // Generic abuse vectors
  "test",
  "guest",
  "anonymous",
  "anon",
  "null",
  "undefined",
  "deleted",
  "banned",
  "verified",
  "official",
  "verified_account",
]);

// Any handle whose cleaned form STARTS WITH one of these is reserved.
// Catches "halqa_official", "admin_ali", "support_24_7", etc.
const HANDLE_PREFIXES = ["halqa", "admin", "support", "staff", "official"];

// Display-name keywords — matched as whole-word substrings (Arabic
// or Latin). Stricter than handle policy because display names are
// the most-visible impersonation vector.
const RESERVED_DISPLAY_KEYWORDS = [
  // English
  "admin",
  "administrator",
  "halqa staff",
  "halqa team",
  "halqa support",
  "support team",
  "system",
  "moderator",
  "official",
  "verified",
  // Arabic — staff impersonation
  "إدارة حلقة",
  "ادارة حلقة",
  "ادارة الحلقة",
  "دعم حلقة",
  "دعم الحلقة",
  "حلقة رسمي",
  "حلقة الرسمية",
  "فريق حلقة",
  "فريق الحلقة",
  "موظف حلقة",
  "مشرف",
  "مشرفة",
  "مدير",
  "مديرة",
  "ادمن",
  "أدمن",
  "أدمين",
  "ادمين",
];

export function isReservedHandle(input: string): boolean {
  const c = cleanHandle(input);
  if (c.length === 0) return false;
  if (RESERVED_HANDLES.has(c)) return true;
  for (const prefix of HANDLE_PREFIXES) {
    if (c.startsWith(prefix)) return true;
  }
  return false;
}

export function isReservedDisplayName(input: string): boolean {
  const c = clean(input);
  if (c.length === 0) return false;
  for (const kw of RESERVED_DISPLAY_KEYWORDS) {
    const k = clean(kw);
    if (c === k || c.includes(k)) return true;
  }
  return false;
}

// Test hook — exported so word-filter unit tests can assert
// canonicalization stability.
export const __testing = { clean, cleanHandle };
