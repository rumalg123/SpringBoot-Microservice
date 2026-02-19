import { redirect } from "next/navigation";

export default async function CategoryProductsPage({
  params,
}: {
  params: Promise<{ name: string }>;
}) {
  const { name } = await params;
  redirect(`/products?category=${encodeURIComponent(decodeURIComponent(name))}`);
}

