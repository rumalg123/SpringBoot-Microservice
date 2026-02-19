import { redirect } from "next/navigation";

export default async function CategoryProductsPage({
  params,
}: {
  params: Promise<{ name: string }>;
}) {
  const { name } = await params;
  const decoded = decodeURIComponent(name || "").trim();
  if (!decoded || decoded.toLowerCase() === "all") {
    redirect("/products");
  }
  redirect(`/products?category=${encodeURIComponent(decoded)}`);
}
