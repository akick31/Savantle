import { DailyData, HintData, PlayerSearchItem } from '../types';
import PercentileDisplay from './PercentileDisplay';
import HintDisplay from './HintDisplay';
import PlayerSearch from './PlayerSearch';

interface GamePlayProps {
  dailyData: DailyData;
  players: PlayerSearchItem[];
  guesses: string[];
  hints: HintData[];
  isSubmitting: boolean;
  onGuess: (name: string) => void;
}

const MAX_GUESSES = 5;

export default function GamePlay({ dailyData, players, guesses, hints, isSubmitting, onGuess }: GamePlayProps) {
  const guessesLeft = MAX_GUESSES - guesses.length;
  const guessNumber = guesses.length + 1;
  const latestHint = hints.length > 0 ? hints[hints.length - 1] : null;

  return (
    <div className="space-y-4">
      <PercentileDisplay date={dailyData.date} playerType={dailyData.playerType} />

      <HintDisplay hints={hints} guesses={guesses} />

      <div className="bg-sv-card border border-sv-border rounded-xl p-4 space-y-3">
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium text-sv-text">
            Guess {guessNumber} of {MAX_GUESSES}
          </span>
          <span className="text-xs text-sv-muted">
            {guessesLeft} {guessesLeft === 1 ? 'guess' : 'guesses'} remaining
          </span>
        </div>

        <div className="flex gap-1">
          {Array.from({ length: MAX_GUESSES }, (_, i) => (
            <div
              key={i}
              className={`flex-1 h-1.5 rounded-full transition-colors ${
                i < guesses.length ? 'bg-sv-red' : 'bg-sv-border'
              }`}
            />
          ))}
        </div>

        {latestHint && (
          <div className="flex items-center gap-2 bg-sv-bg border border-sv-accent/30 rounded-lg px-3 py-2">
            <span className="w-1.5 h-1.5 rounded-full bg-sv-accent flex-shrink-0" />
            <span className="text-xs text-sv-muted">{latestHint.label}:</span>
            <span className="text-sm font-semibold text-sv-accent">{latestHint.value}</span>
          </div>
        )}

        <PlayerSearch
          players={players}
          playerType={dailyData.playerType}
          onSubmit={onGuess}
          disabled={isSubmitting}
        />
      </div>
    </div>
  );
}
