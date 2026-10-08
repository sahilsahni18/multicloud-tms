import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import { errorMessage, http, problemOf, refreshSession } from '../../api/http';
import type { AuthResponse, AuthUser, Problem } from '../../api/types';

/**
 * Session state. The access token lives only in memory (never localStorage);
 * the refresh token is an httpOnly cookie the page cannot read.
 * status 'unknown' = still checking on start-up whether a session exists.
 */
export interface AuthState {
  status: 'unknown' | 'authenticated' | 'anonymous';
  accessToken: string | null;
  expiresAt: number | null;
  user: AuthUser | null;
}

const initialState: AuthState = { status: 'unknown', accessToken: null, expiresAt: null, user: null };

export const restoreSession = createAsyncThunk('auth/restore', async () => refreshSession());

// Rejections carry the API's problem body so the form can show its detail.
export const login = createAsyncThunk<AuthResponse, { email: string; password: string }, { rejectValue: Problem }>(
  'auth/login',
  async (credentials, { rejectWithValue }) => {
    try {
      return (await http.post<AuthResponse>('/auth/login', credentials)).data;
    } catch (e) {
      return rejectWithValue(problemOf(e) ?? { title: 'Error', status: 0, detail: errorMessage(e) });
    }
  },
);

export const register = createAsyncThunk<
  AuthResponse,
  { email: string; password: string; fullName: string },
  { rejectValue: Problem }
>('auth/register', async (data, { rejectWithValue }) => {
  try {
    return (await http.post<AuthResponse>('/auth/register', data)).data;
  } catch (e) {
    return rejectWithValue(problemOf(e) ?? { title: 'Error', status: 0, detail: errorMessage(e) });
  }
});

export const logout = createAsyncThunk('auth/logout', async () => {
  await http.post('/auth/logout').catch(() => undefined);
});

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    sessionRefreshed(state, action: PayloadAction<AuthResponse>) {
      apply(state, action.payload);
    },
    sessionExpired(state) {
      Object.assign(state, initialState, { status: 'anonymous' });
    },
    profileRenamed(state, action: PayloadAction<string>) {
      if (state.user) state.user.fullName = action.payload;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(restoreSession.fulfilled, (state, action) => apply(state, action.payload))
      .addCase(restoreSession.rejected, (state) => {
        state.status = 'anonymous';
      })
      .addCase(login.fulfilled, (state, action) => apply(state, action.payload))
      .addCase(register.fulfilled, (state, action) => apply(state, action.payload))
      .addCase(logout.fulfilled, (state) => {
        Object.assign(state, initialState, { status: 'anonymous' });
      });
  },
});

function apply(state: AuthState, auth: AuthResponse) {
  state.status = 'authenticated';
  state.accessToken = auth.accessToken;
  state.expiresAt = Date.now() + auth.expiresIn * 1000;
  state.user = auth.user;
}

export const { sessionRefreshed, sessionExpired, profileRenamed } = authSlice.actions;
export default authSlice.reducer;
