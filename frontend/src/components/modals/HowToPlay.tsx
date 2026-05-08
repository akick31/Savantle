import Modal from './Modal';

interface HowToPlayProps {
  open: boolean;
  onClose: () => void;
  onContact: () => void;
}

export default function HowToPlay({ open, onClose, onContact }: HowToPlayProps) {
  return (
    <Modal open={open} onClose={onClose} title="How to Play">
      <div className="space-y-3 text-sm text-sv-muted max-h-[70vh] overflow-y-auto">
        <p className="text-sv-text font-medium">
          Guess a MLB player by their Baseball Savant percentile rankings.
        </p>

        <div className="space-y-2">
          {[
            'You\'ll see a screenshot of the player\'s Baseball Savant percentile rankings with the player\'s identity hidden.',
            'You have 5 guesses. Each wrong guess reveals the next hint in order. If your guess is on the same team or in the same division as the answer, those hints are confirmed early and shown in green.',
            'Full name required (e.g., "Aaron Judge"). Accents are not required and auto-fill is provided.',
          ].map((text, i) => (
            <div key={i} className="flex gap-2">
              <span className="text-sv-accent font-bold text-sm leading-5 flex-shrink-0">{i + 1}.</span>
              <p className="text-xs">{text}</p>
            </div>
          ))}
        </div>

        <div className="border-t border-sv-border pt-3">
          <p className="font-medium text-sv-text text-xs mb-2">Hints after each wrong guess</p>
          {[
            ['Guess 1', 'Free for all, no hint'],
            ['Guess 2', 'Position (e.g., SS, 3B) or pitcher handedness (LHP, RHP)'],
            ['Guess 3', 'League (AL or NL)'],
            ['Guess 4', 'Division (e.g., AL East)'],
            ['Guess 5', 'Team name'],
          ].map(([label, desc]) => (
            <div key={label} className="flex items-start gap-1.5 text-xs mb-1">
              <span className="w-1 h-1 rounded-full bg-sv-accent mt-1 flex-shrink-0" />
              <div className="flex-1">
                <span className="text-sv-text font-medium">{label}:</span>{' '}
                <span>{desc}</span>
              </div>
            </div>
          ))}
          <p className="text-xs text-sv-muted mt-2">
            Early confirmation only applies to team and division. Guessing a player on the correct team reveals team, division, and league. Guessing a player in the correct division reveals division and league. A guess that only shares the same league or position does not confirm anything early.
          </p>
        </div>

        <div className="border-t border-sv-border pt-3 text-xs text-sv-muted">
          <p className="mb-2">
            A new player is revealed every day at midnight. Players are selected from active 26-man MLB rosters with at least 75 plate appearances (batters) or 15 innings pitched (pitchers). This requirement is waived during the first 30 days of the season.
          </p>
          <p>
            Percentile data and screenshots are from{' '}
            <a
              href="https://baseballsavant.mlb.com/"
              target="_blank"
              rel="noopener noreferrer"
              className="text-sv-accent hover:underline"
            >
              Baseball Savant
            </a>
            . Savantle is an unofficial fan project by Andrew Kicklighter.
          </p>
        </div>

        <div className="border-t border-sv-border pt-3 text-xs text-sv-muted text-center">
          <p>
            Have a question, suggestion, or bug to report?{' '}
            <button
              onClick={() => { onClose(); onContact(); }}
              className="text-sv-accent hover:underline"
            >
              Contact me using this form.
            </button>
          </p>
        </div>
      </div>
    </Modal>
  );
}
