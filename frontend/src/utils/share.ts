import { GameStatus } from '../types';

export function getLocalDate(date: Date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function buildShareText(
  status: GameStatus,
  guessCount: number,
): string {
  const guesses = status === 'won' ? guessCount : 5;
  const emoji = Array.from({ length: 5 }, (_, i) => {
    if (status === 'won' && i === guessCount - 1) return '🟩';
    if (i < guesses) return '🟥';
    return '⬜';
  }).join('');

  const result = status === 'won' ? `${guessCount}/5` : 'X/5';

  return `Savantle | #1\n${emoji} ${result}`;
}
