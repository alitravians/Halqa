"use client";

import Link from "next/link";
import { useState } from "react";
import { Menu, X } from "lucide-react";
import { Logo } from "@/components/ui/logo";
import { Button } from "@/components/ui/button";
import { ROUTES } from "@/lib/brand";
import { cn } from "@/lib/utils";

const NAV_LINKS = [
  { href: ROUTES.feed, label: "الاستكشاف" },
  { href: ROUTES.arena, label: "الساحة" },
  { href: "#features", label: "المميزات" },
  { href: "#pricing", label: "الأسعار" },
];

export function Header() {
  const [open, setOpen] = useState(false);
  return (
    <header className="fixed top-0 inset-x-0 z-40 glass border-b border-white/5">
      <div className="container mx-auto max-w-7xl px-4 sm:px-6 h-16 flex items-center justify-between">
        <Link href={ROUTES.home} className="shrink-0">
          <Logo />
        </Link>

        <nav className="hidden md:flex items-center gap-8">
          {NAV_LINKS.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              className="text-sm text-white/70 hover:text-white transition-colors"
            >
              {l.label}
            </Link>
          ))}
        </nav>

        <div className="hidden md:flex items-center gap-3">
          <Link href={ROUTES.signin}>
            <Button variant="ghost" size="sm">
              تسجيل دخول
            </Button>
          </Link>
          <Link href={ROUTES.signup}>
            <Button variant="primary" size="sm">
              ابدأ مجاناً
            </Button>
          </Link>
        </div>

        <button
          className="md:hidden inline-flex items-center justify-center w-10 h-10 rounded-lg bg-white/5 border border-white/10"
          onClick={() => setOpen((s) => !s)}
          aria-label="Toggle menu"
        >
          {open ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      <div
        className={cn(
          "md:hidden overflow-hidden transition-all duration-300",
          open ? "max-h-96" : "max-h-0",
        )}
      >
        <div className="px-4 py-4 space-y-3 border-t border-white/5">
          {NAV_LINKS.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              onClick={() => setOpen(false)}
              className="block text-white/80 hover:text-white py-2"
            >
              {l.label}
            </Link>
          ))}
          <div className="flex gap-3 pt-3">
            <Link href={ROUTES.signin} className="flex-1">
              <Button variant="ghost" size="sm" className="w-full">
                تسجيل دخول
              </Button>
            </Link>
            <Link href={ROUTES.signup} className="flex-1">
              <Button variant="primary" size="sm" className="w-full">
                ابدأ مجاناً
              </Button>
            </Link>
          </div>
        </div>
      </div>
    </header>
  );
}
