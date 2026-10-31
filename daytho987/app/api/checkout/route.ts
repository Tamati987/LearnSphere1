import { NextResponse } from "next/server";
import { findVariant, isOrderable } from "@/lib/products";
import { stripe, SHIPPING_COUNTRIES, encodeCart, type CartLine } from "@/lib/stripe";

// Crée une session Stripe Checkout à partir du panier.
// Aucune commande Printful n'est créée ici : elle ne part qu'APRÈS paiement,
// depuis le webhook Stripe (app/api/webhooks/stripe/route.ts).
export async function POST(request: Request) {
  try {
    const body = await request.json().catch(() => null);
    const lines: CartLine[] = Array.isArray(body?.items) ? body.items : [];

    if (lines.length === 0 || lines.length > 20) {
      return NextResponse.json({ error: "Panier vide ou invalide." }, { status: 400 });
    }

    const lineItems = [];
    for (const line of lines) {
      const quantity = Number(line.quantity);
      const found = findVariant(String(line.productId), String(line.size));
      if (!found || !Number.isInteger(quantity) || quantity < 1 || quantity > 10) {
        return NextResponse.json({ error: "Article invalide dans le panier." }, { status: 400 });
      }
      if (!isOrderable(found.variant)) {
        return NextResponse.json(
          { error: `${found.product.name} (${found.variant.size}) n'est pas encore disponible.` },
          { status: 400 },
        );
      }
      lineItems.push({
        quantity,
        price_data: {
          currency: "eur",
          unit_amount: found.product.priceCents, // prix du serveur, jamais du client
          product_data: { name: `${found.product.name} — ${found.variant.size}` },
        },
      });
    }

    const origin = process.env.SITE_URL ?? new URL(request.url).origin;
    const shippingCents = Number(process.env.SHIPPING_FLAT_CENTS ?? 0);

    const session = await stripe().checkout.sessions.create({
      mode: "payment",
      line_items: lineItems,
      shipping_address_collection: { allowed_countries: SHIPPING_COUNTRIES },
      phone_number_collection: { enabled: true },
      shipping_options:
        shippingCents > 0
          ? [
              {
                shipping_rate_data: {
                  type: "fixed_amount",
                  display_name: "Livraison standard",
                  fixed_amount: { amount: shippingCents, currency: "eur" },
                },
              },
            ]
          : undefined,
      metadata: encodeCart(lines),
      locale: "fr",
      success_url: `${origin}/merci?session_id={CHECKOUT_SESSION_ID}`,
      cancel_url: `${origin}/#shop`,
    });

    return NextResponse.json({ url: session.url });
  } catch (error) {
    console.error("Erreur checkout:", error);
    const message = error instanceof Error && error.message.includes("manquante")
      ? "Paiement non configuré (clé Stripe manquante)."
      : "Impossible de lancer le paiement.";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
