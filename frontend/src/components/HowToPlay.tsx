import Modal from './Modal';

interface HowToPlayProps {
  open: boolean;
  onClose: () => void;
}

export default function HowToPlay({ open, onClose }: HowToPlayProps) {
  return (
    <Modal open={open} onClose={onClose} title="How to Play">
      <div className="space-y-4 text-sm text-sv-muted">
        <p className="text-sv-text font-medium">
          Guess today's MLB player from their Baseball Savant percentile rankings.
        </p>

        <div className="space-y-3">
          {[
            'You\'ll see a screenshot of the player\'s Baseball Savant percentile rankings with the player\'s identity hidden.',
            'Type the player\'s full name to make a guess (for example, "Aaron Judge"). Accents are not required and auto-fill is provided.',
            'Each wrong guess reveals a new hint about the player.',
          ].map((text, i) => (
            <div key={i} className="flex gap-3">
              <span className="text-sv-accent font-bold text-base leading-6 flex-shrink-0">{i + 1}.</span>
              <p>{text}</p>
            </div>
          ))}
        </div>

        <div className="border-t border-sv-border pt-4 space-y-2">
          <p className="font-medium text-sv-text">Hints (revealed after each wrong guess)</p>
          {[
            ['Guess 2', 'Position (e.g., SS, 3B) or pitcher handedness (LHP, RHP)'],
            ['Guess 3', 'League (AL or NL)'],
            ['Guess 4', 'Division (e.g., AL East)'],
            ['Guess 5', 'Team name'],
          ].map(([label, desc]) => (
            <div key={label} className="flex items-start gap-2 text-xs">
              <span className="w-1.5 h-1.5 rounded-full bg-sv-accent mt-1.5 flex-shrink-0" />
              <div>
                <span className="text-sv-text font-medium">{label}:</span>{' '}
                <span>{desc}</span>
              </div>
            </div>
          ))}
        </div>

        <div className="border-t border-sv-border pt-4">
          <p className="text-xs text-sv-muted">
            A new player is revealed every day at midnight. Players are selected from active 26-man MLB rosters up to 7 days in advance, so a player who has been removed from an active roster in the last 7 days may still appear.
          </p>
        </div>

        <div className="border-t border-sv-border pt-4">
          <p className="text-xs text-sv-muted">
            Percentile data and screenshots are sourced from{' '}
            <a
              href="https://baseballsavant.mlb.com/"
              target="_blank"
              rel="noopener noreferrer"
              className="text-sv-accent hover:underline"
            >
              Baseball Savant
            </a>
            . Savantle is an unofficial fan project and is not affiliated with MLB or Baseball Savant.
          </p>
        </div>
      </div>
    </Modal>
  );
}
