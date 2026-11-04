import { useState, type FormEvent } from 'react';
import { api, type Session } from './api';

const DEMO = [
  { label: 'Patient', name: 'Jordan Miller', email: 'patient@clinic.test' },
  { label: 'Doctor', name: 'Dr. Amal Haddad', email: 'amal.haddad@clinic.test' },
];

export default function Login({ onSignedIn }: { onSignedIn: (s: Session) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function run(fn: () => Promise<Session>) {
    setBusy(true); setError('');
    try { onSignedIn(await fn()); } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  function submit(e: FormEvent) {
    e.preventDefault();
    run(() => mode === 'login' ? api.login(email, password) : api.register(fullName, email, password));
  }

  return (
    <div className="login">
      <section className="login-intro">
        <h1>Book a doctor in under a minute</h1>
        <p>Pick a doctor, choose a free time, and you're booked. You'll see every appointment in one place and can cancel with one click.</p>
        <div className="demo-box">
          <b>Try the demo</b>
          <p>Sign in with one click as a sample patient or doctor. The password for both is <code>demo1234</code>.</p>
          <div className="demo-buttons">
            {DEMO.map((d) => (
              <button key={d.email} className="btn ghost" disabled={busy} onClick={() => run(() => api.login(d.email, 'demo1234'))}>
                <span>{d.label}</span><small>{d.name}</small>
              </button>
            ))}
          </div>
        </div>
      </section>

      <form className="card form" onSubmit={submit}>
        <h2>{mode === 'login' ? 'Sign in' : 'Create a patient account'}</h2>
        {mode === 'register' && (
          <label>Full name<input id="fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} required maxLength={120} autoComplete="name" /></label>
        )}
        <label>Email<input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" /></label>
        <label>Password<input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={mode === 'register' ? 8 : 1} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></label>
        {error && <p className="error" role="alert">{error}</p>}
        <button className="btn primary" disabled={busy}>{busy ? 'Please wait...' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
        <button type="button" className="link" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}>
          {mode === 'login' ? 'New patient? Create an account' : 'Already have an account? Sign in'}
        </button>
      </form>
    </div>
  );
}
