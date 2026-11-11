/** Logo SmartFood : un coffre a compartiments avec sa serrure, dans un carre orange. */
export function LogoMark({ size = 36 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <defs>
        <linearGradient id="sf-logo" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#ff8a3d" /><stop offset="1" stopColor="#e04e0b" /></linearGradient>
      </defs>
      <rect width="64" height="64" rx="16" fill="url(#sf-logo)" />
      <rect x="13" y="15" width="38" height="34" rx="7" fill="#fff" />
      <path d="M32 15v34M13 32h38" stroke="#e04e0b" strokeWidth="3" />
      <circle cx="41.5" cy="40.5" r="4" fill="#14201b" />
      <rect x="40.2" y="41" width="2.6" height="5.5" rx="1.3" fill="#14201b" />
      <circle cx="22.5" cy="23.5" r="2.2" fill="#17995a" />
    </svg>
  );
}

export function Logo({ light = false }: { light?: boolean }) {
  return (
    <span className="flex items-center gap-2.5">
      <LogoMark />
      <span className="leading-none">
        <span className={`block text-[17px] font-black tracking-tight ${light ? 'text-white' : 'text-ink-900'}`}>Smart<span className="text-brand-500">Food</span></span>
        <span className={`block text-[9px] font-semibold uppercase tracking-[.22em] ${light ? 'text-ink-300' : 'text-ink-500'}`}>Delivery</span>
      </span>
    </span>
  );
}
