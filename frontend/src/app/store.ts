import { configureStore } from '@reduxjs/toolkit';
import { api } from '../api/api';
import { configureSession } from '../api/http';
import authReducer, { logout, sessionExpired, sessionRefreshed } from '../features/auth/authSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    [api.reducerPath]: api.reducer,
  },
  middleware: (getDefault) => getDefault().concat(api.middleware),
});

configureSession({
  getToken: () => store.getState().auth.accessToken,
  onRefreshed: (auth) => store.dispatch(sessionRefreshed(auth)),
  onSessionExpired: () => {
    store.dispatch(sessionExpired());
    store.dispatch(api.util.resetApiState());
  },
});

/** Clears cached API data on logout so the next user never sees it. */
export async function signOut() {
  await store.dispatch(logout());
  store.dispatch(api.util.resetApiState());
}

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
