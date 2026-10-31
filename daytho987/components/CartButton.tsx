"use client";

import { ShoppingCart } from "lucide-react";
import { useCart } from "./CartProvider";

export function CartButton() {
  const { count, open } = useCart();
  return (
    <button onClick={open} aria-label="Ouvrir le panier" className="flex items-center gap-2 hover:text-daytho-red transition-colors">
      <ShoppingCart size={20} />
      <span className="text-xs font-bold">{count}</span>
    </button>
  );
}
