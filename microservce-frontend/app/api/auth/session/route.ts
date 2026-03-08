import { NextRequest, NextResponse } from "next/server";
import { applyCookieMutations, resolveSession, SessionSyncError } from "@/lib/server/bffAuth";

export async function GET(request: NextRequest): Promise<NextResponse> {
  try {
    const { session, cookieMutations } = await resolveSession(request, { forceGatewaySync: true });
    const response = NextResponse.json(session, { status: 200 });
    applyCookieMutations(response, cookieMutations);
    return response;
  } catch (error) {
    if (error instanceof SessionSyncError) {
      return NextResponse.json(
        { message: error.message },
        { status: error.status >= 500 ? 503 : error.status }
      );
    }
    throw error;
  }
}
