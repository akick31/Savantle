import { PlayerSearchItem } from '../../types';
import { normalizeForSearch } from '../../utils/normalize';

interface HintDisplayProps {
  guesses: string[];
  players: PlayerSearchItem[];
}

export default function HintDisplay({ guesses, players }: HintDisplayProps) {
  if (guesses.length === 0) return null;

  const playerMap = new Map(players.map(p => [p.normalizedName, p]));

  return (
    <div className="space-y-1.5">
      {guesses.map((guess, i) => {
        const player = playerMap.get(normalizeForSearch(guess));
        const savantUrl = player
          ? `https://baseballsavant.mlb.com/savant-player/${player.mlbamId}`
          : null;

        return (
          <div key={i} className="bg-sv-card border border-sv-border rounded-lg px-3 py-2 flex items-center justify-between">
            <span className="text-sm text-sv-muted line-through">{guess}</span>
            <div className="flex items-center gap-2 ml-2">
              {savantUrl && (
                <a
                  href={savantUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs text-sv-accent hover:underline flex-shrink-0"
                >
                  Baseball Savant Page
                </a>
              )}
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="14"
                height="14"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="3"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="text-sv-red flex-shrink-0"
              >
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </div>
          </div>
        );
      })}
    </div>
  );
}
