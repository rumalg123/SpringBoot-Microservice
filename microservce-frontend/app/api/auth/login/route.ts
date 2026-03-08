import { NextRequest, NextResponse } from "next/server";
import { applyCookieMutations, buildAuthorizationRedirect } from "@/lib/server/bffAuth";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { redirectUrl, cookieMutations } = await buildAuthorizationRedirect(request, "login");
  const response = NextResponse.redirect(redirectUrl);
  applyCookieMutations(response, cookieMutations);
  return response;
}
