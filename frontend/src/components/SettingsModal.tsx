import Modal from './Modal';
import { Settings } from '../types';

interface SettingsModalProps {
  open: boolean;
  onClose: () => void;
  settings: Settings;
  onUpdate: (partial: Partial<Settings>) => void;
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex items-center justify-between py-2">
      <span className="text-sm text-sv-text">{label}</span>
      <button
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        style={{ backgroundColor: checked ? 'var(--sv-accent)' : 'var(--sv-toggle-off)' }}
        className="relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-sv-accent"
      >
        <span
          className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
            checked ? 'translate-x-6' : 'translate-x-1'
          }`}
        />
      </button>
    </div>
  );
}

export default function SettingsModal({ open, onClose, settings, onUpdate }: SettingsModalProps) {
  return (
    <Modal open={open} onClose={onClose} title="Settings">
      <div className="space-y-1 divide-y divide-sv-border">
        <Toggle
          label="Dark Mode"
          checked={settings.darkMode}
          onChange={v => onUpdate({ darkMode: v })}
        />
        <Toggle
          label="High Contrast"
          checked={settings.highContrast}
          onChange={v => onUpdate({ highContrast: v })}
        />
      </div>
    </Modal>
  );
}
