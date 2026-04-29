import { HintData } from '../types';

const HINT_ICONS: Record<string, string> = {
  POSITION: '📍',
  LEAGUE: '🏟️',
  DIVISION: '🗺️',
  TEAM: '👕',
};

interface HintDisplayProps {
  hints: HintData[];
  guesses: string[];
}

export default function HintDisplay({ hints, guesses }: HintDisplayProps) {
  if (guesses.length === 0) return null;

  return (
    <div className="space-y-2">
      {guesses.map((guess, i) => {
        const hint = hints[i];
        return (
          <div key={i} className="flex items-center gap-2">
            <div className="flex-1 bg-sv-card border border-sv-border rounded-lg px-3 py-2 flex items-center justify-between">
              <span className="text-sm text-sv-muted line-through">{guess}</span>
              <span className="text-xs text-sv-red font-medium ml-2">✗</span>
            </div>
            {hint && (
              <div className="flex items-center gap-1.5 bg-sv-card border border-sv-border rounded-lg px-3 py-2 min-w-[120px]">
                <span className="text-base leading-none">{HINT_ICONS[hint.type]}</span>
                <div className="flex flex-col">
                  <span className="text-xs text-sv-muted leading-none">{hint.label}</span>
                  <span className="text-sm font-semibold text-sv-text">{hint.value}</span>
                </div>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
