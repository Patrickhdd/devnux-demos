// Small typed client for the support agent API.

export interface Customer { id: number; fullName: string; email: string }
export interface Session { token: string; customer: Customer }
export interface Order { orderNumber: string; status: string; placedAt: string; deliveredAt: string | null; items: string; totalCents: number }

export type AgentEvent =
  | { type: 'text'; delta: string }
  | { type: 'tool_started'; id: string; name: string }
  | { type: 'tool_finished'; id: string; name: string; summary: string; ok: boolean }
  | { type: 'done'; reply: string; conversationStatus: string }
  | { type: 'error'; message: string };

export interface StoredEvent { id: number; kind: 'customer' | 'agent' | 'tool' | 'error'; payload: any; createdAt: string }

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

async function request<T>(path: string, init: RequestInit = {}, headers: Record<string, string> = {}): Promise<T> {
  const h: Record<string, string> = { Accept: 'application/json', ...headers };
  if (init.body) h['Content-Type'] = 'application/json';
  const res = await fetch(`/api${path}`, { ...init, headers: h });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(res.status, body.error ?? `Request failed (${res.status})`);
  }
  return res.json() as Promise<T>;
}

const bearer = (s: Session) => ({ Authorization: `Bearer ${s.token}` });

export const api = {
  health: () => request<{ agentEnabled: boolean; model: string }>('/health'),
  customers: () => request<Customer[]>('/demo/customers'),
  signIn: (customerId: number) => request<Session>('/session', { method: 'POST', body: JSON.stringify({ customerId }) }),
  orders: (s: Session) => request<Order[]>('/me/orders', {}, bearer(s)),
  conversation: (s: Session, id: string) =>
    request<{ id: string; status: string; events: StoredEvent[] }>(`/conversations/${id}`, {}, bearer(s)),
  newConversation: (s: Session) => request<{ id: string }>('/conversations', { method: 'POST' }, bearer(s)),

  /** Sends a message and calls onEvent for each line of the streamed reply. */
  async send(s: Session, conversationId: string, text: string, onEvent: (e: AgentEvent) => void) {
    const res = await fetch(`/api/conversations/${conversationId}/messages`, {
      method: 'POST',
      headers: { ...bearer(s), 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    });
    if (!res.ok || !res.body) {
      const body = await res.json().catch(() => ({}));
      throw new ApiError(res.status, body.error ?? `Request failed (${res.status})`);
    }
    const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = '';
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += value;
      let nl;
      while ((nl = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, nl).trim();
        buffer = buffer.slice(nl + 1);
        if (line) onEvent(JSON.parse(line) as AgentEvent);
      }
    }
  },

  staff: (key: string) => {
    const h = { 'x-staff-key': key };
    return {
      overview: () => request<{ conversations: number; pendingRefunds: number; openTickets: number; inputTokens: number; outputTokens: number }>('/staff/overview', {}, h),
      refunds: () => request<any[]>('/staff/refunds', {}, h),
      decide: (id: number, decision: 'approved' | 'denied') =>
        request(`/staff/refunds/${id}/decision`, { method: 'POST', body: JSON.stringify({ decision, staffName: 'Support team' }) }, h),
      tickets: () => request<any[]>('/staff/tickets', {}, h),
      resolve: (id: number) => request(`/staff/tickets/${id}/resolve`, { method: 'POST' }, h),
      conversations: () => request<any[]>('/staff/conversations', {}, h),
      conversation: (id: string) => request<any>(`/staff/conversations/${id}`, {}, h),
    };
  },
};

export const money = (cents: number) => (cents / 100).toLocaleString('en-US', { style: 'currency', currency: 'USD' });
export const shortDate = (iso: string) => new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
export const dateTime = (iso: string) =>
  new Date(iso).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });

export function loadJson<T>(key: string): T | null {
  try { return JSON.parse(localStorage.getItem(key) ?? 'null') as T | null; } catch { return null; }
}
export function saveJson(key: string, value: unknown) {
  try { value == null ? localStorage.removeItem(key) : localStorage.setItem(key, JSON.stringify(value)); } catch { /* private mode */ }
}
