import { HintData } from '../types';

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
                className="text-sv-red ml-2"
              >
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </div>
            {hint && (
              <div className="flex items-center gap-1.5 bg-sv-card border border-sv-border rounded-lg px-3 py-2 min-w-[120px]">
                <span className="w-1.5 h-1.5 rounded-full bg-sv-accent flex-shrink-0" />
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
