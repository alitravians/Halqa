export const BRAND = {
  name: "Halqa",
  nameAr: "حلقة",
  tagline: {
    ar: "حلقتك تبدأ هنا",
    en: "Where moments gather",
  },
  description: {
    ar: "تطبيق البث المباشر العربي مع PK Arena مبتكرة. ابثّ، شاهد، تنافس، اكسب.",
    en: "Arabic-first live streaming with an innovative PK Arena. Stream, watch, battle, earn.",
  },
  social: {
    twitter: "@halqa_app",
    instagram: "@halqa_app",
    tiktok: "@halqa_app",
  },
  colors: {
    primary: "#7C3AED",
    primaryFrom: "#7C3AED",
    primaryTo: "#EC4899",
    accent: "#F59E0B",
    bg: "#0A0A1A",
    surface: "#13132B",
    text: "#F5F5F7",
    textMuted: "#9CA3AF",
  },
} as const;

export const ROUTES = {
  home: "/",
  feed: "/feed",
  goLive: "/go-live",
  arena: "/arena",
  wallet: "/wallet",
  profile: (handle: string) => `/u/${handle}`,
  liveWatch: (id: string) => `/live/${id}`,
  signin: "/signin",
  signup: "/signup",
  terms: "/legal/terms",
  privacy: "/legal/privacy",
  community: "/legal/community",
} as const;
