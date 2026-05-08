import { useState, useCallback } from 'react';
import { PlayerStats } from '../types';
import { getLocalDate } from '../utils/share';

const STATS_KEY = 'savantle-stats';

function getYesterday(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return getLocalDate(d);
}

function loadStats(): PlayerStats {
  try {
    const raw = localStorage.getItem(STATS_KEY);
    if (raw) return JSON.parse(raw);
  } catch { }
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

  const recordGame = useCallback((won: boolean, guessCount: number, gameDate?: string) => {
    const date = gameDate ?? getLocalDate();
    const current = loadStats();

    if (current.lastPlayedDate === date) return current;

    const yesterday = getYesterday();
    const newStreak = (current.lastPlayedDate === yesterday && won)
      ? current.currentStreak + 1
      : won ? 1 : 0;

    const dist = { ...current.guessDistribution };
    if (won && guessCount >= 1 && guessCount <= 5) {
      dist[guessCount] = (dist[guessCount] ?? 0) + 1;
    }

    const updated: PlayerStats = {
      currentStreak: newStreak,
      maxStreak: Math.max(current.maxStreak, newStreak),
      gamesPlayed: current.gamesPlayed + 1,
      gamesWon: current.gamesWon + (won ? 1 : 0),
      lastPlayedDate: date,
      guessDistribution: dist,
    };

    localStorage.setItem(STATS_KEY, JSON.stringify(updated));
    setStats(updated);
    return updated;
  }, []);

  return { stats, recordGame };
}
