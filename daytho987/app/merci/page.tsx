import Link from "next/link";
import { ClearCart } from "./ClearCart";

export const metadata = { title: "Merci | DAYTHO 987" };

export default function MerciPage() {
  return (
    <section className="min-h-[70vh] flex flex-col items-center justify-center text-center px-4 scanlines relative">
      <ClearCart />
      <p className="text-daytho-red font-mono text-sm tracking-widest mb-4">[ PAIEMENT.CONFIRMÉ ]</p>
      <h2 className="font-display text-5xl md:text-7xl uppercase mb-6">
        Merci <span className="text-daytho-red">guerrier</span>
      </h2>
      <p className="text-gray-300 max-w-xl mb-10">
        Ta commande DAYTHO 987 est validée. Elle part en impression chez Printful ; tu recevras un e-mail de
        confirmation puis le suivi de livraison.
      </p>
      <Link href="/" className="bg-daytho-red text-white font-bold py-4 px-10 uppercase tracking-widest hover:bg-red-700 relative z-10">
        Retour à la boutique
      </Link>
    </section>
  );
}
