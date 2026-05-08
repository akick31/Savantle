import { useState, useCallback } from 'react';
import { GameStatus, HintData, PlayerInfo, PlayerSearchItem, RandomGameData } from '../types';
import { fetchPlayerList, createRandomGame, submitRandomGuess } from '../services/api';
import { mergeHints } from '../utils/hints';

export function useRandomGameState() {
  const [gameData, setGameData] = useState<RandomGameData | null>(null);
  const [players, setPlayers] = useState<PlayerSearchItem[]>([]);
  const [status, setStatus] = useState<GameStatus>('loading');
  const [guesses, setGuesses] = useState<string[]>([]);
  const [hints, setHints] = useState<HintData[]>([]);
  const [latestHints, setLatestHints] = useState<HintData[]>([]);
  const [playerInfo, setPlayerInfo] = useState<PlayerInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const startGame = useCallback(async () => {
    setStatus('loading');
    setGuesses([]);
    setHints([]);
    setLatestHints([]);
    setPlayerInfo(null);
    setError(null);
    setGameData(null);

    try {
      const [newGame, playerList] = await Promise.all([
        createRandomGame(),
        players.length > 0 ? Promise.resolve(players) : fetchPlayerList(),
      ]);
      setGameData(newGame);
      setPlayers(playerList);
      setStatus('playing');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to start random game');
      setStatus('loading');
    }
  }, [players]);

  const makeGuess = useCallback(async (playerName: string): Promise<boolean> => {
    if (isSubmitting || !gameData) return false;
    setIsSubmitting(true);

    try {
      const guessNumber = guesses.length + 1;
      const result = await submitRandomGuess(gameData.gameId, playerName, guessNumber);

      const newGuesses = [...guesses, playerName];
      const incoming = result.hints ?? [];
      const newHints = mergeHints(hints, incoming);
      const freshHints = incoming.filter(h => {
        const ex = hints.find(e => e.type === h.type);
        return !ex || (h.confirmed && !ex.confirmed);
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
      return result.correct;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to submit guess');
      return false;
    } finally {
      setIsSubmitting(false);
    }
  }, [isSubmitting, gameData, guesses, hints, playerInfo]);

  return { gameData, players, status, guesses, hints, latestHints, playerInfo, error, isSubmitting, startGame, makeGuess };
}
