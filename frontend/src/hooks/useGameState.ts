import { useState, useCallback, useEffect } from 'react';
import { DailyData, GameStatus, HintData, PlayerInfo, PlayerSearchItem, StoredGame } from '../types';
import { fetchDailyPlayer, fetchPlayerList, submitGuess } from '../services/api';
import { getLocalDate } from '../utils/share';

const GAME_KEY = 'savantle-game';

function getTodayKey(): string {
  return `${GAME_KEY}-${getLocalDate()}`;
}

function loadStoredGame(): StoredGame | null {
  try {
    const raw = localStorage.getItem(getTodayKey());
    if (raw) return JSON.parse(raw);
  } catch { /* ignore */ }
  return null;
}

function saveGame(game: StoredGame): void {
  localStorage.setItem(getTodayKey(), JSON.stringify(game));
}

export function useGameState() {
  const [dailyData, setDailyData] = useState<DailyData | null>(null);
  const [players, setPlayers] = useState<PlayerSearchItem[]>([]);
  const [status, setStatus] = useState<GameStatus>('loading');
  const [guesses, setGuesses] = useState<string[]>([]);
  const [hints, setHints] = useState<HintData[]>([]);
  const [playerInfo, setPlayerInfo] = useState<PlayerInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    async function init() {
      try {
        const localDate = getLocalDate();
        const [data, playerList] = await Promise.all([
          fetchDailyPlayer(localDate),
          fetchPlayerList(),
        ]);
        setDailyData(data);
        setPlayers(playerList);

        const stored = loadStoredGame();
        if (stored && stored.date === data.date) {
          setGuesses(stored.guesses);
          setHints(stored.hints);
          setPlayerInfo(stored.playerInfo);
          setStatus(stored.status);
        } else {
          setStatus('playing');
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load game data');
        setStatus('loading');
      }
    }
    init();
  }, []);

  const makeGuess = useCallback(async (playerName: string): Promise<boolean> => {
    if (isSubmitting || !dailyData) return false;
    setIsSubmitting(true);

    try {
      const guessNumber = guesses.length + 1;
      const result = await submitGuess(playerName, guessNumber, dailyData.date);

      const newGuesses = [...guesses, playerName];
      const newHints = result.hint ? [...hints, result.hint] : hints;

      setGuesses(newGuesses);
      setHints(newHints);

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

      const game: StoredGame = {
        date: dailyData.date,
        status: newStatus,
        guesses: newGuesses,
        hints: newHints,
        playerInfo: newPlayerInfo,
      };
      saveGame(game);

      return result.correct;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to submit guess');
      return false;
    } finally {
      setIsSubmitting(false);
    }
  }, [isSubmitting, dailyData, guesses, hints, playerInfo]);

  return {
    dailyData,
    players,
    status,
    guesses,
    hints,
    playerInfo,
    error,
    isSubmitting,
    makeGuess,
  };
}
