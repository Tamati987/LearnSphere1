import Stripe from "stripe";

let client: Stripe | null = null;

export function stripe() {
  const key = process.env.STRIPE_SECRET_KEY;
  if (!key) throw new Error("STRIPE_SECRET_KEY manquante");
  client ??= new Stripe(key);
  return client;
}

// Pays de livraison proposés au client (Printful + Stripe).
export const SHIPPING_COUNTRIES: Stripe.Checkout.SessionCreateParams.ShippingAddressCollection.AllowedCountry[] = [
  "PF", "FR", "NC", "RE", "GP", "MQ", "GF", "YT", "BE", "CH", "LU", "DE", "ES", "IT", "PT", "NL", "GB", "US", "CA", "AU", "NZ",
];

// Les articles du panier voyagent dans les métadonnées Stripe sous une forme
// compacte : "productId|taille|quantité;...". Stripe limite chaque valeur à
// 500 caractères : la chaîne est donc découpée en cart_0, cart_1, ...
export type CartLine = { productId: string; size: string; quantity: number };

const CHUNK = 450;

export function encodeCart(lines: CartLine[]): Record<string, string> {
  const raw = lines.map((l) => `${l.productId}|${l.size}|${l.quantity}`).join(";");
  const meta: Record<string, string> = {};
  for (let i = 0; i * CHUNK < raw.length; i++) meta[`cart_${i}`] = raw.slice(i * CHUNK, (i + 1) * CHUNK);
  return meta;
}

export function decodeCart(metadata: Record<string, string> | null | undefined): CartLine[] {
  let raw = "";
  for (let i = 0; metadata?.[`cart_${i}`] !== undefined; i++) raw += metadata[`cart_${i}`];
  return raw
    .split(";")
    .filter(Boolean)
    .map((s) => {
      const [productId, size, quantity] = s.split("|");
      return { productId, size, quantity: Number(quantity) };
    });
}
