import Link from "next/link";
import { Sparkles, ArrowLeft } from "lucide-react";
import { Header } from "@/components/layout/header";
import { Footer } from "@/components/layout/footer";
import { Button } from "@/components/ui/button";
import { ROUTES } from "@/lib/brand";
import { Hero } from "@/components/marketing/hero";

export default function HomePage() {
  return (
    <div className="min-h-screen flex flex-col">
      <Header />

      <main className="flex-1 pt-16">
        <Hero />

        <section className="container mx-auto max-w-7xl px-4 sm:px-6 py-24 text-center">
          <div className="glass-strong rounded-3xl p-10 sm:p-16 relative overflow-hidden">
            <div
              aria-hidden
              className="absolute inset-0 -z-10 opacity-30"
              style={{
                background:
                  "radial-gradient(circle at 30% 30%, rgba(124,58,237,0.4), transparent 50%), radial-gradient(circle at 70% 70%, rgba(236,72,153,0.4), transparent 50%)",
              }}
            />
            <Sparkles className="mx-auto text-amber-400 mb-4" size={40} />
            <h2 className="text-3xl sm:text-5xl font-bold text-balance mb-4">
              <span className="gradient-brand-text">حلقتك تبدأ هنا</span>
            </h2>
            <p className="text-white/70 text-lg max-w-2xl mx-auto mb-8">
              تطبيق Halqa Android متاح قريباً للتحميل من Google Play. اربح، تنافس، اشتهر — كل شي في مكان واحد.
            </p>
            <div className="flex flex-col sm:flex-row gap-3 justify-center">
              <Link href={ROUTES.signup}>
                <Button variant="primary" size="lg" className="w-full sm:w-auto">
                  ابدأ مجاناً
                  <ArrowLeft size={20} />
                </Button>
              </Link>
              <Link href={ROUTES.feed}>
                <Button variant="outline" size="lg" className="w-full sm:w-auto">
                  استكشف الحلقات
                </Button>
              </Link>
            </div>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}
