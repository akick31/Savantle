import { useEffect, useRef } from 'react';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}

export default function Modal({ open, onClose, title, children }: ModalProps) {
  const backdropRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : '';
    return () => { document.body.style.overflow = ''; };
  }, [open]);

  useEffect(() => {
    function handleKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    if (open) window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      ref={backdropRef}
      className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-black/60"
      onClick={e => { if (e.target === backdropRef.current) onClose(); }}
    >
      <div className="w-full max-w-[380px] bg-sv-card rounded-xl border border-sv-border shadow-xl max-h-[85vh] overflow-y-auto">
        <div className="flex items-center justify-between p-4 border-b border-sv-border">
          <h2 className="text-lg font-bold text-sv-text">{title}</h2>
          <button
            onClick={onClose}
            className="text-sv-muted hover:text-sv-text transition-colors text-2xl leading-none p-1"
            aria-label="Close"
          >
            &times;
          </button>
        </div>
        <div className="p-4">{children}</div>
      </div>
    </div>
  );
}
