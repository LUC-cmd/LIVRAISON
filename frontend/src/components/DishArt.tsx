/**
 * Illustrations originales de plats, affichees tant que le restaurant n'a pas televerse sa propre photo.
 * Le type d'illustration est deduit du nom du plat.
 */
type Kind = 'riz' | 'pate' | 'grill' | 'frites' | 'boisson' | 'dessert' | 'plat';

function kindOf(name: string): Kind {
  const n = name.toLowerCase();
  if (/(jus|bissap|gingembre|boisson|eau|soda|bi[eè]re)/.test(n)) return 'boisson';
  if (/(degue|dessert|yaourt|g[aâ]teau|glace|beignet)/.test(n)) return 'dessert';
  if (/(alloco|koliko|frite|plantain)/.test(n)) return 'frites';
  if (/(poulet|brochette|grill|brais|viande|poisson)/.test(n)) return 'grill';
  if (/(fufu|akoum|p[aâ]te|gboma|ablo|plateau)/.test(n)) return 'pate';
  if (/(riz|ayimolou|haricot|atti[eé]k|couscous)/.test(n)) return 'riz';
  return 'plat';
}

const bg: Record<Kind, [string, string]> = {
  riz: ['#fde9c9', '#f8c98a'], pate: ['#e3f1d9', '#b9dba0'], grill: ['#ffd9c2', '#f7a877'], frites: ['#fff0b8', '#f6cf5a'],
  boisson: ['#f9d0dc', '#ee93ad'], dessert: ['#e8e1fb', '#c2b2f1'], plat: ['#ffe6d5', '#fbb98d'],
};

function Plate({ children }: { children: React.ReactNode }) {
  return (
    <>
      <ellipse cx="60" cy="96" rx="44" ry="7" fill="#000" opacity=".12" />
      <circle cx="60" cy="60" r="42" fill="#fff" />
      <circle cx="60" cy="60" r="33" fill="#f4efe8" />
      {children}
    </>
  );
}

export function DishArt({ name, className = '' }: { name: string; className?: string }) {
  const kind = kindOf(name);
  const [c1, c2] = bg[kind];
  const id = `dish-${kind}`;
  return (
    <svg viewBox="0 0 120 120" className={className} role="img" aria-label={`Illustration : ${name}`} preserveAspectRatio="xMidYMid slice">
      <defs><linearGradient id={id} x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor={c1} /><stop offset="1" stopColor={c2} /></linearGradient></defs>
      <rect width="120" height="120" fill={`url(#${id})`} />
      {kind === 'riz' && (
        <Plate>
          <path d="M34 62a26 22 0 0 1 52 0z" fill="#fffdf7" stroke="#eadfcb" />
          {[40, 48, 56, 64, 72, 80].map((x, i) => <ellipse key={x} cx={x} cy={56 - (i % 2) * 5} rx="2.2" ry="1.1" fill="#e9dcc3" />)}
          <path d="M36 66q24 16 48 0q-6 14 -24 14t-24 -14z" fill="#8b3f1d" />
          {[46, 55, 64, 73].map((x) => <circle key={x} cx={x} cy="72" r="2.6" fill="#5b2710" />)}
          <path d="M70 44c8 -6 16 -2 16 5c-6 3 -12 2 -16 -5z" fill="#c9742c" />
        </Plate>
      )}
      {kind === 'pate' && (
        <Plate>
          <circle cx="48" cy="58" r="17" fill="#fbf3dc" stroke="#e6d6a8" />
          <path d="M38 52q10 -8 20 0" stroke="#efe2bb" strokeWidth="2" fill="none" />
          <path d="M62 50a17 17 0 1 1 0 22c8 -4 8 -18 0 -22z" fill="#3f7d32" />
          <circle cx="76" cy="56" r="3" fill="#c0392b" /><circle cx="80" cy="66" r="2.4" fill="#f1c40f" /><circle cx="72" cy="68" r="2.6" fill="#7b4a22" />
        </Plate>
      )}
      {kind === 'grill' && (
        <Plate>
          <path d="M40 66c-4 -14 8 -24 20 -22c14 2 22 12 18 24c-6 10 -30 12 -38 -2z" fill="#b5651d" />
          <path d="M46 54l22 16M52 48l22 16M42 62l18 12" stroke="#6e3610" strokeWidth="2.4" strokeLinecap="round" />
          <rect x="74" y="42" width="5" height="16" rx="2.5" fill="#f7f1e3" transform="rotate(35 76 50)" />
          <circle cx="42" cy="46" r="4" fill="#e74c3c" /><path d="M40 78q6 -5 12 0" stroke="#2e8b3d" strokeWidth="3" fill="none" strokeLinecap="round" />
        </Plate>
      )}
      {kind === 'frites' && (
        <Plate>
          {[[40, 52, 20], [46, 62, -15], [56, 50, 35], [60, 64, 5], [70, 54, -30], [74, 66, 25], [50, 72, 60], [66, 74, -50]].map(([x, y, r], i) => (
            <rect key={i} x={x} y={y} width="18" height="7" rx="3.5" fill={i % 2 ? '#e8a020' : '#f4c542'} stroke="#c9871a" strokeWidth=".8" transform={`rotate(${r} ${x + 9} ${y + 3.5})`} />
          ))}
        </Plate>
      )}
      {kind === 'boisson' && (
        <>
          <ellipse cx="60" cy="100" rx="26" ry="5" fill="#000" opacity=".12" />
          <path d="M42 30h36l-5 66a5 5 0 0 1 -5 4h-16a5 5 0 0 1 -5 -4z" fill="#fff" opacity=".85" />
          <path d="M44.5 46h31l-3.8 48a4 4 0 0 1 -4 3.6h-15.4a4 4 0 0 1 -4 -3.6z" fill="#b3123f" />
          <rect x="50" y="52" width="9" height="9" rx="2" fill="#fff" opacity=".45" transform="rotate(12 54 56)" /><rect x="61" y="62" width="8" height="8" rx="2" fill="#fff" opacity=".4" transform="rotate(-18 65 66)" />
          <path d="M66 30l8 -16" stroke="#17995a" strokeWidth="4" strokeLinecap="round" /><circle cx="78" cy="32" r="8" fill="#f6cf5a" stroke="#e0a91f" />
        </>
      )}
      {kind === 'dessert' && (
        <>
          <ellipse cx="60" cy="98" rx="30" ry="5" fill="#000" opacity=".12" />
          <path d="M32 54h56l-6 34a8 8 0 0 1 -8 7h-28a8 8 0 0 1 -8 -7z" fill="#fff" />
          <path d="M34 54q26 -22 52 0z" fill="#fff8ec" stroke="#eadfcb" />
          {[44, 52, 60, 68, 76].map((x, i) => <circle key={x} cx={x} cy={50 - (i % 2) * 6} r="2.4" fill="#c79a5b" />)}
          <circle cx="60" cy="36" r="5" fill="#d62f4b" /><path d="M60 31q4 -8 10 -8" stroke="#2e8b3d" strokeWidth="2" fill="none" />
        </>
      )}
      {kind === 'plat' && (
        <Plate>
          <path d="M36 62a24 20 0 0 1 48 0q-24 14 -48 0z" fill="#d98236" />
          <circle cx="50" cy="58" r="5" fill="#2e8b3d" /><circle cx="64" cy="54" r="5" fill="#e74c3c" /><circle cx="72" cy="64" r="4" fill="#f1c40f" />
        </Plate>
      )}
    </svg>
  );
}
