interface HintDisplayProps {
  guesses: string[];
}

export default function HintDisplay({ guesses }: HintDisplayProps) {
  if (guesses.length === 0) return null;

  return (
    <div className="space-y-1.5">
      {guesses.map((guess, i) => (
        <div key={i} className="bg-sv-card border border-sv-border rounded-lg px-3 py-2 flex items-center justify-between">
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
      ))}
    </div>
  );
}
