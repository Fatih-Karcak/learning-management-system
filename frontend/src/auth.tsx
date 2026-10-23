import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, apiUrl, setAccessToken, setTokenRefresher } from './api';
import type { Auth, User } from './types';

const key = 'ileri.session';
function read(): Auth | null {
  try {
    const raw = sessionStorage.getItem(key);
    if (!raw) return null;
    const saved = JSON.parse(raw);
    if (
      typeof saved.accessToken !== 'string' ||
      !saved.user?.id ||
      !['STUDENT', 'INSTRUCTOR', 'ADMIN'].includes(saved.user.role)
    )
      return null;
    return saved;
  } catch {
    return null;
  }
}
const initial = read();
setAccessToken(initial?.accessToken || null);
const Context = createContext<{
  session: Auth | null;
  signIn: (auth: Auth) => void;
  signOut: () => void;
  updateUser: (user: User) => void;
}>({ session: null, signIn: () => {}, signOut: () => {}, updateUser: () => {} });
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Auth | null>(initial);
  const client = useQueryClient();
  const sessionEpoch = useRef(0);
  function persist(auth: Auth | null) {
    setAccessToken(auth?.accessToken || null);
    setSession(auth);
    if (auth) sessionStorage.setItem(key, JSON.stringify(auth));
    else sessionStorage.removeItem(key);
  }
  function signOut() {
    sessionEpoch.current++;
    try {
      const sid = JSON.parse(
        atob((session?.accessToken.split('.')[1] || '').replace(/-/g, '+').replace(/_/g, '/')),
      ).sid;
      if (sid) void api(`/users/me/sessions/${sid}`, { method: 'DELETE' }).catch(() => {});
    } catch {
      /* An invalid token is cleared locally. */
    }
    setTokenRefresher(null);
    persist(null);
    client.clear();
  }
  useEffect(() => {
    let active = true;
    const epoch = sessionEpoch.current;
    setTokenRefresher(
      session?.refreshToken
        ? async () => {
            const response = await fetch(apiUrl('/auth/refresh'), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ refreshToken: session.refreshToken }),
            });
            if (!response.ok) {
              if (response.status >= 500) throw new Error('Oturum servisine ulaşılamıyor.');
              return false;
            }
            const result = await response.json();
            if (
              !active ||
              epoch !== sessionEpoch.current ||
              !result.success ||
              !result.data?.accessToken
            )
              return false;
            persist(result.data as Auth);
            return true;
          }
        : null,
    );
    return () => {
      active = false;
      setTokenRefresher(null);
    };
  }, [session?.refreshToken]);
  useEffect(() => {
    const clear = () => {
      sessionEpoch.current++;
      setAccessToken(null);
      setSession(null);
      sessionStorage.removeItem(key);
      client.clear();
    };
    window.addEventListener('lms:unauthorized', clear);
    return () => window.removeEventListener('lms:unauthorized', clear);
  }, [client]);
  useEffect(() => {
    if (session?.user.id)
      api<User>('/users/me')
        .then((user) => {
          setSession((current) => {
            if (!current) return null;
            const next = { ...current, user };
            sessionStorage.setItem(key, JSON.stringify(next));
            return next;
          });
        })
        .catch(() => {});
  }, [session?.user.id]);
  return (
    <Context.Provider
      value={{
        session,
        signIn: (auth) => {
          sessionEpoch.current++;
          client.clear();
          persist(auth);
        },
        signOut,
        updateUser: (user) => {
          if (session) persist({ ...session, user });
        },
      }}
    >
      {children}
    </Context.Provider>
  );
}
export const useAuth = () => useContext(Context);
