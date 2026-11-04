import { useEffect, useState } from 'react';
import { api, loadJson, saveJson, type Customer, type Session } from './api';
import Chat from './Chat';
import Staff from './Staff';

const SESSION_KEY = 'harbor.session';

function Logo() {
  return (
    <a className="brand" href="/">
      <svg viewBox="7 5 18 22" aria-hidden="true"><path d="M16 5l7 11h-4l5 9H8l5-9H9z" /><path d="M14.5 25h3v2h-3z" /></svg>
      Harbor <span className="amp">&amp;</span> Pine
    </a>
  );
}

export default function App() {
  const isStaff = window.location.pathname.startsWith('/staff');
  const [session, setSession] = useState<Session | null>(() => loadJson<Session>(SESSION_KEY));

  const signOut = () => { saveJson(SESSION_KEY, null); saveJson('harbor.conversation', null); setSession(null); };

  return (
    <div className="shell">
      <p className="strip">{isStaff ? 'Staff only. Everything here is demo data.' : 'Help centre. Ask about an order, a return or delivery.'}</p>
      <header className="top">
        <Logo />
        <nav>
          {isStaff ? <a href="/">Customer view</a> : <a href="/staff">Staff console</a>}
          {!isStaff && session && (
            <>
              <span className="who">{session.customer.fullName}</span>
              <button className="link" onClick={signOut}>Sign out</button>
            </>
          )}
        </nav>
      </header>
      <main>
        {isStaff ? <Staff /> : session ? <Chat session={session} onExpired={signOut} /> : (
          <PickCustomer onPick={(s) => { saveJson(SESSION_KEY, s); setSession(s); }} />
        )}
      </main>
      <footer>
        <span>Harbor &amp; Pine is a made-up shop for this demo.</span>
        <span>Built by <a href="https://devnux.tech">Devnux</a> with a Claude agent, Node.js, PostgreSQL and React</span>
      </footer>
    </div>
  );
}

function PickCustomer({ onPick }: { onPick: (s: Session) => void }) {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [error, setError] = useState('');
  useEffect(() => { api.customers().then(setCustomers).catch((e) => setError(e.message)); }, []);

  return (
    <section className="pick">
      <h1>Need a hand with an order?</h1>
      <p className="lead">Our assistant can look up your orders, explain returns and delivery, and start a refund for our team to check. If you'd rather talk to a person, just say so.</p>
      <h2>Pick a demo customer</h2>
      {error && <p className="error">{error}</p>}
      <div className="people">
        {customers.map((c) => (
          <button key={c.id} className="person" onClick={() => api.signIn(c.id).then(onPick).catch((e) => setError(e.message))}>
            <span className="avatar">{c.fullName.split(' ').map((p) => p[0]).join('')}</span>
            <span><strong>{c.fullName}</strong><small>{c.email}</small></span>
          </button>
        ))}
      </div>
    </section>
  );
}
