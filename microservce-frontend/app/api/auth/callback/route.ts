import { NextRequest, NextResponse } from "next/server";
import { applyCookieMutations, exchangeCallback } from "@/lib/server/bffAuth";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { redirectTo, cookieMutations } = await exchangeCallback(request);
  const response = NextResponse.redirect(redirectTo);
  applyCookieMutations(response, cookieMutations);
  return response;
}
