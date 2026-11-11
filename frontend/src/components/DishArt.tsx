import { useState } from 'react';
import { UtensilsCrossed } from 'lucide-react';

/**
 * Photos reelles de plats, issues de Wikimedia Commons (licences libres, auteurs credites sur la page de chaque fichier).
 * Elles servent d'image par defaut tant que le restaurant n'a pas televerse sa propre photo.
 * Un plat sans photo correspondante affiche un fond neutre, jamais un dessin.
 */
export const PHOTOS: { match: RegExp; file: string; label: string }[] = [
  { match: /(ayimolou|riz|haricot|togolaise)/, file: "Riz à l'haricot communément appelé Ayimolou au Togo.jpg", label: 'Ayimolou (riz au haricot), Togo' },
  { match: /gboma/, file: 'Akoumin et sauce gboman.jpg', label: 'Akoumé et sauce gboma' },
  { match: /(akoum|p[aâ]te)/, file: "Plat d'Akoumé au Togo 01.jpg", label: "Plat d'akoumé, Togo" },
  { match: /fufu.*arachide/, file: "Fufu accompagné d'une sauce arachide au poulet.JPG", label: 'Fufu, sauce arachide au poulet' },
  { match: /plateau/, file: 'Plat de foufou (Togo) 07.jpg', label: 'Plat de foufou, Togo' },
  { match: /(fufu|foufou)/, file: 'Plat de foufou (Togo) 01.jpg', label: 'Plat de foufou, Togo' },
  { match: /demi-poulet/, file: 'Poulet braisé attieké.JPG', label: 'Poulet braisé' },
  { match: /(poulet|grillade|brais)/, file: 'Du poulet braiser ivoirien.JPG', label: 'Poulet braisé' },
  { match: /alloco/, file: 'Alloco au poulet.jpg', label: 'Alloco' },
  { match: /gingembre/, file: 'Jus de bouye, gingembre et bissap 04.jpg', label: 'Jus de gingembre et de bissap' },
  { match: /(bissap|jus)/, file: 'Jus de bouye, gingembre et bissap 01.jpg', label: 'Jus de bissap' },
  { match: /(degue|dégué|thiakry)/, file: 'Gambia Chakery 0001.jpg', label: 'Dégué (thiakry)' },
];

export const commonsImage = (file: string, width = 640) => `https://commons.wikimedia.org/wiki/Special:FilePath/${encodeURIComponent(file)}?width=${width}`;
export const commonsPage = (file: string) => `https://commons.wikimedia.org/wiki/File:${encodeURIComponent(file.replace(/ /g, '_'))}`;

export function DishArt({ name, className = '' }: { name: string; className?: string }) {
  const [failed, setFailed] = useState(false);
  const photo = PHOTOS.find((p) => p.match.test(name.toLowerCase()));
  if (!photo || failed) {
    return <div className={`flex items-center justify-center bg-ink-100 text-ink-300 ${className}`} role="img" aria-label={`Pas encore de photo pour ${name}`}><UtensilsCrossed className="h-1/3 w-1/3" /></div>;
  }
  return <img src={commonsImage(photo.file)} alt={photo.label} loading="lazy" onError={() => setFailed(true)} className={`object-cover ${className}`} />;
}
