import Link from "next/link";
import { Logo } from "@/components/ui/logo";
import { ROUTES } from "@/lib/brand";

export function Footer() {
  return (
    <footer className="mt-32 border-t border-white/5">
      <div className="container mx-auto max-w-7xl px-4 sm:px-6 py-12">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
          <div className="col-span-2">
            <Logo />
            <p className="mt-4 text-sm text-white/60 max-w-xs">
              تطبيق البث المباشر العربي. حلقتك تبدأ هنا.
            </p>
          </div>
          <div>
            <h4 className="text-white font-semibold mb-3">المنصة</h4>
            <ul className="space-y-2 text-sm text-white/60">
              <li><Link href={ROUTES.feed} className="hover:text-white">الاستكشاف</Link></li>
              <li><Link href={ROUTES.arena} className="hover:text-white">الساحة</Link></li>
              <li><Link href={ROUTES.goLive} className="hover:text-white">ابدأ بث</Link></li>
            </ul>
          </div>
          <div>
            <h4 className="text-white font-semibold mb-3">قانوني</h4>
            <ul className="space-y-2 text-sm text-white/60">
              <li><Link href={ROUTES.terms} className="hover:text-white">الشروط</Link></li>
              <li><Link href={ROUTES.privacy} className="hover:text-white">الخصوصية</Link></li>
              <li><Link href={ROUTES.community} className="hover:text-white">قواعد المجتمع</Link></li>
            </ul>
          </div>
        </div>
        <div className="mt-12 pt-6 border-t border-white/5 text-center text-xs text-white/40">
          © {new Date().getFullYear()} Halqa · حلقة. جميع الحقوق محفوظة.
        </div>
      </div>
    </footer>
  );
}
