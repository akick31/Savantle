import Modal from './Modal';

export const API_NOTICE_ACTIVE = true;

interface ApiNoticeModalProps {
  open: boolean;
  onClose: () => void;
}

export default function ApiNoticeModal({ open, onClose }: ApiNoticeModalProps) {
  return (
    <Modal open={open} onClose={onClose} title="Known issue: player search">
      <div className="text-sv-text text-sm leading-relaxed">
        <p>
          Player search (autofill) isn&apos;t returning every player right now because of recent changes on MLB&apos;s Stats API.
        </p>
        <p className="mt-4">
          I&apos;m aware of this and actively working on a fix. Sorry for the inconvenience!
        </p>
      </div>
      <button
        type="button"
        onClick={onClose}
        className="mt-4 w-full rounded-lg bg-sv-accent py-2.5 text-sm font-semibold text-white hover:opacity-90 transition-opacity"
      >
        Got it
      </button>
    </Modal>
  );
}
