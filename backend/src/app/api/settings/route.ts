import { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

interface SettingsBody {
  language?: "ar" | "en";
  theme?: "light" | "dark" | "auto";
  notificationsPush?: boolean;
  notificationsEmail?: boolean;
  privacyShowOnline?: boolean;
  privacyAllowMessages?: "everyone" | "followers" | "none";
}

const DEFAULTS = {
  language: "ar" as const,
  theme: "auto" as const,
  notificationsPush: true,
  notificationsEmail: true,
  privacyShowOnline: true,
  privacyAllowMessages: "everyone" as const,
};

function settingsRef(uid: string) {
  return adminFirestore().collection("users").doc(uid).collection("settings").doc("default");
}

export async function GET(req: NextRequest) {
  try {
    // PR-H — settings (language, theme, notifications) have no abuse
    // vector. Banned users may keep editing preferences.
    const user = await requireUser(req, { allowBanned: true });
    const snap = await settingsRef(user.uid).get();
    return asJson(200, { ...DEFAULTS, ...(snap.data() ?? {}) });
  } catch (err) {
    return asError(err);
  }
}

export async function POST(req: NextRequest) {
  try {
    // PR-H — settings (language, theme, notifications) have no abuse
    // vector. Banned users may keep editing preferences.
    const user = await requireUser(req, { allowBanned: true });
    const body = (await req.json()) as Partial<SettingsBody>;
    const update: Record<string, unknown> = { updatedAt: new Date().toISOString() };

    if (body.language !== undefined) {
      if (body.language !== "ar" && body.language !== "en") {
        throw new HttpError(400, "language must be ar or en.");
      }
      update.language = body.language;
    }
    if (body.theme !== undefined) {
      if (!["light", "dark", "auto"].includes(body.theme)) {
        throw new HttpError(400, "theme must be light/dark/auto.");
      }
      update.theme = body.theme;
    }
    if (body.notificationsPush !== undefined) {
      if (typeof body.notificationsPush !== "boolean") {
        throw new HttpError(400, "notificationsPush must be boolean.");
      }
      update.notificationsPush = body.notificationsPush;
    }
    if (body.notificationsEmail !== undefined) {
      if (typeof body.notificationsEmail !== "boolean") {
        throw new HttpError(400, "notificationsEmail must be boolean.");
      }
      update.notificationsEmail = body.notificationsEmail;
    }
    if (body.privacyShowOnline !== undefined) {
      if (typeof body.privacyShowOnline !== "boolean") {
        throw new HttpError(400, "privacyShowOnline must be boolean.");
      }
      update.privacyShowOnline = body.privacyShowOnline;
    }
    if (body.privacyAllowMessages !== undefined) {
      if (!["everyone", "followers", "none"].includes(body.privacyAllowMessages)) {
        throw new HttpError(400, "privacyAllowMessages invalid.");
      }
      update.privacyAllowMessages = body.privacyAllowMessages;
    }

    await settingsRef(user.uid).set(update, { merge: true });
    const fresh = await settingsRef(user.uid).get();
    return asJson(200, { ...DEFAULTS, ...(fresh.data() ?? {}) });
  } catch (err) {
    return asError(err);
  }
}
