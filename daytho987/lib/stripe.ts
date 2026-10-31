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
// compacte : "productId|taille|quantité;..." (500 caractères max par valeur).
export type CartLine = { productId: string; size: string; quantity: number };

export const encodeCart = (lines: CartLine[]) =>
  lines.map((l) => `${l.productId}|${l.size}|${l.quantity}`).join(";");

export const decodeCart = (raw: string): CartLine[] =>
  raw
    .split(";")
    .filter(Boolean)
    .map((s) => {
      const [productId, size, quantity] = s.split("|");
      return { productId, size, quantity: Number(quantity) };
    });
