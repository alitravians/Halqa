import { cn } from "@/lib/utils";

export function Logo({
  size = 32,
  showText = true,
  className,
}: {
  size?: number;
  showText?: boolean;
  className?: string;
}) {
  return (
    <div className={cn("flex items-center gap-2.5", className)}>
      <svg
        width={size}
        height={size}
        viewBox="0 0 40 40"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-label="Halqa logo"
      >
        <defs>
          <linearGradient id="halqa-grad" x1="0" y1="0" x2="40" y2="40">
            <stop offset="0%" stopColor="#7C3AED" />
            <stop offset="50%" stopColor="#A855F7" />
            <stop offset="100%" stopColor="#EC4899" />
          </linearGradient>
        </defs>
        {[0, 60, 120, 180, 240, 300].map((angle) => {
          const rad = (angle * Math.PI) / 180;
          const cx = 20 + Math.cos(rad) * 11;
          const cy = 20 + Math.sin(rad) * 11;
          return <circle key={angle} cx={cx} cy={cy} r="3.5" fill="url(#halqa-grad)" />;
        })}
        <circle cx="20" cy="20" r="5" fill="#F59E0B" />
      </svg>
      {showText && (
        <span
          className="text-xl font-bold tracking-tight"
          style={{ fontFamily: "var(--font-display)" }}
        >
          <span className="gradient-brand-text">حلقة</span>
        </span>
      )}
    </div>
  );
}
