import type { Metadata } from "next";
import { Inter, Oswald } from "next/font/google";
import Image from "next/image";
import "./globals.css";
import { CartProvider } from "@/components/CartProvider";
import { CartButton } from "@/components/CartButton";
import { CartDrawer } from "@/components/CartDrawer";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });
const oswald = Oswald({ subsets: ["latin"], variable: "--font-oswald" });

export const metadata: Metadata = {
  title: "DAYTHO 987 | Streetwear Cyberpunk",
  description: "Boutique officielle DAYTHO 987. Vêtements et accessoires inspirés par l'univers manga et la culture polynésienne.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="fr">
      <body className={`${inter.variable} ${oswald.variable} font-sans bg-daytho-black text-daytho-white antialiased min-h-screen flex flex-col`}>
        <CartProvider>
        {/* Header */}
        <header className="border-b border-daytho-red/30 p-4 flex justify-between items-center sticky top-0 bg-daytho-black/90 backdrop-blur-md z-50">
          <a href="/" className="flex items-center">
            <h1>
              <span className="sr-only">DAYTHO 987</span>
              <Image src="/logo-daytho.png" alt="DAYTHO 987" width={1113} height={207} className="h-9 w-auto" priority />
            </h1>
          </a>
          <nav className="hidden md:flex space-x-8 text-sm font-semibold tracking-wider">
            <a href="/#shop" className="hover:text-daytho-red transition-colors">SHOP</a>
            <a href="/#garcons" className="hover:text-daytho-red transition-colors">GARÇONS</a>
            <a href="/#filles" className="hover:text-daytho-red transition-colors">FILLES</a>
            <a href="#" className="hover:text-daytho-red transition-colors">LOOKBOOK</a>
            <a href="#" className="hover:text-daytho-red transition-colors">CONTACT</a>
          </nav>
          <CartButton />
        </header>
        <CartDrawer />

        <main className="flex-grow">{children}</main>

        {/* Footer */}
        <footer className="border-t border-daytho-red/30 p-8 text-center text-xs text-gray-500 mt-20">
          <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-center gap-4">
            <p>&copy; {new Date().getFullYear()} DAYTHO 987. Tous droits réservés.</p>
            <div className="flex gap-4">
              <a href="#" className="hover:text-white transition-colors">Mentions Légales</a>
              <a href="#" className="hover:text-white transition-colors">CGV</a>
              <a href="#" className="hover:text-white transition-colors">Confidentialité</a>
            </div>
            <p>Impression &amp; Livraison par <span className="text-daytho-red font-bold">Printful</span>.</p>
          </div>
        </footer>
        </CartProvider>
      </body>
    </html>
  );
}
