// Catalogue DAYTHO 987 — source de vérité unique pour les prix.
// Le serveur recalcule TOUJOURS les montants à partir de ce fichier :
// le navigateur n'envoie que des identifiants, jamais des prix.
//
// `printfulSyncVariantId` : identifiant de la variante dans TON store Printful
// (produit déjà créé avec le design). Pour les retrouver, appelle
//   GET /api/admin/printful-products   (en-tête Authorization: Bearer <ADMIN_TOKEN>)
// et recopie les "sync_variant_id" ici. Tant qu'il vaut 0, la variante est
// affichée mais ne peut pas être commandée.

export type Variant = {
  size: string;
  printfulSyncVariantId: number;
};

export type Product = {
  id: string;
  name: string;
  /** Prix de vente en centimes d'euro */
  priceCents: number;
  image: string;
  variants: Variant[];
};

const apparelSizes = (ids: Record<string, number>): Variant[] =>
  Object.entries(ids).map(([size, printfulSyncVariantId]) => ({ size, printfulSyncVariantId }));

export const products: Product[] = [
  {
    id: "cyber-samurai-hoodie",
    name: "Cyber Samurai Hoodie",
    priceCents: 7500,
    image: "/image1.jpg",
    variants: apparelSizes({ S: 0, M: 0, L: 0, XL: 0, XXL: 0 }),
  },
  {
    id: "red-mask-tshirt",
    name: "Red Mask T-Shirt",
    priceCents: 3500,
    image: "/image2.jpg",
    variants: apparelSizes({ S: 0, M: 0, L: 0, XL: 0, XXL: 0 }),
  },
  {
    id: "neon-zen-poster",
    name: "Neon Zen Poster",
    priceCents: 2500,
    image: "/image3.jpg",
    variants: apparelSizes({ "30x40 cm": 0, "45x60 cm": 0 }),
  },
  {
    id: "street-cargo-pants",
    name: "Street Cargo Pants",
    priceCents: 8500,
    image: "/image4.jpg",
    variants: apparelSizes({ S: 0, M: 0, L: 0, XL: 0 }),
  },
];

export function findVariant(productId: string, size: string) {
  const product = products.find((p) => p.id === productId);
  const variant = product?.variants.find((v) => v.size === size);
  return product && variant ? { product, variant } : null;
}

export const isOrderable = (v: Variant) => v.printfulSyncVariantId > 0;

export const formatPrice = (cents: number) =>
  (cents / 100).toLocaleString("fr-FR", { style: "currency", currency: "EUR" });
