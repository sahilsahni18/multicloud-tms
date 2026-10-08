import { describe, expect, it } from 'vitest';
import type { AuthResponse } from '../../api/types';
import reducer, { profileRenamed, sessionExpired, sessionRefreshed } from './authSlice';

const auth: AuthResponse = {
  accessToken: 'jwt',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: { id: 2, email: 'pm@trackflow.dev', fullName: 'Morgan Manager', roles: ['PROJECT_MANAGER'] },
};

describe('authSlice', () => {
  it('starts unknown until the session check finishes', () => {
    expect(reducer(undefined, { type: '@@init' }).status).toBe('unknown');
  });

  it('stores the token and user after a refresh', () => {
    const state = reducer(undefined, sessionRefreshed(auth));
    expect(state.status).toBe('authenticated');
    expect(state.accessToken).toBe('jwt');
    expect(state.user?.roles).toEqual(['PROJECT_MANAGER']);
    expect(state.expiresAt).toBeGreaterThan(Date.now());
  });

  it('forgets everything when the session expires', () => {
    const state = reducer(reducer(undefined, sessionRefreshed(auth)), sessionExpired());
    expect(state).toMatchObject({ status: 'anonymous', accessToken: null, user: null });
  });

  it('updates the display name after a profile change', () => {
    const state = reducer(reducer(undefined, sessionRefreshed(auth)), profileRenamed('Morgan M.'));
    expect(state.user?.fullName).toBe('Morgan M.');
  });
});
