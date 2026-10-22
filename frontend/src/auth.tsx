import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, setAccessToken } from './api';
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
  function persist(auth: Auth | null) {
    setAccessToken(auth?.accessToken || null);
    setSession(auth);
    if (auth) sessionStorage.setItem(key, JSON.stringify(auth));
    else sessionStorage.removeItem(key);
  }
  function signOut() {
    persist(null);
    client.clear();
  }
  useEffect(() => {
    const clear = () => {
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
