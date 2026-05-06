import { HintData } from '../types';

export function mergeHints(existing: HintData[], incoming: HintData[]): HintData[] {
  const map = new Map(existing.map(h => [h.type, h]));
  for (const h of incoming) {
    const current = map.get(h.type);
    if (!current || h.confirmed) map.set(h.type, h);
  }
  return Array.from(map.values());
}
