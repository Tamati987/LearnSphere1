"use client";

import { useEffect } from "react";
import { useCart } from "@/components/CartProvider";

// Vide le panier une fois le paiement confirmé.
export function ClearCart() {
  const { clear } = useCart();
  useEffect(() => {
    // Effacer aussi le stockage : cet effet s'exécute avant le chargement
    // du panier par CartProvider, qui sinon le restaurerait.
    try {
      localStorage.removeItem("daytho-cart");
    } catch {}
    clear();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return null;
}
