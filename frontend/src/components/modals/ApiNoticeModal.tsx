import Modal from './Modal';

export const API_NOTICE_ACTIVE = true;

interface ApiNoticeModalProps {
  open: boolean;
  onClose: () => void;
}

export default function ApiNoticeModal({ open, onClose }: ApiNoticeModalProps) {
  return (
    <Modal open={open} onClose={onClose} title="Update: player search issue">
      <div className="text-sv-text text-sm leading-relaxed">
        <p>
          The player search (autofill) issue from the past few days, caused by changes on MLB&apos;s Stats API, should now
          be resolved. I&apos;ll be monitoring it closely over the next few days to make sure it stays fixed.
        </p>
        <p className="mt-4">
          One side effect of the fix: the game can no longer always tell whether a player is currently on the injured
          list or optioned to the minors, so an inactive player could occasionally show up as the mystery player or in
          search. To help spot this, player reveals now show their last game played.
        </p>
        <p className="mt-4">
          Thanks for your patience, and sorry for the inconvenience!
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
