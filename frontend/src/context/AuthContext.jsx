import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authApi, setUnauthorizedHandler, tokenStore } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(Boolean(tokenStore.get()));

  const clearSession = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(clearSession);
    if (!tokenStore.get()) return;
    authApi
      .me()
      .then(setUser)
      .catch(clearSession)
      .finally(() => setLoading(false));
  }, [clearSession]);

  const startSession = (res) => {
    tokenStore.set(res.token);
    setUser(res.user);
    return res.user;
  };

  const login = async (email, password) => startSession(await authApi.login({ email, password }));

  const register = async (fullName, email, password) =>
    startSession(await authApi.register({ fullName, email, password }));

  const logout = async () => {
    try {
      await authApi.logout(); // revokes the token on the server (Redis blacklist)
    } catch {
      /* token may already be invalid; sign out locally anyway */
    }
    clearSession();
  };

  const refreshUser = useCallback(async () => {
    const me = await authApi.me();
    setUser(me);
    return me;
  }, []);

  const value = useMemo(
    () => ({ user, loading, isPremium: Boolean(user?.premium), login, register, logout, refreshUser }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [user, loading, refreshUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
