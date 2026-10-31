"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

export type CartItem = { productId: string; size: string; quantity: number };

type CartContextValue = {
  items: CartItem[];
  count: number;
  isOpen: boolean;
  open: () => void;
  close: () => void;
  add: (item: Omit<CartItem, "quantity">) => void;
  setQuantity: (productId: string, size: string, quantity: number) => void;
  clear: () => void;
};

const CartContext = createContext<CartContextValue | null>(null);
const STORAGE_KEY = "daytho-cart";

export function CartProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<CartItem[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) setItems(JSON.parse(saved));
    } catch {}
    setLoaded(true);
  }, []);

  useEffect(() => {
    if (!loaded) return;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
    } catch {}
  }, [items, loaded]);

  const add: CartContextValue["add"] = ({ productId, size }) => {
    setItems((prev) => {
      const existing = prev.find((i) => i.productId === productId && i.size === size);
      if (existing) {
        return prev.map((i) => (i === existing ? { ...i, quantity: Math.min(i.quantity + 1, 10) } : i));
      }
      return [...prev, { productId, size, quantity: 1 }];
    });
    setIsOpen(true);
  };

  const setQuantity: CartContextValue["setQuantity"] = (productId, size, quantity) => {
    setItems((prev) =>
      quantity <= 0
        ? prev.filter((i) => !(i.productId === productId && i.size === size))
        : prev.map((i) => (i.productId === productId && i.size === size ? { ...i, quantity: Math.min(quantity, 10) } : i)),
    );
  };

  return (
    <CartContext.Provider
      value={{
        items,
        count: items.reduce((n, i) => n + i.quantity, 0),
        isOpen,
        open: () => setIsOpen(true),
        close: () => setIsOpen(false),
        add,
        setQuantity,
        clear: () => setItems([]),
      }}
    >
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error("useCart doit être utilisé dans <CartProvider>");
  return ctx;
}
