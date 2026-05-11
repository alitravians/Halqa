// PR-J — Word-filter middleware for chat messages, display names,
// and bios.
//
// Status
// ------
// Best-effort, NOT a security primitive. This filter is one layer of
// defence; the real moderation pipeline is human review (via reports
// + bans). Treat hits as a SIGNAL, not a verdict — false positives
// are tolerable (user gets "rephrase your message"), false negatives
// are caught by reports → ban.
//
// Scope of the filter
// -------------------
//   classifyMessage(text) -> 'clean' | 'soft' | 'hard'
//     'clean' = no hit
//     'soft'  = mild profanity / borderline (allow with warning, log to
//               /audit_log so T&S can pattern-match repeat offenders)
//     'hard'  = block outright (CSAE flags, slurs, severe insults)
//
//   sanitizeText(text) -> { ok: bool, classification, blockedTerms?: string[] }
//
// Sources
// -------
//
//   - Public open-source Arabic profanity lists (CC-BY / public domain):
//     starter set drawn from publicly available Arabic profanity word
//     lists (multiple sources cross-referenced).
//   - Transliteration variants common in chat (k4f1r, b00sa, etc) —
//     hand-augmented for closed beta.
//   - English slurs — minimal set (Halqa is Arabic-first but allows
//     English).
//
// Audit / extension
// -----------------
// Owner: Layla (T&S). To add a term:
//   1. PR against this file.
//   2. Specify category ('hard' or 'soft').
//   3. One-line rationale.
//   4. Source link if it came from a public list.
// To remove: requires Ali sign-off (could be revealing a false negative
// for an active abuser).
//
// What this filter does NOT do
// ----------------------------
//   - It does NOT do semantic understanding. "I want to harm the kafir"
//     is hate speech but classifyMessage returns 'clean' if no listed
//     term appears. That's caught by user reports → ban.
//   - It does NOT detect doxxing (phone numbers, addresses). Separate
//     PII filter is a future PR.
//   - It does NOT detect spam / promotional content. Separate spam
//     filter is a future PR.
//   - It does NOT block CSAE imagery (no image input). LiveKit recordings
//     are scanned out-of-band in v0.2 (LAYLA-004 pipeline).
//
// Normalization (matches against text after this)
// ----------------------------------------------
//   1. NFC unicode normalize.
//   2. Lowercase (English).
//   3. Strip Arabic diacritics (تشكيل).
//   4. Replace common letter-substitution variants:
//        0 -> o, 1 -> i, 3 -> e, 4 -> a, 5 -> s, 7 -> t, $ -> s, @ -> a
//   5. Replace Arabic letter normalization: أ إ آ ا -> ا,  ى -> ي,  ة -> ه
//   6. Collapse repeated chars: "kaaaafir" -> "kafir" (3+ same char -> 1).
//   7. Strip non-alphanumeric except spaces.

const ARABIC_DIACRITICS = /[\u064B-\u065F\u0670\u06D6-\u06ED]/g;
const ARABIC_TATWEEL = /\u0640/g;

const LEET_MAP: Record<string, string> = {
  "0": "o",
  "1": "i",
  "3": "e",
  "4": "a",
  "5": "s",
  "7": "t",
  $: "s",
  "@": "a",
};

function normalize(s: string): string {
  let t = s.normalize("NFC").toLowerCase();
  t = t.replace(ARABIC_DIACRITICS, "");
  t = t.replace(ARABIC_TATWEEL, "");
  // Arabic letter unify
  t = t.replace(/[أإآ]/g, "ا");
  t = t.replace(/ى/g, "ي");
  t = t.replace(/ة/g, "ه");
  // Leet
  t = t.replace(/[01345 7$@]/g, (c) => LEET_MAP[c] ?? c);
  // Collapse 3+ repeats
  t = t.replace(/(.)\1{2,}/g, "$1");
  // Strip punctuation but keep spaces
  t = t.replace(/[^\p{L}\p{N}\s]/gu, " ");
  // Collapse whitespace
  t = t.replace(/\s+/g, " ").trim();
  return t;
}

