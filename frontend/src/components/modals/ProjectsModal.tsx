import Modal from './Modal';

interface ProjectsModalProps {
  open: boolean;
  onClose: () => void;
}

const PROJECTS = [
  {
    name: 'MSRP',
    url: 'https://msrpgame.com/',
    description: 'A daily game to guess the sale price of 5 eBay auction items.',
  },
  {
    name: 'Sine',
    url: 'https://apps.apple.com/us/app/sine-the-waveform-puzzle/id6756984657',
    description: 'An oscilloscope puzzle game on iOS. Try to match your own waveform with procedurally generated ones.',
  },
];

export default function ProjectsModal({ open, onClose }: ProjectsModalProps) {
  return (
    <Modal open={open} onClose={onClose} title="My Other Projects">
      <div className="space-y-3">
        {PROJECTS.map((p) => (
          <a
            key={p.name}
            href={p.url}
            target="_blank"
            rel="noopener noreferrer"
            className="flex flex-col gap-1 p-3 rounded-lg border border-sv-border bg-sv-bg hover:border-sv-accent transition-colors group"
          >
            <span className="text-sm font-semibold text-sv-accent group-hover:underline">
              {p.name} ↗
            </span>
            <span className="text-xs text-sv-muted">{p.description}</span>
          </a>
        ))}
      </div>
    </Modal>
  );
}
