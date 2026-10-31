import { NextResponse } from "next/server";

export async function POST(request: Request) {
  try {
    const body = await request.json();

    // Vérification de sécurité basique
    if (!body.variantId || !body.imageUrl) {
      return NextResponse.json({ error: "Données de commande manquantes." }, { status: 400 });
    }

    if (!process.env.PRINTFUL_API_KEY || process.env.PRINTFUL_API_KEY === "ta_cle_secrete_ici") {
      return NextResponse.json({ error: "Clé API Printful non configurée (PRINTFUL_API_KEY)." }, { status: 500 });
    }

    // Construction de l'URL absolue pour Printful
    // On utilise l'en-tête 'host' pour s'adapter automatiquement à Railway ou localhost
    const host = request.headers.get("host");
    const protocol = host?.includes("localhost") ? "http" : "https";
    const absoluteImageUrl = `${protocol}://${host}${body.imageUrl}`;

    const recipient = {
      name: "Client DAYTHO 987",
      address1: "123 Rue de la Paix",
      city: "Paris",
      country_code: "FR",
      zip: "75000",
      email: "client@daytho987.com"
    };

    const orderData = {
      recipient: recipient,
      items: [
        {
          variant_id: body.variantId,
          quantity: 1,
          files: [
            {
              url: absoluteImageUrl
            }
          ]
        }
      ],
      retail_costs: {
        currency: "EUR",
        subtotal: "35.00",
        total: "35.00"
      }
    };

    const response = await fetch("https://api.printful.com/orders", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${process.env.PRINTFUL_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(orderData),
    });

    const data = await response.json();

    if (!response.ok) {
      console.error("Erreur Printful:", data);
      return NextResponse.json({ error: data.error?.message || "Erreur Printful" }, { status: response.status });
    }

    return NextResponse.json({ success: true, printfulOrder: data.result });

  } catch (error) {
    console.error("Erreur serveur:", error);
    return NextResponse.json({ error: "Erreur interne du serveur" }, { status: 500 });
  }
}
