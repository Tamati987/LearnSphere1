// Catalogue DAYTHO 987 — source de vérité unique pour les prix.
// Le serveur recalcule TOUJOURS les montants à partir de ce fichier :
// le navigateur n'envoie que des identifiants, jamais des prix.
//
// `printfulSyncVariantId` : identifiant de la variante dans TON store Printful
// (produit déjà créé avec le design). Pour les retrouver, appelle
//   GET /api/admin/printful-products   (en-tête Authorization: Bearer <ADMIN_TOKEN>)
// et recopie les "sync_variant_id" ici. Tant qu'il vaut 0, la variante est
// affichée mais ne peut pas être commandée ("Bientôt disponible").

export type Variant = {
  size: string;
  printfulSyncVariantId: number;
};

export type Collection = "garcons" | "filles";

export type Product = {
  id: string;
  name: string;
  collection: Collection;
  /** Prix de vente en centimes d'euro */
  priceCents: number;
  image: string;
  variants: Variant[];
};

export const collections: { id: Collection; title: string; accent: string; tag: string }[] = [
  { id: "garcons", title: "Garçons", accent: "Shadow", tag: "[ DEPT.987 / UNIT.G ]" },
  { id: "filles", title: "Filles", accent: "Samurai", tag: "[ DEPT.987 / UNIT.F ]" },
];

const sizes = (list: string[], ids: Record<string, number> = {}): Variant[] =>
  list.map((size) => ({ size, printfulSyncVariantId: ids[size] ?? 0 }));

const TOPS = ["S", "M", "L", "XL", "XXL"];
const PANTS = ["S", "M", "L", "XL"];
const POSTERS = ["30x40 cm", "45x60 cm"];

// Raccourci pour les t-shirts graphiques (un design = un t-shirt).
// Pour activer une taille : tee(..., { S: 123456, M: 123457, ... })
const tee = (id: string, name: string, collection: Collection, image: string, ids?: Record<string, number>): Product => ({
  id,
  name,
  collection,
  priceCents: 3500,
  image,
  variants: sizes(TOPS, ids),
});

export const products: Product[] = [
  // ─── GARÇONS ────────────────────────────────────────────────────────
  tee("g-backpack-ronin", "Backpack Ronin Tee", "garcons", "/garcons/g01.jpg"),
  tee("g-shadow-walk", "Shadow Walk Tee", "garcons", "/garcons/g02.jpg"),
  tee("g-wall-lean", "Wall Lean Tee", "garcons", "/garcons/g03.jpg"),
  tee("g-red-eye", "Red Eye Tee", "garcons", "/garcons/g04.jpg"),
  tee("g-wall-climb", "Wall Climb Tee", "garcons", "/garcons/g05.jpg"),
  tee("g-edge-grip", "Edge Grip Tee", "garcons", "/garcons/g06.jpg"),
  tee("g-ledge-sit", "Ledge Sit Tee", "garcons", "/garcons/g07.jpg"),
  tee("g-hang-tight", "Hang Tight Tee", "garcons", "/garcons/g08.jpg"),
  tee("g-off-duty", "Off Duty Tee", "garcons", "/garcons/g09.jpg"),
  tee("g-impact-kick", "Impact Kick Tee", "garcons", "/garcons/g10.jpg"),
  tee("g-one-hand", "One Hand Tee", "garcons", "/garcons/g11.jpg"),
  tee("g-upside-down", "Upside Down Tee", "garcons", "/garcons/g12.jpg"),
  tee("g-tightrope", "Tightrope Tee", "garcons", "/garcons/g13.jpg"),

  // ─── FILLES ─────────────────────────────────────────────────────────
  {
    id: "cyber-samurai-hoodie",
    name: "Cyber Samurai Hoodie",
    collection: "filles",
    priceCents: 7500,
    image: "/image1.jpg",
    variants: sizes(TOPS),
  },
  {
    id: "red-mask-tshirt",
    name: "Red Mask T-Shirt",
    collection: "filles",
    priceCents: 3500,
    image: "/image2.jpg",
    variants: sizes(TOPS),
  },
  {
    id: "neon-zen-poster",
    name: "Neon Zen Poster",
    collection: "filles",
    priceCents: 2500,
    image: "/image3.jpg",
    variants: sizes(POSTERS),
  },
  {
    id: "street-cargo-pants",
    name: "Street Cargo Pants",
    collection: "filles",
    priceCents: 8500,
    image: "/image4.jpg",
    variants: sizes(PANTS),
  },
  tee("f-air-kick", "Air Kick Tee", "filles", "/image5.jpg"),
  tee("f-breaker", "Breaker Tee", "filles", "/image6.jpg"),
  tee("f-ledge-queen", "Ledge Queen Tee", "filles", "/filles/f01.jpg"),
  tee("f-peace-sign", "Peace Sign Tee", "filles", "/filles/f02.jpg"),
  tee("f-low-stance", "Low Stance Tee", "filles", "/filles/f03.jpg"),
];

export function findVariant(productId: string, size: string) {
  const product = products.find((p) => p.id === productId);
  const variant = product?.variants.find((v) => v.size === size);
  return product && variant ? { product, variant } : null;
}

export const isOrderable = (v: Variant) => v.printfulSyncVariantId > 0;

export const formatPrice = (cents: number) =>
  (cents / 100).toLocaleString("fr-FR", { style: "currency", currency: "EUR" });
