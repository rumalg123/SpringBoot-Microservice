import { redirect } from "next/navigation";

type Category = {
  id: string;
  name: string;
  slug: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
};

export default async function CategoryProductsPage({
  params,
}: {
  params: Promise<{ name: string }>;
}) {
  const { name } = await params;
  const slug = decodeURIComponent(name || "").trim().toLowerCase();
  if (!slug || slug === "all") {
    redirect("/products");
  }

  const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
  try {
    const res = await fetch(`${apiBase}/categories`, { cache: "no-store" });
    if (!res.ok) {
      redirect("/products");
    }
    const categories = ((await res.json()) as Category[]) || [];
    const match = categories.find((category) => (category.slug || "").toLowerCase() === slug);
    if (!match) {
      redirect("/products");
    }
    if (match.type === "PARENT") {
      redirect(`/products?mainCategory=${encodeURIComponent(match.name)}`);
    }
    if (match.parentCategoryId) {
      const parent = categories.find((category) => category.id === match.parentCategoryId);
      if (parent) {
        redirect(
          `/products?mainCategory=${encodeURIComponent(parent.name)}&subCategory=${encodeURIComponent(match.name)}`
        );
      }
    }
    redirect(`/products?subCategory=${encodeURIComponent(match.name)}`);
  } catch {
    redirect("/products");
  }
}
