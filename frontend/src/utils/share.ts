import { GameStatus } from '../types';

const LAUNCH_DATE = new Date('2026-04-30');

/** Calendar date in America/New_York (matches server analytics bucketing). */
export function getSavantleAnalyticsDate(date: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/New_York',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
}

/** Eastern calendar May 6–7, 2026 (through midnight Eastern into May 8). */
const EW_DOWNTIME_APOLOGY_DATES = new Set(['2026-05-06', '2026-05-07']);

export function isEffectivelyWildDowntimeApologyWindow(date: Date = new Date()): boolean {
  return EW_DOWNTIME_APOLOGY_DATES.has(getSavantleAnalyticsDate(date));
}

export function getLocalDate(date: Date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function calculateSavantleNumber(date: string): number {
  const [year, month, day] = date.split('-').map(Number);
  const gameDate = new Date(year, month - 1, day);
  const daysSinceLaunch = Math.floor((gameDate.getTime() - LAUNCH_DATE.getTime()) / (1000 * 60 * 60 * 24));
  return Math.max(1, daysSinceLaunch + 1);
}

export function buildShareText(
  status: GameStatus,
  guessCount: number,
  currentStreak: number = 0,
  savantleNumber: number = 1,
): string {
  const guesses = status === 'won' ? guessCount : 5;
  const emoji = Array.from({ length: 5 }, (_, i) => {
    if (status === 'won' && i === guessCount - 1) return '🟩';
    if (i < guesses) return '🟥';
    return '⬜';
  }).join('');

  const result = status === 'won' ? `${guessCount}/5` : 'X/5';
  const streakLine = currentStreak > 0 ? `\nWinning streak: ${currentStreak}` : '';

  return `Savantle | #${savantleNumber}\n${emoji} ${result}${streakLine}`;
}
