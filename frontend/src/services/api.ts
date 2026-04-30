import { DailyData, GuessResult, PlayerSearchItem } from '../types';

const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim();
export const BASE_URL = API_ORIGIN
  ? `${API_ORIGIN.replace(/\/+$/, '')}/api/v1/savantle`
  : '/api/v1/savantle';

export async function fetchDailyPlayer(date?: string): Promise<DailyData> {
  const url = date ? `${BASE_URL}/daily?date=${date}` : `${BASE_URL}/daily`;
  const res = await fetch(url);
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error((body as { error?: string }).error ?? `HTTP ${res.status}`);
  }
  return res.json();
}

export async function fetchPlayerList(): Promise<PlayerSearchItem[]> {
  const res = await fetch(`${BASE_URL}/players`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function submitGuess(
  playerName: string,
  guessNumber: number,
  date?: string
): Promise<GuessResult> {
  const res = await fetch(`${BASE_URL}/guess`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ playerName, guessNumber, date }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error((body as { error?: string }).error ?? `HTTP ${res.status}`);
  }
  return res.json();
}
