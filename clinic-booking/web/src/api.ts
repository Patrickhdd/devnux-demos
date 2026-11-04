// Small typed client for the clinic booking API.

export const CLINIC_ZONE = import.meta.env.VITE_CLINIC_ZONE ?? 'America/New_York';

export type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN';
export interface User { id: number; email: string; fullName: string; role: Role }
export interface Session { token: string; expiresAt: string; user: User }
export interface Doctor { id: number; name: string; specialty: string; bio: string; slotMinutes: number }
export interface Slot { startsAt: string; endsAt: string; available: boolean }
export interface Appointment {
  id: number; doctorId: number; doctorName: string; specialty: string;
  patientId: number; patientName: string; startsAt: string; endsAt: string;
  status: 'BOOKED' | 'CANCELLED' | 'COMPLETED'; reason: string | null;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

const SESSION_KEY = 'clinic.session';

export function loadSession(): Session | null {
  try {
    const s = JSON.parse(localStorage.getItem(SESSION_KEY) ?? 'null') as Session | null;
    return s && new Date(s.expiresAt) > new Date() ? s : null;
  } catch { return null; }
}
export function saveSession(s: Session | null) {
  try { s ? localStorage.setItem(SESSION_KEY, JSON.stringify(s)) : localStorage.removeItem(SESSION_KEY); } catch { /* ignore */ }
}

async function request<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (init.body) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`/api${path}`, { ...init, headers });
  if (!res.ok) {
    const problem = await res.json().catch(() => ({}));
    throw new ApiError(res.status, problem.detail ?? `Request failed (${res.status})`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  login: (email: string, password: string) =>
    request<Session>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  register: (fullName: string, email: string, password: string) =>
    request<Session>('/auth/register', { method: 'POST', body: JSON.stringify({ fullName, email, password }) }),
  doctors: () => request<Doctor[]>('/doctors'),
  slots: (doctorId: number, date: string) => request<Slot[]>(`/doctors/${doctorId}/slots?date=${date}`),
  book: (token: string, doctorId: number, startsAt: string, reason: string) =>
    request<Appointment>('/appointments', { method: 'POST', body: JSON.stringify({ doctorId, startsAt, reason }) }, token),
  mine: (token: string) => request<Appointment[]>('/appointments/mine', {}, token),
  cancel: (token: string, id: number) => request<Appointment>(`/appointments/${id}/cancel`, { method: 'POST' }, token),
  schedule: (token: string, from: string, days: number) =>
    request<Appointment[]>(`/doctor/schedule?from=${from}&days=${days}`, {}, token),
  complete: (token: string, id: number) =>
    request<Appointment>(`/doctor/appointments/${id}/complete`, { method: 'POST' }, token),
};

// Date helpers, always in the clinic's time zone.

export function todayInClinic(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: CLINIC_ZONE }).format(new Date());
}
export function addDays(isoDate: string, n: number): string {
  const d = new Date(`${isoDate}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
export const fmtTime = (iso: string) =>
  new Intl.DateTimeFormat('en-US', { timeZone: CLINIC_ZONE, hour: 'numeric', minute: '2-digit' }).format(new Date(iso));
export const fmtDay = (iso: string) =>
  new Intl.DateTimeFormat('en-US', { timeZone: CLINIC_ZONE, weekday: 'long', month: 'long', day: 'numeric' }).format(new Date(iso));
export const dayKey = (iso: string) =>
  new Intl.DateTimeFormat('en-CA', { timeZone: CLINIC_ZONE }).format(new Date(iso));
export function labelForDate(isoDate: string) {
  const d = new Date(`${isoDate}T12:00:00Z`);
  return {
    weekday: new Intl.DateTimeFormat('en-US', { weekday: 'short', timeZone: 'UTC' }).format(d),
    day: d.getUTCDate(),
    month: new Intl.DateTimeFormat('en-US', { month: 'short', timeZone: 'UTC' }).format(d),
  };
}
