import { NextResponse } from "next/server";
import type Stripe from "stripe";
import { stripe, decodeCart } from "@/lib/stripe";
import { findVariant, isOrderable } from "@/lib/products";
import { createOrder } from "@/lib/printful";

// Stripe appelle cette route après un paiement réussi.
// La signature est vérifiée : personne ne peut déclencher une commande
// Printful sans avoir réellement payé.
export async function POST(request: Request) {
  const secret = process.env.STRIPE_WEBHOOK_SECRET;
  const signature = request.headers.get("stripe-signature");
  if (!secret || !signature) {
    return NextResponse.json({ error: "Webhook non configuré" }, { status: 400 });
  }

  let event: Stripe.Event;
  try {
    const rawBody = await request.text();
    event = stripe().webhooks.constructEvent(rawBody, signature, secret);
  } catch (err) {
    console.error("Signature Stripe invalide:", err);
    return NextResponse.json({ error: "Signature invalide" }, { status: 400 });
  }

  if (event.type !== "checkout.session.completed" && event.type !== "checkout.session.async_payment_succeeded") {
    return NextResponse.json({ received: true });
  }

  const session = event.data.object as Stripe.Checkout.Session;
  if (session.payment_status !== "paid") {
    return NextResponse.json({ received: true, skipped: "paiement non finalisé" });
  }

  const shipping = session.collected_information?.shipping_details ?? session.shipping_details;
  const address = shipping?.address;
  if (!shipping || !address?.line1 || !address.city || !address.country) {
    console.error("Adresse de livraison manquante pour la session", session.id);
    return NextResponse.json({ error: "Adresse manquante" }, { status: 500 });
  }

  const items = decodeCart(session.metadata).flatMap((line) => {
    const found = findVariant(line.productId, line.size);
    if (!found || !isOrderable(found.variant)) {
      console.error(`Article non relié à Printful ignoré (${line.productId} ${line.size}) — session ${session.id}`);
      return [];
    }
    return [{
      sync_variant_id: found.variant.printfulSyncVariantId,
      quantity: line.quantity,
      retail_price: (found.product.priceCents / 100).toFixed(2),
    }];
  });

  if (items.length === 0) {
    console.error("Panier vide/illisible pour la session", session.id);
    return NextResponse.json({ error: "Panier illisible" }, { status: 500 });
  }

  try {
    const order = await createOrder({
      externalId: session.id,
      confirm: process.env.PRINTFUL_AUTO_CONFIRM === "true",
      recipient: {
        name: shipping.name ?? session.customer_details?.name ?? "Client DAYTHO 987",
        address1: address.line1,
        address2: address.line2 ?? undefined,
        city: address.city,
        state_code: address.state ?? undefined,
        country_code: address.country,
        zip: address.postal_code ?? "",
        email: session.customer_details?.email ?? undefined,
        phone: session.customer_details?.phone ?? undefined,
      },
      items,
    });
    console.log(`Commande Printful ${order.id} (${order.status}) créée pour ${session.id}`);
    return NextResponse.json({ received: true, printfulOrderId: order.id });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    // Webhook rejoué : la commande existe déjà chez Printful (external_id identique).
    if (/external.?id/i.test(message) && /already|exist|used/i.test(message)) {
      return NextResponse.json({ received: true, duplicate: true });
    }
    console.error("Erreur création commande Printful:", message);
    // 500 → Stripe renverra le webhook plus tard.
    return NextResponse.json({ error: "Échec Printful" }, { status: 500 });
  }
}
