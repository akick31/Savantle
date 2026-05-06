import { DailyData, GuessResult, PlayerSearchItem, RandomGameData } from '../types';

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

export function liveScreenshotUrl(date: string): string {
  return `${BASE_URL}/screenshot/live/${date}`;
}

export function dailyScreenshotUrl(date: string): string {
  return `${BASE_URL}/screenshot/${date}`;
}

export function randomGameScreenshotUrl(gameId: string): string {
  return `${BASE_URL}/random-player/screenshot/${gameId}`;
}

export async function createRandomGame(): Promise<RandomGameData> {
  const res = await fetch(`${BASE_URL}/random-player/new`, { method: 'POST' });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error((body as { error?: string }).error ?? `HTTP ${res.status}`);
  }
  return res.json();
}

export async function submitRandomGuess(
  gameId: string,
  playerName: string,
  guessNumber: number
): Promise<GuessResult> {
  const res = await fetch(`${BASE_URL}/random-player/guess`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ gameId, playerName, guessNumber }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error((body as { error?: string }).error ?? `HTTP ${res.status}`);
  }
  return res.json();
}

export async function fetchAvailableDates(): Promise<string[]> {
  const res = await fetch(`${BASE_URL}/available-dates`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function fetchRandomDate(): Promise<string> {
  const res = await fetch(`${BASE_URL}/random-date`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const data = await res.json();
  return data.date;
}

export async function recordAnalytics(eventType: string): Promise<void> {
  try {
    await fetch(`${BASE_URL}/analytics`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventType }),
    });
  } catch (e) {
    console.error('[analytics] failed to record', eventType, e);
  }
}

export async function submitContact(payload: {
  name: string;
  email: string;
  subject: string;
  message: string;
}): Promise<void> {
  const res = await fetch(`${BASE_URL}/contact`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error((body as { error?: string }).error ?? `HTTP ${res.status}`);
  }
}
