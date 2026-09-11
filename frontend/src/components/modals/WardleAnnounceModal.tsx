import Modal from './Modal';

export const WARDLE_ANNOUNCE_LAUNCH_DATE = '2026-09-11';
export const WARDLE_ANNOUNCE_ICON_WINDOW_DAYS = 5;

export function daysSinceWardleAnnounceLaunch(todayEST: string): number {
  const [ty, tm, td] = todayEST.split('-').map(Number);
  const [ly, lm, ld] = WARDLE_ANNOUNCE_LAUNCH_DATE.split('-').map(Number);
  return Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(ly, lm - 1, ld)) / 86400000);
}

interface WardleAnnounceModalProps {
  open: boolean;
  onClose: () => void;
}

export default function WardleAnnounceModal({ open, onClose }: WardleAnnounceModalProps) {
  return (
    <Modal open={open} onClose={onClose} title="New game: Wardle">
      <div className="text-sv-text text-sm leading-relaxed">
        <p>
          I just launched a new daily baseball game called Wardle! You get 5 player stat lines from any MLB
          season and have to guess their WAR for that year, no name or anything attached.
        </p>
        <p className="mt-4">
          After finishing, you get a score to share with your streak, same as here. I figured Savantle players
          would be into it!
        </p>
        <p className="mt-4">
          If you've got friends who'd like either game, please send them a link. Word of mouth is basically the
          only marketing these get!
        </p>
      </div>
      <a
        href="https://wardlegame.app"
        target="_blank"
        rel="noopener noreferrer"
        onClick={onClose}
        className="mt-4 block w-full rounded-lg bg-sv-accent py-2.5 text-center text-sm font-semibold text-white hover:opacity-90 transition-opacity"
      >
        Check out Wardle
      </a>
    </Modal>
  );
}
