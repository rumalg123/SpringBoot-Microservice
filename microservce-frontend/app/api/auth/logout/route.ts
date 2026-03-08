import { NextRequest, NextResponse } from "next/server";
import { applyCookieMutations, assertValidCsrf, buildLogoutRedirect, CsrfValidationError } from "@/lib/server/bffAuth";

export async function POST(request: NextRequest): Promise<NextResponse> {
  try {
    assertValidCsrf(request);
    const { redirectUrl, cookieMutations } = await buildLogoutRedirect(request);
    const response = NextResponse.json({ redirectUrl }, { status: 200 });
    applyCookieMutations(response, cookieMutations);
    return response;
  } catch (error) {
    if (error instanceof CsrfValidationError) {
      return NextResponse.json({ message: error.message }, { status: 403 });
    }
    return NextResponse.json({ message: "Logout failed" }, { status: 503 });
  }
}
