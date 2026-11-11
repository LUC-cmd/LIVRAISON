import { Camera } from 'lucide-react';
import { PHOTOS, commonsImage, commonsPage } from '../components/DishArt';
import { PageTitle } from '../components/ui';

/** Credits des photos par defaut : chaque image renvoie a sa page Wikimedia Commons (auteur et licence). */
export function CreditsPage() {
  const files = PHOTOS.filter((p, i, all) => all.findIndex((x) => x.file === p.file) === i);
  return (
    <div>
      <PageTitle icon={<Camera className="h-6 w-6" />} title="Crédits photos" subtitle="Les photos de plats affichées par défaut viennent de Wikimedia Commons, sous licences libres. L'auteur et la licence de chaque photo figurent sur sa page." />
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {files.map((p) => (
          <a key={p.file} href={commonsPage(p.file)} target="_blank" rel="noreferrer" className="card block overflow-hidden !p-0">
            <img src={commonsImage(p.file, 480)} alt={p.label} loading="lazy" className="h-40 w-full object-cover" />
            <div className="p-3"><p className="text-sm font-semibold">{p.label}</p><p className="break-words text-xs text-ink-500">{p.file} — Wikimedia Commons</p></div>
          </a>
        ))}
      </div>
    </div>
  );
}
