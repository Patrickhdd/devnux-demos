import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, ApiError, dateTime, loadJson, money, saveJson } from './api';

type Tab = 'refunds' | 'tickets' | 'conversations';
const KEY = 'harbor.staffKey';
const label = (s: string) => (s.charAt(0).toUpperCase() + s.slice(1)).replace('_', ' ');

export default function Staff() {
  const [key, setKey] = useState<string | null>(() => loadJson<string>(KEY));
  if (!key) return <StaffSignIn onKey={(k) => { saveJson(KEY, k); setKey(k); }} />;
  return <Console staffKey={key} onSignOut={() => { saveJson(KEY, null); setKey(null); }} />;
}

function StaffSignIn({ onKey }: { onKey: (k: string) => void }) {
  const [value, setValue] = useState('');
  const [error, setError] = useState('');
  async function submit(e: FormEvent) {
    e.preventDefault();
    try { await api.staff(value).overview(); onKey(value); } catch (err) { setError((err as Error).message); }
  }
  return (
    <form className="pick narrow" onSubmit={submit}>
      <h1>Staff console</h1>
      <p className="lead">Review refunds the assistant submitted, pick up escalated chats, and read exactly what the assistant did.</p>
      <label>Staff key<input value={value} onChange={(e) => setValue(e.target.value)} placeholder="demo-staff" autoFocus /></label>
      {error && <p className="error">{error}</p>}
      <button className="primary">Open console</button>
      <p className="hint">The demo key is <code>demo-staff</code>.</p>
    </form>
  );
}

function Console({ staffKey, onSignOut }: { staffKey: string; onSignOut: () => void }) {
  const staff = api.staff(staffKey);
  const [tab, setTab] = useState<Tab>('refunds');
  const [overview, setOverview] = useState<any>(null);
  const [rows, setRows] = useState<any[]>([]);
  const [openConversation, setOpenConversation] = useState<string | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      setError('');
      setOverview(await staff.overview());
      setRows(tab === 'refunds' ? await staff.refunds() : tab === 'tickets' ? await staff.tickets() : await staff.conversations());
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) onSignOut(); else setError((err as Error).message);
    }
  }, [tab, staffKey]);
  useEffect(() => { load(); }, [load]);

  const act = async (fn: () => Promise<unknown>) => { try { await fn(); } catch (err) { setError((err as Error).message); } load(); };

  return (
    <div className="console">
      <div className="console-head">
        <h1>Staff console</h1>
        <div className="actions"><button className="ghost" onClick={load}>Refresh</button><button className="ghost" onClick={onSignOut}>Lock</button></div>
      </div>
      {overview && (
        <div className="stats">
          <div><strong>{overview.pendingRefunds}</strong><span>refunds to review</span></div>
          <div><strong>{overview.openTickets}</strong><span>open tickets</span></div>
          <div><strong>{overview.conversations}</strong><span>conversations</span></div>
          <div><strong>{(overview.inputTokens + overview.outputTokens).toLocaleString('en-US')}</strong><span>tokens used</span></div>
        </div>
      )}
      <div className="tabs" role="tablist">
        {(['refunds', 'tickets', 'conversations'] as Tab[]).map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} className={tab === t ? 'on' : ''} onClick={() => { setTab(t); setOpenConversation(null); }}>
            {t === 'refunds' ? 'Refund requests' : t === 'tickets' ? 'Escalations' : 'Conversations'}
          </button>
        ))}
      </div>
      {error && <p className="error">{error}</p>}

      {openConversation ? <Transcript staffKey={staffKey} id={openConversation} onBack={() => setOpenConversation(null)} /> : (
        <div className="cards">
          {rows.length === 0 && <p className="muted">Nothing here yet. Try a chat in the customer view.</p>}
          {tab === 'refunds' && rows.map((r) => (
            <article key={r.id} className="card">
              <div className="row"><strong>{r.reference} · {r.orderNumber}</strong><span className={`tag ${r.status}`}>{label(r.status)}</span></div>
              <p>{r.customer} asked for {money(r.amountCents)}: “{r.reason}”</p>
              <small>{dateTime(r.createdAt)}{r.decidedBy && ` · decided by ${r.decidedBy}`}</small>
              <div className="actions">
                {r.status === 'pending_review' && <>
                  <button className="primary" onClick={() => act(() => staff.decide(r.id, 'approved'))}>Approve</button>
                  <button className="ghost" onClick={() => act(() => staff.decide(r.id, 'denied'))}>Deny</button>
                </>}
                {r.conversationId && <button className="link" onClick={() => setOpenConversation(r.conversationId)}>View chat</button>}
              </div>
            </article>
          ))}
          {tab === 'tickets' && rows.map((t) => (
            <article key={t.id} className="card">
              <div className="row"><strong>{t.reference} · {t.customer}</strong><span className={`tag ${t.status === 'open' ? t.priority : 'resolved'}`}>{label(t.status === 'open' ? t.priority : 'resolved')}</span></div>
              <p>{t.summary}</p>
              <small>{t.email} · {dateTime(t.createdAt)}</small>
              <div className="actions">
                {t.status === 'open' && <button className="primary" onClick={() => act(() => staff.resolve(t.id))}>Mark resolved</button>}
                <button className="link" onClick={() => setOpenConversation(t.conversationId)}>View chat</button>
              </div>
            </article>
          ))}
          {tab === 'conversations' && rows.map((c) => (
            <button key={c.id} className="card clickable" onClick={() => setOpenConversation(c.id)}>
              <div className="row"><strong>{c.customer}</strong><span className={`tag ${c.status}`}>{label(c.status)}</span></div>
              <p>“{c.firstMessage}”</p>
              <small>{dateTime(c.updatedAt)} · {c.toolCalls} tool {c.toolCalls === 1 ? 'call' : 'calls'}</small>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function Transcript({ staffKey, id, onBack }: { staffKey: string; id: string; onBack: () => void }) {
  const [data, setData] = useState<any>(null);
  useEffect(() => { api.staff(staffKey).conversation(id).then(setData).catch(() => setData(null)); }, [id]);
  if (!data) return <p className="muted">Loading…</p>;
  return (
    <div className="transcript">
      <button className="link" onClick={onBack}>← Back</button>
      <h2>{data.customer} <small>{data.email}</small></h2>
      <p className="muted">{(data.inputTokens + data.outputTokens).toLocaleString('en-US')} tokens · status {data.status}</p>
      <ol>
        {data.events.map((e: any) => (
          <li key={e.id} className={`ev ${e.kind}`}>
            <span className="who">{e.kind === 'customer' ? data.customer : e.kind === 'tool' ? 'Tool' : e.kind === 'error' ? 'Error' : 'Assistant'}</span>
            {e.kind === 'tool' ? (
              <details>
                <summary><code>{e.payload.name}</code> {e.payload.summary}</summary>
                <pre>{JSON.stringify(e.payload.input, null, 2)}</pre>
              </details>
            ) : <p>{e.payload.text}</p>}
            <time>{dateTime(e.createdAt)}</time>
          </li>
        ))}
      </ol>
    </div>
  );
}
