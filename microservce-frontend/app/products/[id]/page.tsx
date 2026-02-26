import type { Metadata } from "next";
import { API_BASE } from "../../../lib/constants";
import ProductDetailClient from "./ProductDetailClient";

type ProductMeta = {
  name: string;
  shortDescription: string | null;
  mainImage: string | null;
  sellingPrice: number;
  brandName: string | null;
};

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  try {
    const res = await fetch(`${API_BASE}/products/${encodeURIComponent(id)}`, {
      next: { revalidate: 300 },
    });
    if (!res.ok) return { title: "Product – Rumal Store" };
    const product = (await res.json()) as ProductMeta;
    const description =
      product.shortDescription ||
      `Shop ${product.name} at Rumal Store. Best prices and fast delivery.`;
    const imageUrl = product.mainImage
      ? product.mainImage.startsWith("http")
        ? product.mainImage
        : `${API_BASE}/products/images/${product.mainImage}`
      : undefined;

    return {
      title: `${product.name} – Rumal Store`,
      description,
      openGraph: {
        title: product.name,
        description,
        ...(imageUrl ? { images: [{ url: imageUrl, width: 600, height: 600 }] } : {}),
        type: "website",
      },
    };
  } catch {
    return { title: "Product – Rumal Store" };
  }
}

export default function ProductDetailPage() {
  return <ProductDetailClient />;
}
