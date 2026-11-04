import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api, ApiError, loadJson, money, saveJson, shortDate, type Order, type Session } from './api';

interface ToolStep { id: string; name: string; summary?: string; ok?: boolean }
interface Bubble { id: string; from: 'customer' | 'agent'; text: string; steps: ToolStep[]; pending?: boolean; failed?: boolean }

const CONVERSATION_KEY = 'harbor.conversation';

const suggestions = [
  'Where is my backpack order?',
  'My boots from HP-10482 are too small. Can I return them?',
  'Can I still return the rain jacket I bought last month?',
  'Please cancel my tent order.',
];

const statusLabel: Record<string, string> = { processing: 'Processing', shipped: 'On the way', delivered: 'Delivered', cancelled: 'Cancelled' };

export default function Chat({ session, onExpired }: { session: Session; onExpired: () => void }) {
  const [orders, setOrders] = useState<Order[]>([]);
  const [agentEnabled, setAgentEnabled] = useState(true);
  const [bubbles, setBubbles] = useState<Bubble[]>([]);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [escalated, setEscalated] = useState(false);
  const conversation = useRef<{ id: string; customerId: number } | null>(loadJson(CONVERSATION_KEY));
  const logRef = useRef<HTMLDivElement>(null);

  const refreshOrders = () => api.orders(session).then(setOrders).catch((e) => { if (e instanceof ApiError && e.status === 401) onExpired(); });
  useEffect(() => { refreshOrders(); api.health().then((h) => setAgentEnabled(h.agentEnabled)).catch(() => {}); }, [session.token]);
  // Rebuild the chat after a page reload from what the server stored.
  useEffect(() => {
    const c = conversation.current;
    if (!c || c.customerId !== session.customer.id) return;
    api.conversation(session, c.id).then((conv) => {
      const restored: Bubble[] = [];
      let steps: ToolStep[] = [];
      for (const e of conv.events) {
        if (e.kind === 'customer') restored.push({ id: `e${e.id}`, from: 'customer', text: e.payload.text, steps: [] });
        else if (e.kind === 'tool') steps.push({ id: `e${e.id}`, name: e.payload.name, summary: e.payload.summary, ok: e.payload.ok });
        else { restored.push({ id: `e${e.id}`, from: 'agent', text: e.payload.text, steps, failed: e.kind === 'error' }); steps = []; }
      }
      setBubbles(restored);
      setEscalated(conv.status === 'escalated');
    }).catch(() => { conversation.current = null; saveJson(CONVERSATION_KEY, null); });
  }, [session.token]);
  useEffect(() => { logRef.current?.scrollTo({ top: logRef.current.scrollHeight, behavior: 'smooth' }); }, [bubbles]);

  const update = (id: string, fn: (b: Bubble) => Bubble) => setBubbles((bs) => bs.map((b) => (b.id === id ? fn(b) : b)));

  async function send(text: string) {
    text = text.trim();
    if (!text || busy) return;
    setDraft('');
    setBusy(true);
    const agentId = crypto.randomUUID();
    setBubbles((bs) => [...bs, { id: crypto.randomUUID(), from: 'customer', text, steps: [] },
      { id: agentId, from: 'agent', text: '', steps: [], pending: true }]);
    try {
      if (!conversation.current || conversation.current.customerId !== session.customer.id) {
        const c = await api.newConversation(session);
        conversation.current = { id: c.id, customerId: session.customer.id };
        saveJson(CONVERSATION_KEY, conversation.current);
      }
      await api.send(session, conversation.current.id, text, (e) => {
        if (e.type === 'text') update(agentId, (b) => ({ ...b, text: b.text + e.delta }));
        else if (e.type === 'tool_started') update(agentId, (b) => ({ ...b, steps: [...b.steps, { id: e.id, name: e.name }] }));
        else if (e.type === 'tool_finished') update(agentId, (b) => ({ ...b, steps: b.steps.map((s) => (s.id === e.id ? { ...s, summary: e.summary, ok: e.ok } : s)) }));
        else if (e.type === 'done') { update(agentId, (b) => ({ ...b, text: e.reply, pending: false })); setEscalated(e.conversationStatus === 'escalated'); }
        else if (e.type === 'error') update(agentId, (b) => ({ ...b, text: e.message, pending: false, failed: true }));
      });
      refreshOrders();
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) { conversation.current = null; saveJson(CONVERSATION_KEY, null); }
      update(agentId, (b) => ({ ...b, text: (err as Error).message, pending: false, failed: true }));
    } finally {
      setBusy(false);
    }
  }

  const startOver = () => { conversation.current = null; saveJson(CONVERSATION_KEY, null); setBubbles([]); setEscalated(false); };
  const onSubmit = (e: FormEvent) => { e.preventDefault(); send(draft); };

  return (
    <div className="help">
      <aside className="orders">
        <h2>Your orders</h2>
        <ul>
          {orders.map((o) => (
            <li key={o.orderNumber}>
              <div className="row"><strong>{o.orderNumber}</strong><span className={`tag ${o.status}`}>{statusLabel[o.status] ?? o.status}</span></div>
              <p>{o.items}</p>
              <small>
                {money(o.totalCents)} · ordered {shortDate(o.placedAt)}
                {o.deliveredAt && <> · delivered {shortDate(o.deliveredAt)}</>}
              </small>
            </li>
          ))}
        </ul>
      </aside>

      <section className="chat" aria-label="Chat with Harbor & Pine support">
        <div className="chat-head">
          <div>
            <h1>Support chat</h1>
            <p>Our assistant answers right away. A person reviews every refund.</p>
          </div>
          {bubbles.length > 0 && <button className="ghost" onClick={startOver} disabled={busy}>New chat</button>}
        </div>

        {!agentEnabled && (
          <p className="notice">The assistant isn't switched on for this server. Add <code>ANTHROPIC_API_KEY</code> to the API's environment and restart it.</p>
        )}

        <div className="log" ref={logRef} aria-live="polite">
          {bubbles.length === 0 && (
            <div className="empty">
              <p>Hi {session.customer.fullName.split(' ')[0]}, what can we help with today?</p>
              <div className="chips">
                {suggestions.map((s) => <button key={s} onClick={() => send(s)} disabled={busy || !agentEnabled}>{s}</button>)}
              </div>
            </div>
          )}
          {bubbles.map((b) => (
            <div key={b.id} className={`bubble ${b.from}${b.failed ? ' failed' : ''}`}>
              {b.steps.length > 0 && (
                <ul className="steps">
                  {b.steps.map((s) => (
                    <li key={s.id} className={s.summary ? (s.ok ? 'ok' : 'warn') : 'running'}>{s.summary ?? 'Working on it…'}</li>
                  ))}
                </ul>
              )}
              {b.text ? <p>{b.text}</p> : b.pending && <p className="typing" aria-label="Assistant is typing"><i /><i /><i /></p>}
            </div>
          ))}
          {escalated && <p className="handoff">A member of our team has your conversation and will reply by email.</p>}
        </div>

        <form className="composer" onSubmit={onSubmit}>
          <input value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="Type your question…" maxLength={2000}
            disabled={!agentEnabled} aria-label="Your message" />
          <button className="primary" disabled={busy || !draft.trim() || !agentEnabled}>Send</button>
        </form>
      </section>
    </div>
  );
}