// HARD list — block outright. Includes:
//   - CSAE related terms (block + flag for staff queue)
//   - Severe slurs (Arabic + English)
//   - Direct hate-speech targeting protected classes
//
// Add new HARD entries with extreme care; over-broad terms cause
// closed-beta UX regressions.
const HARD_TERMS = [
  // CSAE flags (block + log)
  "child porn",
  "cp link",
  "kiddie",
  "loli",
  "shota",
  // Severe English slurs (minimal closed-beta set)
  "nigger",
  "n1gger",
  "faggot",
  "f4ggot",
  "tranny",
  // Severe Arabic slurs / hate speech
  "كافر",
  "كفار",
  "كلب",
  "كلبة",
  "حقير",
  "حقيرة",
  "زاني",
  "زانية",
  "عرص",
  "قواد",
  "خنيث",
  "حيوان",
];

// SOFT list — mild profanity / borderline. Allow with warning;
// log to /audit_log for pattern detection.
const SOFT_TERMS = [
  // English mild
  "fuck",
  "shit",
  "bitch",
  "dumb",
  "stupid",
  "idiot",
  // Arabic mild
  "غبي",
  "غبية",
  "اخرس",
  "اسكت",
  "تافه",
  "تافهة",
  "حمار",
  "حمارة",
  "جحش",
];

// Pre-normalize at module load — saves work per request.
const HARD_NORM = HARD_TERMS.map(normalize).filter((s) => s.length > 0);
const SOFT_NORM = SOFT_TERMS.map(normalize).filter((s) => s.length > 0);

export type WordFilterClassification = "clean" | "soft" | "hard";

export interface WordFilterResult {
  ok: boolean;
  classification: WordFilterClassification;
  blockedTerms: string[]; // matched normalized forms (for /audit_log)
}

export function classifyText(text: string): WordFilterResult {
  if (!text || text.length === 0) {
    return { ok: true, classification: "clean", blockedTerms: [] };
  }
  const norm = normalize(text);
  if (norm.length === 0) {
    return { ok: true, classification: "clean", blockedTerms: [] };
  }

  const hits: string[] = [];

  for (const term of HARD_NORM) {
    // Word-boundary match: term must appear as a whole word in norm.
    // Use a non-regex approach because Arabic word boundaries are
    // tricky — check that the position before/after is whitespace or
    // a string boundary.
    if (containsAsWord(norm, term)) {
      hits.push(term);
    }
  }
  if (hits.length > 0) {
    return { ok: false, classification: "hard", blockedTerms: hits };
  }

  const softHits: string[] = [];
  for (const term of SOFT_NORM) {
    if (containsAsWord(norm, term)) {
      softHits.push(term);
    }
  }
  if (softHits.length > 0) {
    return { ok: true, classification: "soft", blockedTerms: softHits };
  }

  return { ok: true, classification: "clean", blockedTerms: [] };
}

function containsAsWord(haystack: string, needle: string): boolean {
  if (needle.length === 0) return false;
  let from = 0;
  while (from <= haystack.length - needle.length) {
    const idx = haystack.indexOf(needle, from);
    if (idx === -1) return false;
    const before = idx === 0 ? " " : haystack[idx - 1];
    const after =
      idx + needle.length === haystack.length
        ? " "
        : haystack[idx + needle.length];
    // Word boundary = whitespace or non-letter/digit. Since we
    // stripped punctuation in normalize(), only whitespace counts.
    const beforeOk = before === " " || before === undefined;
    const afterOk = after === " " || after === undefined;
    if (beforeOk && afterOk) return true;
    from = idx + 1;
  }
  return false;
}

// Test hook — surface normalization for unit tests.
export const __testing = { normalize, containsAsWord };
