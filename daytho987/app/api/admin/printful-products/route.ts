import { NextResponse } from "next/server";
import { listStoreProducts } from "@/lib/printful";

export const dynamic = "force-dynamic";

// Outil d'admin : liste les produits de ton store Printful avec leurs
// sync_variant_id, pour remplir lib/products.ts.
// Protégé par ADMIN_TOKEN :  curl -H "Authorization: Bearer $ADMIN_TOKEN" https://ton-site/api/admin/printful-products
export async function GET(request: Request) {
  const token = process.env.ADMIN_TOKEN;
  if (!token || request.headers.get("authorization") !== `Bearer ${token}`) {
    return NextResponse.json({ error: "Non autorisé" }, { status: 401 });
  }
  try {
    return NextResponse.json(await listStoreProducts());
  } catch (err) {
    return NextResponse.json({ error: err instanceof Error ? err.message : "Erreur" }, { status: 502 });
  }
}
