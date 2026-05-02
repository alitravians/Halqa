export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  return new Response(
    JSON.stringify({
      ok: true,
      service: "halqa-backend",
      time: new Date().toISOString(),
      hasFirebase: Boolean(process.env.FIREBASE_SERVICE_ACCOUNT_JSON),
      hasLivekit: Boolean(
        process.env.LIVEKIT_API_KEY &&
          process.env.LIVEKIT_API_SECRET &&
          process.env.LIVEKIT_URL
      ),
    }),
    { headers: { "content-type": "application/json" } }
  );
}
