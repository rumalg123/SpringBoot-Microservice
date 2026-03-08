import { NextRequest, NextResponse } from "next/server";
import { CsrfValidationError, proxyToGateway } from "@/lib/server/bffAuth";

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

async function handle(request: NextRequest, context: RouteContext): Promise<NextResponse> {
  try {
    const { path } = await context.params;
    return await proxyToGateway(request, path);
  } catch (error) {
    if (error instanceof CsrfValidationError) {
      return NextResponse.json({ message: error.message }, { status: 403 });
    }
    return NextResponse.json({ message: "Gateway proxy unavailable" }, { status: 503 });
  }
}

export async function GET(request: NextRequest, context: RouteContext): Promise<NextResponse> {
  return handle(request, context);
}

export async function POST(request: NextRequest, context: RouteContext): Promise<NextResponse> {
  return handle(request, context);
}

export async function PUT(request: NextRequest, context: RouteContext): Promise<NextResponse> {
  return handle(request, context);
}

export async function PATCH(request: NextRequest, context: RouteContext): Promise<NextResponse> {
  return handle(request, context);
}

export async function DELETE(request: NextRequest, context: RouteContext): Promise<NextResponse> {
  return handle(request, context);
}

export async function OPTIONS(request: NextRequest, context: RouteContext): Promise<NextResponse> {
  return handle(request, context);
}
