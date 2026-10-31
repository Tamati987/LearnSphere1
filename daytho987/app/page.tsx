import { ArrowRight, Zap } from "lucide-react";
import { products } from "@/lib/products";
import { ProductCard } from "@/components/ProductCard";

export default function Home() {
  return (
    <div className="relative">
      {/* Hero Section */}
      <section className="relative h-[90vh] flex flex-col justify-center items-center text-center px-4 overflow-hidden border-b border-daytho-red/20 scanlines">
        <div className="absolute inset-0 bg-[url('/image5.jpg')] bg-cover bg-center opacity-30 mix-blend-luminosity z-0"></div>
        <div className="absolute inset-0 bg-gradient-to-t from-daytho-black via-transparent to-daytho-black z-0"></div>

        <div className="z-10 relative max-w-4xl">
          <div className="flex items-center justify-center gap-2 mb-4 text-daytho-red font-bold tracking-widest text-sm animate-pulse">
            <Zap size={16} /> NOUVELLE COLLECTION DISPONIBLE <Zap size={16} />
          </div>
          <h2 className="font-display text-6xl md:text-8xl lg:text-9xl font-bold uppercase tracking-tighter mb-6 leading-none">
            L&apos;ART DU <br/>
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-daytho-red to-red-800">
              COMBAT
            </span>
          </h2>
          <p className="text-lg md:text-xl text-gray-300 max-w-2xl mx-auto mb-10 font-light">
            Fusion de l&apos;esthétique Manga Cyberpunk et de l&apos;esprit de Fenua. Des pièces exclusives par DAYTHO 987, imprimées à la demande.
          </p>
          <a href="#shop" className="group relative inline-flex items-center gap-4 bg-daytho-red text-white font-bold py-4 px-10 uppercase tracking-widest overflow-hidden transition-all hover:bg-red-700 border border-daytho-red">
            <span className="relative z-10">Explorer le Drop</span>
            <ArrowRight className="relative z-10 group-hover:translate-x-2 transition-transform" />
            <div className="absolute inset-0 bg-white/20 translate-y-full group-hover:translate-y-0 transition-transform duration-300"></div>
          </a>
        </div>
      </section>

      {/* Product Grid */}
      <section id="shop" className="py-24 px-4 max-w-7xl mx-auto relative z-10">
        <div className="flex justify-between items-end mb-12 border-b border-daytho-gray pb-4">
          <h3 className="font-display text-4xl md:text-5xl uppercase border-l-4 border-daytho-red pl-4">
            Derniers <span className="text-daytho-red">Drops</span>
          </h3>
          <span className="text-sm text-gray-500 font-mono hidden md:block">[ DAYTHO.SYSTEM.ONLINE ]</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {products.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      </section>

      {/* Section Manifesto */}
      <section className="py-20 bg-daytho-red/5 border-y border-daytho-red/20">
        <div className="max-w-4xl mx-auto text-center px-4">
          <h3 className="font-display text-3xl mb-6 uppercase tracking-widest">Imprimé à la demande. Livré avec honneur.</h3>
          <p className="text-gray-400 text-lg leading-relaxed">
            Chaque pièce DAYTHO 987 est produite de manière éthique et durable par notre partenaire Printful.
            Aucun stock inutile. Juste de l&apos;art, du style et une qualité d&apos;impression irréprochable.
          </p>
        </div>
      </section>
    </div>
  );
}
