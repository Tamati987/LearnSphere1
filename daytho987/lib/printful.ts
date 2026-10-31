// Client minimal pour l'API Printful (v1) — uniquement côté serveur.

const API = "https://api.printful.com";

function headers() {
  const key = process.env.PRINTFUL_API_KEY;
  if (!key) throw new Error("PRINTFUL_API_KEY manquante");
  const h: Record<string, string> = {
    Authorization: `Bearer ${key}`,
    "Content-Type": "application/json",
  };
  // Nécessaire si le token est de niveau "compte" (plusieurs stores) :
  // il faut alors préciser le store DAYTHO.
  if (process.env.PRINTFUL_STORE_ID) h["X-PF-Store-Id"] = process.env.PRINTFUL_STORE_ID;
  return h;
}

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API}${path}`, { ...init, headers: headers(), cache: "no-store" });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(`Printful ${res.status}: ${data?.error?.message ?? data?.result ?? "erreur inconnue"}`);
  }
  return data.result as T;
}

export type PrintfulRecipient = {
  name: string;
  address1: string;
  address2?: string;
  city: string;
  state_code?: string;
  country_code: string;
  zip: string;
  email?: string;
  phone?: string;
};

export type PrintfulOrderItem = {
  sync_variant_id: number;
  quantity: number;
  retail_price: string;
};

export function createOrder(params: {
  externalId: string;
  recipient: PrintfulRecipient;
  items: PrintfulOrderItem[];
  confirm: boolean;
}) {
  const qs = params.confirm ? "?confirm=true" : "";
  return call<{ id: number; status: string }>(`/orders${qs}`, {
    method: "POST",
    body: JSON.stringify({
      // external_id unique = la session Stripe : Printful refuse un doublon,
      // donc un webhook rejoué ne crée pas deux commandes.
      external_id: params.externalId,
      recipient: params.recipient,
      items: params.items,
    }),
  });
}

type SyncProduct = { id: number; name: string };
type SyncProductDetail = {
  sync_product: SyncProduct;
  sync_variants: { id: number; name: string; size?: string; retail_price: string }[];
};

/** Liste les produits du store Printful avec leurs variantes (pour remplir lib/products.ts). */
export async function listStoreProducts() {
  const list = await call<SyncProduct[]>("/store/products?limit=100");
  return Promise.all(
    list.map(async (p) => {
      const d = await call<SyncProductDetail>(`/store/products/${p.id}`);
      return {
        product: d.sync_product.name,
        sync_product_id: d.sync_product.id,
        variants: d.sync_variants.map((v) => ({
          sync_variant_id: v.id,
          name: v.name,
          size: v.size,
          retail_price: v.retail_price,
        })),
      };
    }),
  );
}
