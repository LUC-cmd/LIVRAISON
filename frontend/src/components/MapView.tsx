import { useEffect } from 'react';
import L from 'leaflet';
import { MapContainer, Marker, Polyline, Popup, TileLayer, useMap, useMapEvents } from 'react-leaflet';

export interface MapPoint { lat: number; lng: number; label: string; kind: 'courier' | 'destination' | 'pickup' | 'pin' }

const colors = { courier: '#f4661b', destination: '#14201b', pickup: '#17995a', pin: '#2563eb' };
const glyph = { courier: '🛵', destination: '🏠', pickup: '🍽', pin: '📍' };

function icon(kind: MapPoint['kind']) {
  return L.divIcon({
    className: '',
    iconSize: [34, 34],
    iconAnchor: [17, 17],
    html: `<div style="width:34px;height:34px;border-radius:9999px;background:${colors[kind]};display:flex;align-items:center;justify-content:center;font-size:17px;border:3px solid #fff;box-shadow:0 2px 8px rgba(0,0,0,.35)">${glyph[kind]}</div>`,
  });
}

function Fit({ points }: { points: MapPoint[] }) {
  const map = useMap();
  const key = points.map((p) => `${p.lat.toFixed(4)},${p.lng.toFixed(4)}`).join('|');
  useEffect(() => {
    if (points.length === 1) map.setView([points[0].lat, points[0].lng], 15);
    else if (points.length > 1) map.fitBounds(points.map((p) => [p.lat, p.lng] as [number, number]), { padding: [40, 40], maxZoom: 16 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, map]);
  return null;
}

function Clicks({ onPick }: { onPick: (lat: number, lng: number) => void }) {
  useMapEvents({ click: (e) => onPick(e.latlng.lat, e.latlng.lng) });
  return null;
}

/** Carte OpenStreetMap (Leaflet). Par defaut centree sur Lome. */
export function MapView({ points, path, onPick, height = 320 }: { points: MapPoint[]; path?: [number, number][]; onPick?: (lat: number, lng: number) => void; height?: number }) {
  return (
    <MapContainer center={[6.1375, 1.2123]} zoom={13} style={{ height, width: '100%' }} scrollWheelZoom={false}>
      <TileLayer attribution='&copy; contributeurs <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      {path && path.length > 1 && <Polyline positions={path} pathOptions={{ color: '#f4661b', weight: 4, opacity: 0.8 }} />}
      {points.map((p, i) => (
        <Marker key={`${p.kind}-${i}`} position={[p.lat, p.lng]} icon={icon(p.kind)}>
          <Popup>{p.label}</Popup>
        </Marker>
      ))}
      <Fit points={points} />
      {onPick && <Clicks onPick={onPick} />}
    </MapContainer>
  );
}
