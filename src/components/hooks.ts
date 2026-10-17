import { useEffect, useState, useSyncExternalStore } from 'react';

// View-only preferences (sub-tabs, sort order, metric toggles) that should survive switching
// sections without being lifted into App or triggering a new query.
const sticky = new Map<string, unknown>();
export function useSticky<T>(key: string, initial: T) {
  const [value, setValue] = useState<T>(() => (sticky.has(key) ? (sticky.get(key) as T) : initial));
  useEffect(() => { sticky.set(key, value); }, [key, value]);
  return [value, setValue] as const;
}

export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    notify => {
      const list = window.matchMedia(query);
      list.addEventListener('change', notify);
      return () => list.removeEventListener('change', notify);
    },
    () => window.matchMedia(query).matches,
    () => false,
  );
}

export const useReducedMotion = () => useMediaQuery('(prefers-reduced-motion: reduce)');
export const useCompact = () => useMediaQuery('(max-width: 720px)');
