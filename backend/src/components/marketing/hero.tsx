"use client";

import { motion } from "framer-motion";
import Link from "next/link";
import { ArrowLeft, Play, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ROUTES } from "@/lib/brand";

export function Hero() {
  return (
    <section className="relative pt-12 pb-24 overflow-hidden">
      <BackgroundOrbs />

      <div className="container mx-auto max-w-7xl px-4 sm:px-6 relative">
        <div className="grid lg:grid-cols-2 gap-12 items-center">
          <div>
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6 }}
              className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full glass border border-white/10 text-sm text-white/80 mb-6"
            >
              <Sparkles size={14} className="text-amber-400" />
              <span>منصة البث العربية الجديدة</span>
            </motion.div>

            <motion.h1
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.1 }}
              className="text-5xl sm:text-7xl font-extrabold leading-[1.05] tracking-tight mb-6 text-balance"
              style={{ fontFamily: "var(--font-display)" }}
            >
              <span className="block">حلقتك</span>
              <span className="gradient-brand-text">تبدأ هنا.</span>
            </motion.h1>

            <motion.p
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.2 }}
              className="text-lg sm:text-xl text-white/70 max-w-xl mb-8 leading-relaxed"
            >
              تطبيق البث المباشر العربي. ابثّ مباشرة، تنافس في{" "}
              <span className="gradient-brand-text font-semibold">PK Arena</span>{" "}
              المبتكرة، استلم الهدايا واكسب من جمهورك.
            </motion.p>

            <motion.div
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.3 }}
              className="flex flex-col sm:flex-row gap-3 mb-10"
            >
              <Link href={ROUTES.signup}>
                <Button variant="primary" size="lg" className="w-full sm:w-auto">
                  ابدأ مجاناً
                  <ArrowLeft size={20} />
                </Button>
              </Link>
              <Link href={ROUTES.feed}>
                <Button variant="ghost" size="lg" className="w-full sm:w-auto">
                  <Play size={18} className="fill-current" />
                  شاهد البث الآن
                </Button>
              </Link>
            </motion.div>

            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.7, delay: 0.5 }}
              className="flex items-center gap-6 text-sm text-white/60"
            >
              <div>
                <div className="text-2xl font-bold text-white">+100K</div>
                <div>مشاهد نشط</div>
              </div>
              <div className="w-px h-10 bg-white/10" />
              <div>
                <div className="text-2xl font-bold text-white">+5K</div>
                <div>مذيع</div>
              </div>
              <div className="w-px h-10 bg-white/10" />
              <div>
                <div className="text-2xl font-bold text-white">+50</div>
                <div>دولة عربية</div>
              </div>
            </motion.div>
          </div>

          <HeroVisual />
        </div>
      </div>
    </section>
  );
}

function HeroVisual() {
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.8, delay: 0.2 }}
      className="relative aspect-[3/4] sm:aspect-[4/5] max-w-md mx-auto w-full"
    >
      <div className="absolute inset-0 rounded-3xl overflow-hidden glass-strong border-white/10">
        <div
          className="absolute inset-0"
          style={{
            background:
              "linear-gradient(135deg, rgba(124,58,237,0.4) 0%, rgba(236,72,153,0.4) 100%)",
          }}
        />
        <div
          className="absolute inset-0 opacity-30"
          style={{
            backgroundImage:
              "radial-gradient(circle at 50% 30%, rgba(255,255,255,0.3), transparent 60%)",
          }}
        />

        <div className="absolute top-4 right-4 left-4 flex items-center justify-between z-10">
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-red-500 text-white text-xs font-bold">
              <span className="w-1.5 h-1.5 rounded-full bg-white animate-pulse" />
              مباشر
            </span>
            <span className="text-xs text-white/90 bg-black/40 px-2 py-1 rounded-md">
              12.4K مشاهد
            </span>
          </div>
          <div className="text-xs text-white/90 bg-black/40 px-2 py-1 rounded-md">
            #ترفيه
          </div>
        </div>

        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 z-10">
          <div className="relative">
            <div className="absolute inset-0 rounded-full bg-pink-500/40 blur-2xl animate-pulse" />
            <div className="relative w-28 h-28 rounded-full gradient-brand p-1">
              <div className="w-full h-full rounded-full bg-zinc-900 flex items-center justify-center text-3xl">
                🎤
              </div>
            </div>
          </div>
        </div>

        <div className="absolute bottom-4 left-4 right-4 z-10">
          <div className="glass rounded-2xl p-3 mb-3">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-7 h-7 rounded-full gradient-brand flex items-center justify-center text-xs font-bold">
                ع
              </div>
              <div>
                <div className="text-sm font-semibold">عبدالله الفنان</div>
                <div className="text-xs text-amber-400">⭐ مذيع VIP</div>
              </div>
            </div>
          </div>

          <div className="space-y-1.5">
            <ChatBubble user="فاطمة" message="بث رهيب 🔥" />
            <ChatBubble user="سعد" message="ابدع كالعادة" />
            <ChatBubble user="ليلى" message="❤️❤️❤️" />
          </div>
        </div>

        <FloatingGifts />
      </div>

      <div className="absolute -top-8 -right-8 w-24 h-24 rounded-full bg-pink-500/20 blur-3xl" />
      <div className="absolute -bottom-8 -left-8 w-32 h-32 rounded-full bg-violet-500/30 blur-3xl" />
    </motion.div>
  );
}

function ChatBubble({ user, message }: { user: string; message: string }) {
  return (
    <div className="flex items-start gap-2 text-sm bg-black/30 backdrop-blur-md rounded-xl px-3 py-1.5 max-w-fit">
      <span className="text-amber-300 font-semibold">{user}:</span>
      <span className="text-white/90">{message}</span>
    </div>
  );
}

function FloatingGifts() {
  const items = [
    { emoji: "🌹", delay: 0, x: "20%" },
    { emoji: "💎", delay: 0.7, x: "60%" },
    { emoji: "🦁", delay: 1.4, x: "40%" },
    { emoji: "🚗", delay: 2.1, x: "75%" },
    { emoji: "👑", delay: 2.8, x: "30%" },
  ];
  return (
    <>
      {items.map((it, i) => (
        <motion.div
          key={i}
          initial={{ y: 100, opacity: 0, scale: 0.8 }}
          animate={{
            y: -300,
            opacity: [0, 1, 1, 0],
            scale: [0.8, 1.2, 1, 0.9],
          }}
          transition={{
            duration: 3.5,
            delay: it.delay,
            repeat: Infinity,
            repeatDelay: 1,
            ease: "easeOut",
          }}
          className="absolute bottom-0 text-4xl pointer-events-none"
          style={{ left: it.x }}
        >
          {it.emoji}
        </motion.div>
      ))}
    </>
  );
}

function BackgroundOrbs() {
  return (
    <div aria-hidden className="absolute inset-0 -z-10 pointer-events-none">
      <div className="absolute top-20 right-1/3 w-72 h-72 bg-violet-500/10 rounded-full blur-3xl" />
      <div className="absolute bottom-20 left-1/4 w-96 h-96 bg-pink-500/10 rounded-full blur-3xl" />
      <div
        className="absolute inset-0 opacity-[0.03]"
        style={{
          backgroundImage:
            "radial-gradient(circle at 1px 1px, white 1px, transparent 0)",
          backgroundSize: "32px 32px",
        }}
      />
    </div>
  );
}
