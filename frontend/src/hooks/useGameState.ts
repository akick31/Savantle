import { useState, useCallback, useEffect } from 'react';
import { DailyData, GameStatus, HintData, PlayerInfo, PlayerSearchItem, StoredGame } from '../types';
import { fetchDailyPlayer, fetchPlayerList, submitGuess } from '../services/api';
import { getLocalDate } from '../utils/share';
import { mergeHints } from '../utils/hints';

const GAME_KEY = 'savantle-game';

function getStorageKey(date: string): string {
  return `${GAME_KEY}-${date}`;
}

function loadStoredGame(date: string): StoredGame | null {
  try {
    const raw = localStorage.getItem(getStorageKey(date));
    if (raw) return JSON.parse(raw);
  } catch { /* ignore */ }
  return null;
}

function saveGame(date: string, game: StoredGame): void {
  localStorage.setItem(getStorageKey(date), JSON.stringify(game));
}

interface UseGameStateOptions {
  overrideDate?: string;
  persist?: boolean;
}

export function useGameState(options: UseGameStateOptions = {}) {
  const { overrideDate, persist = true } = options;

  const [dailyData, setDailyData] = useState<DailyData | null>(null);
  const [players, setPlayers] = useState<PlayerSearchItem[]>([]);
  const [status, setStatus] = useState<GameStatus>('loading');
  const [guesses, setGuesses] = useState<string[]>([]);
  const [hints, setHints] = useState<HintData[]>([]);
  const [latestHints, setLatestHints] = useState<HintData[]>([]);
  const [playerInfo, setPlayerInfo] = useState<PlayerInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function init() {
      setStatus('loading');
      setGuesses([]);
      setHints([]);
      setLatestHints([]);
      setPlayerInfo(null);
      setError(null);

      try {
        const dateParam = overrideDate ?? getLocalDate();
        const [data, playerList] = await Promise.all([
          fetchDailyPlayer(dateParam),
          fetchPlayerList(),
        ]);
        if (cancelled) return;

        setDailyData(data);
        setPlayers(playerList);

        if (persist) {
          const stored = loadStoredGame(data.date);
          if (stored && stored.date === data.date) {
            setGuesses(stored.guesses);
            setHints(stored.hints);
            setPlayerInfo(stored.playerInfo);
            setStatus(stored.status);
            return;
          }
        }
        setStatus('playing');
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Failed to load game data');
          setStatus('loading');
        }
      }
    }

    init();
    return () => { cancelled = true; };
  }, [overrideDate, persist]);

  const makeGuess = useCallback(async (playerName: string): Promise<boolean> => {
    if (isSubmitting || !dailyData) return false;
    setIsSubmitting(true);

    try {
      const guessNumber = guesses.length + 1;
      const result = await submitGuess(playerName, guessNumber, dailyData.date);

      const newGuesses = [...guesses, playerName];
      const incoming = result.hints ?? [];
      const newHints = mergeHints(hints, incoming);

      const freshHints = incoming.filter(h => {
        const existing = hints.find(e => e.type === h.type);
        if (!existing) return true;
        if (h.confirmed && !existing.confirmed) return true;
        return false;
      });

      setGuesses(newGuesses);
      setHints(newHints);
      setLatestHints(freshHints);

      let newStatus: GameStatus = 'playing';
      let newPlayerInfo = playerInfo;

      if (result.correct) {
        newStatus = 'won';
        newPlayerInfo = result.playerInfo ?? null;
      } else if (result.gameOver) {
        newStatus = 'lost';
        newPlayerInfo = result.playerInfo ?? null;
      }

      setStatus(newStatus);
      setPlayerInfo(newPlayerInfo);

      if (persist) {
        const game: StoredGame = {
          date: dailyData.date,
          status: newStatus,
          guesses: newGuesses,
          hints: newHints,
          playerInfo: newPlayerInfo,
        };
        saveGame(dailyData.date, game);
      }

      return result.correct;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to submit guess');
      return false;
    } finally {
      setIsSubmitting(false);
    }
  }, [isSubmitting, dailyData, guesses, hints, playerInfo, persist]);

  return {
    dailyData,
    players,
    status,
    guesses,
    hints,
    latestHints,
    playerInfo,
    error,
    isSubmitting,
    makeGuess,
  };
}
