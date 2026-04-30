import { GameStatus } from '../types';

const LAUNCH_DATE = new Date('2026-04-30'); // Savantle launch date

export function getLocalDate(date: Date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function calculateSaventleNumber(date: string): number {
  const [year, month, day] = date.split('-').map(Number);
  const gameDate = new Date(year, month - 1, day);
  const daysSinceLaunch = Math.floor((gameDate.getTime() - LAUNCH_DATE.getTime()) / (1000 * 60 * 60 * 24));
  return Math.max(1, daysSinceLaunch + 1);
}

export function buildShareText(
  status: GameStatus,
  guessCount: number,
  currentStreak: number = 0,
  saventleNumber: number = 1,
): string {
  const guesses = status === 'won' ? guessCount : 5;
  const emoji = Array.from({ length: 5 }, (_, i) => {
    if (status === 'won' && i === guessCount - 1) return '🟩';
    if (i < guesses) return '🟥';
    return '⬜';
  }).join('');

  const result = status === 'won' ? `${guessCount}/5` : 'X/5';
  const streakText = currentStreak > 0 ? ` - Streak: ${currentStreak}` : '';

  return `Savantle | #${saventleNumber}${streakText}\n${emoji} ${result}`;
}
