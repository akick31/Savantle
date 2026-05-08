import { useCallback, useEffect, useState } from 'react';
import Modal from './Modal';
import { isEffectivelyWildDowntimeApologyWindow } from '../../utils/share';

const STORAGE_KEY = 'savantle-ew-apology-dismissed';
/** Auto-open at most once per tab session; dismiss uses localStorage forever. */
const SESSION_KEY = 'savantle-ew-apology-session-opened';

export default function DowntimeApologyModal() {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!isEffectivelyWildDowntimeApologyWindow()) return;
    try {
      if (localStorage.getItem(STORAGE_KEY)) return;
      if (sessionStorage.getItem(SESSION_KEY)) return;
      sessionStorage.setItem(SESSION_KEY, '1');
    } catch {
      return;
    }
    setOpen(true);
  }, []);

  const dismiss = useCallback(() => {
    try {
      localStorage.setItem(STORAGE_KEY, '1');
    } catch {
      /* ignore */
    }
    setOpen(false);
  }, []);

  return (
    <Modal open={open} onClose={dismiss} title="Quick note">
      <div className="text-sv-text text-sm leading-relaxed">
        <p>Howdy!</p>
        <p className="mt-6">
          My apologies for the downtime earlier! I wasn&apos;t expecting a shoutout on Effectively Wild today and the extra traffic temporarily brought down my server. It should be working again!
        </p>
        <p className="mt-6">
          Best,
          <br />
          Andrew
        </p>
      </div>
      <button
        type="button"
        onClick={dismiss}
        className="mt-4 w-full rounded-lg bg-sv-accent py-2.5 text-sm font-semibold text-white hover:opacity-90 transition-opacity"
      >
        Got it
      </button>
    </Modal>
  );
}
