"use client";

import Image from "next/image";
import { useState } from "react";
import { X, Minus, Plus, AlertCircle } from "lucide-react";
import { useCart } from "./CartProvider";
import { findVariant, formatPrice } from "@/lib/products";

export function CartDrawer() {
  const { items, isOpen, close, setQuantity } = useCart();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const lines = items.flatMap((item) => {
    const found = findVariant(item.productId, item.size);
    return found ? [{ ...item, ...found }] : [];
  });
  const total = lines.reduce((sum, l) => sum + l.product.priceCents * l.quantity, 0);

  const checkout = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch("/api/checkout", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ items }),
      });
      const data = await res.json();
      if (data.url) {
        window.location.href = data.url; // redirection vers la page de paiement Stripe
        return;
      }
      setError(data.error || "Une erreur est survenue.");
    } catch {
      setError("Erreur de connexion au serveur.");
    }
    setLoading(false);
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[60]">
      <div className="absolute inset-0 bg-black/70" onClick={close} />
      <aside className="absolute right-0 top-0 h-full w-full max-w-md bg-daytho-black border-l border-daytho-red/30 flex flex-col">
        <div className="flex justify-between items-center p-4 border-b border-daytho-gray">
          <h2 className="font-display text-2xl uppercase tracking-widest">
            Panier <span className="text-daytho-red">[{lines.length}]</span>
          </h2>
          <button onClick={close} aria-label="Fermer" className="hover:text-daytho-red"><X /></button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {lines.length === 0 && <p className="text-gray-500 font-mono text-sm">[ PANIER.VIDE ]</p>}
          {lines.map((l) => (
            <div key={`${l.productId}-${l.size}`} className="flex gap-4 border border-daytho-gray p-2">
              <div className="relative w-20 aspect-[3/4] shrink-0 bg-daytho-gray">
                <Image src={l.product.image} alt={l.product.name} fill sizes="80px" className="object-cover" />
              </div>
              <div className="flex-1">
                <p className="font-bold leading-tight">{l.product.name}</p>
                <p className="text-xs text-gray-400 font-mono">TAILLE : {l.size}</p>
                <p className="text-daytho-red font-display text-lg">{formatPrice(l.product.priceCents)}</p>
                <div className="flex items-center gap-3 mt-1">
                  <button onClick={() => setQuantity(l.productId, l.size, l.quantity - 1)} aria-label="Retirer un" className="border border-daytho-gray p-1 hover:border-daytho-red"><Minus size={14} /></button>
                  <span className="font-mono text-sm">{l.quantity}</span>
                  <button onClick={() => setQuantity(l.productId, l.size, l.quantity + 1)} aria-label="Ajouter un" className="border border-daytho-gray p-1 hover:border-daytho-red"><Plus size={14} /></button>
                </div>
              </div>
            </div>
          ))}
        </div>

        <div className="p-4 border-t border-daytho-gray space-y-3">
          {error && (
            <p className="flex items-center gap-2 bg-red-900 text-white text-sm p-3"><AlertCircle size={16} /> {error}</p>
          )}
          <div className="flex justify-between font-display text-xl uppercase">
            <span>Total</span>
            <span className="text-daytho-red">{formatPrice(total)}</span>
          </div>
          <p className="text-xs text-gray-500">Livraison et adresse demandées à l&apos;étape suivante (paiement sécurisé Stripe).</p>
          <button
            onClick={checkout}
            disabled={lines.length === 0 || loading}
            className="w-full bg-daytho-red text-white font-bold py-4 uppercase tracking-widest hover:bg-white hover:text-black transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? "Redirection..." : "Payer"}
          </button>
        </div>
      </aside>
    </div>
  );
}
