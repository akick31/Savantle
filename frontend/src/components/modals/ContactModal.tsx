import { useState } from 'react';
import Modal from './Modal';
import { submitContact } from '../../services/api';

interface ContactModalProps {
  open: boolean;
  onClose: () => void;
}

export default function ContactModal({ open, onClose }: ContactModalProps) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [status, setStatus] = useState<'idle' | 'sending' | 'sent' | 'error'>('idle');
  const [errorMsg, setErrorMsg] = useState('');

  function reset() {
    setName('');
    setEmail('');
    setSubject('');
    setMessage('');
    setStatus('idle');
    setErrorMsg('');
  }

  function handleClose() {
    onClose();
    setTimeout(reset, 300);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setStatus('sending');
    setErrorMsg('');
    try {
      await submitContact({ name, email, subject, message });
      setStatus('sent');
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Something went wrong.');
      setStatus('error');
    }
  }

  const inputClass =
    'w-full bg-sv-bg border border-sv-border rounded-lg px-3 py-2 text-sm text-sv-text placeholder-sv-muted focus:outline-none focus:border-sv-accent transition-colors';

  return (
    <Modal open={open} onClose={handleClose} title="Contact Me">
      {status === 'sent' ? (
        <div className="text-center space-y-3 py-4">
          <div className="text-sv-accent text-3xl">&#10003;</div>
          <p className="text-sv-text font-medium">Message sent!</p>
          <p className="text-xs text-sv-muted">Thanks for reaching out. I'll get back to you soon.</p>
          <button
            onClick={handleClose}
            className="mt-2 px-4 py-2 bg-sv-accent text-sv-bg rounded-lg text-sm font-semibold hover:opacity-90 transition-opacity"
          >
            Close
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-3">
          <p className="text-xs text-sv-muted">
            Send suggestions, bug reports, or anything else.
          </p>

          <div className="space-y-1">
            <label className="text-xs font-medium text-sv-muted">Name</label>
            <input
              type="text"
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder="Your name"
              required
              maxLength={100}
              className={inputClass}
            />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-sv-muted">Email</label>
            <input
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="your@email.com"
              required
              maxLength={200}
              className={inputClass}
            />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-sv-muted">Subject</label>
            <input
              type="text"
              value={subject}
              onChange={e => setSubject(e.target.value)}
              placeholder="Bug report, suggestion, etc."
              required
              maxLength={200}
              className={inputClass}
            />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-sv-muted">Message</label>
            <textarea
              value={message}
              onChange={e => setMessage(e.target.value)}
              placeholder="Tell me what's on your mind..."
              required
              maxLength={5000}
              rows={4}
              className={`${inputClass} resize-none`}
            />
          </div>

          {status === 'error' && (
            <p className="text-xs text-sv-red">{errorMsg}</p>
          )}

          <button
            type="submit"
            disabled={status === 'sending'}
            className="w-full py-2.5 bg-sv-accent text-sv-bg rounded-lg font-semibold text-sm hover:opacity-90 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {status === 'sending' ? 'Sending...' : 'Send Message'}
          </button>
        </form>
      )}
    </Modal>
  );
}
