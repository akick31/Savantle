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
          Guess today's MLB player from their Baseball Savant percentile stats.
        </p>

        <div className="space-y-3">
          {[
            'You\'ll see a player\'s percentile rankings for various stats — just like Baseball Savant, but with the player\'s identity hidden.',
            'Type the player\'s full name to make a guess (e.g., "Aaron Judge"). Accents are not required.',
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
            ['⚾', 'Guess 1', 'Only percentile stats'],
            ['📍', 'Guess 2', 'Player position (e.g., SS, SP)'],
            ['🏟️', 'Guess 3', 'League (AL or NL)'],
            ['🗺️', 'Guess 4', 'Division (e.g., AL East)'],
            ['👕', 'Guess 5', 'Team name'],
          ].map(([icon, label, desc]) => (
            <div key={label} className="flex items-start gap-2 text-xs">
              <span className="text-base leading-4 mt-0.5">{icon}</span>
              <div>
                <span className="text-sv-text font-medium">{label}:</span>{' '}
                <span>{desc}</span>
              </div>
            </div>
          ))}
        </div>

        <div className="border-t border-sv-border pt-4">
          <div className="space-y-1.5">
            <p className="font-medium text-sv-text mb-2">Percentile colors</p>
            {[
              ['bg-sv-p100', '90–99 — Elite'],
              ['bg-sv-p80', '70–89 — Above Average'],
              ['bg-sv-p50', '45–69 — Average'],
              ['bg-sv-p20', '25–44 — Below Average'],
              ['bg-sv-p0', '1–24 — Poor'],
            ].map(([cls, label]) => (
              <div key={cls} className="flex items-center gap-2 text-xs">
                <span className={`w-4 h-4 rounded-full flex-shrink-0 ${cls}`} />
                <span>{label}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="border-t border-sv-border pt-4">
          <p className="text-xs text-sv-muted">
            A new player is revealed every day at midnight. Play daily to keep your streak going!
          </p>
        </div>
      </div>
    </Modal>
  );
}
