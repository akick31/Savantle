import { useState, useCallback } from 'react';
import { PlayerStats } from '../types';

const STATS_KEY = 'savantle-stats';

function getLocalDate(date: Date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function getYesterday(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return getLocalDate(d);
}

function loadStats(): PlayerStats {
  try {
    const raw = localStorage.getItem(STATS_KEY);
    if (raw) return JSON.parse(raw);
  } catch { /* ignore */ }
  return {
    currentStreak: 0,
    maxStreak: 0,
    gamesPlayed: 0,
    gamesWon: 0,
    lastPlayedDate: '',
    guessDistribution: { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 },
  };
}

export function useStats() {
  const [stats, setStats] = useState<PlayerStats>(loadStats);

  const recordGame = useCallback((won: boolean, guessCount: number) => {
    const today = getLocalDate();
    const current = loadStats();

    if (current.lastPlayedDate === today) return current;

    const newStreak = current.lastPlayedDate === getYesterday()
      ? current.currentStreak + (won ? 1 : 0)
      : won ? 1 : 0;

    const dist = { ...current.guessDistribution };
    if (won) dist[guessCount] = (dist[guessCount] ?? 0) + 1;

    const updated: PlayerStats = {
      currentStreak: newStreak,
      maxStreak: Math.max(current.maxStreak, newStreak),
      gamesPlayed: current.gamesPlayed + 1,
      gamesWon: current.gamesWon + (won ? 1 : 0),
      lastPlayedDate: today,
      guessDistribution: dist,
    };

    localStorage.setItem(STATS_KEY, JSON.stringify(updated));
    setStats(updated);
    return updated;
  }, []);

  return { stats, recordGame };
}
