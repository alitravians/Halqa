import { GIFT_CATALOG } from "@/lib/gifts";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * Public, unauthenticated catalogue endpoint.
 *
 * Mobile clients fetch this on app start (cached) and on every Live
 * Watch open so a server-side price change propagates without an app
 * release. The picker UI uses this exclusively — there is no longer a
 * client-side hardcoded mock list.
 */
export async function GET() {
  return new Response(
    JSON.stringify({ gifts: GIFT_CATALOG }),
    {
      status: 200,
      headers: {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "public, max-age=60, s-maxage=60",
      },
    }
  );
}
