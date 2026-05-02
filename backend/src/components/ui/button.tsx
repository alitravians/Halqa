"use client";

import { forwardRef, type ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

type Variant = "primary" | "gold" | "ghost" | "outline" | "danger";
type Size = "sm" | "md" | "lg" | "xl";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
}

const variantClasses: Record<Variant, string> = {
  primary:
    "gradient-brand text-white hover:brightness-110 active:brightness-95 glow-brand",
  gold:
    "gradient-gold text-zinc-900 font-bold hover:brightness-110 active:brightness-95 glow-gold",
  ghost: "bg-white/5 text-white hover:bg-white/10 border border-white/10",
  outline:
    "bg-transparent text-white hover:bg-white/5 border border-white/20",
  danger: "bg-red-500/90 text-white hover:bg-red-500",
};

const sizeClasses: Record<Size, string> = {
  sm: "h-9 px-4 text-sm rounded-xl",
  md: "h-11 px-6 text-base rounded-xl",
  lg: "h-13 px-8 text-lg rounded-2xl",
  xl: "h-15 px-10 text-xl rounded-2xl",
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    { variant = "primary", size = "md", loading, className, children, disabled, ...rest },
    ref,
  ) => (
    <button
      ref={ref}
      disabled={loading || disabled}
      className={cn(
        "relative inline-flex items-center justify-center gap-2 font-semibold transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed select-none",
        sizeClasses[size],
        variantClasses[variant],
        className,
      )}
      {...rest}
    >
      {loading ? (
        <span className="inline-block w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />
      ) : null}
      {children}
    </button>
  ),
);
Button.displayName = "Button";
