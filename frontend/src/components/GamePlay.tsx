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

const HINT_TYPE_ORDER: Record<string, number> = {
  POSITION: 0,
  LEAGUE: 1,
  DIVISION: 2,
  TEAM: 3,
};

function HintRow({ hint }: { hint: HintData }) {
  return (
    <div
      className={`flex items-center gap-2 rounded-lg px-3 py-2 border ${
        hint.confirmed
          ? 'bg-sv-accent/10 border-sv-accent/40'
          : 'bg-sv-bg border-sv-accent/30'
      }`}
    >
      {hint.confirmed ? (
        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" className="text-sv-accent flex-shrink-0">
          <polyline points="20 6 9 17 4 12" />
        </svg>
      ) : (
        <span className="w-1.5 h-1.5 rounded-full bg-sv-accent flex-shrink-0" />
      )}
      <span className="text-xs text-sv-muted">{hint.label}:</span>
      <span className="text-sm font-semibold text-sv-accent">{hint.value}</span>
      {hint.confirmed && (
        <span className="ml-auto text-xs text-sv-accent font-medium">confirmed</span>
      )}
    </div>
  );
}

export default function GamePlay({ dailyData, players, guesses, hints, isSubmitting, onGuess }: GamePlayProps) {
  const guessesLeft = MAX_GUESSES - guesses.length;
  const guessNumber = guesses.length + 1;

  const confirmedHints = hints
    .filter(h => h.confirmed)
    .sort((a, b) => (HINT_TYPE_ORDER[a.type] ?? 9) - (HINT_TYPE_ORDER[b.type] ?? 9));
  const currentHints = hints.filter(h => !h.confirmed);

  return (
    <div className="space-y-4">
      <PercentileDisplay date={dailyData.date} playerType={dailyData.playerType} />

      {guesses.length > 0 && <HintDisplay guesses={guesses} />}

      <div className="bg-sv-card border border-sv-border rounded-xl p-4 space-y-3">
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium text-sv-text">
            Guess {guessNumber} of {MAX_GUESSES}
          </span>
          <span className="text-xs text-sv-muted">
            {guessesLeft} {guessesLeft === 1 ? 'guess' : 'guesses'} remaining
          </span>
        </div>

        {confirmedHints.length > 0 && (
          <div className="space-y-1.5">
            {confirmedHints.map(hint => (
              <HintRow key={hint.type} hint={hint} />
            ))}
          </div>
        )}

        {currentHints.length > 0 && (
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              {confirmedHints.length > 0 && <div className="flex-1 h-px bg-sv-border" />}
              <span className="text-xs font-medium text-sv-muted uppercase tracking-wide">Current Hint</span>
              <div className="flex-1 h-px bg-sv-border" />
            </div>
            <div className="space-y-1.5">
              {currentHints.map(hint => (
                <HintRow key={hint.type} hint={hint} />
              ))}
            </div>
          </div>
        )}

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
