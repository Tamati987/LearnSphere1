"use client";

import Image from "next/image";
import { useState } from "react";
import { useCart } from "./CartProvider";
import { formatPrice, isOrderable, type Product } from "@/lib/products";

export function ProductCard({ product }: { product: Product }) {
  const { add } = useCart();
  const [size, setSize] = useState(product.variants[0]?.size ?? "");
  const variant = product.variants.find((v) => v.size === size);
  const available = variant ? isOrderable(variant) : false;

  return (
    <div className="group relative border border-daytho-gray hover:border-daytho-red transition-all duration-300 bg-black/40 flex flex-col">
      <div className="aspect-[3/4] relative overflow-hidden bg-daytho-gray">
        <Image
          src={product.image}
          alt={product.name}
          fill
          sizes="(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 25vw"
          className="object-cover group-hover:scale-105 transition-transform duration-700 ease-out"
        />
      </div>
      <div className="p-4 flex flex-col gap-3 mt-auto">
        <div>
          <h4 className="font-bold text-lg leading-tight mb-1">{product.name}</h4>
          <p className="text-daytho-red font-display text-xl">{formatPrice(product.priceCents)}</p>
        </div>
        <div className="flex flex-wrap gap-1" role="radiogroup" aria-label="Taille">
          {product.variants.map((v) => (
            <button
              key={v.size}
              role="radio"
              aria-checked={v.size === size}
              onClick={() => setSize(v.size)}
              className={`px-2 py-1 text-xs font-mono border transition-colors ${
                v.size === size ? "border-daytho-red text-daytho-red" : "border-daytho-gray text-gray-400 hover:border-gray-500"
              }`}
            >
              {v.size}
            </button>
          ))}
        </div>
        <button
          onClick={() => add({ productId: product.id, size })}
          disabled={!available}
          className="w-full bg-daytho-red text-white font-bold py-3 text-sm tracking-wider uppercase hover:bg-white hover:text-black transition-colors disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-daytho-red disabled:hover:text-white"
        >
          {available ? "Ajouter au panier" : "Bientôt disponible"}
        </button>
      </div>
    </div>
  );
}
