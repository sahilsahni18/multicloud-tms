import { configureStore } from '@reduxjs/toolkit';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { RoleName } from '../../api/types';
import authReducer, { sessionExpired, sessionRefreshed } from './authSlice';
import { RequireAuth, RequireRole } from './guards';

function renderAt(path: string, roles: RoleName[] | null) {
  const store = configureStore({ reducer: { auth: authReducer } });
  if (roles) {
    store.dispatch(
      sessionRefreshed({
        accessToken: 't',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: { id: 1, email: 'x@y.dev', fullName: 'X', roles },
      }),
    );
  } else {
    store.dispatch(sessionExpired());
  }
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/login" element={<p>login page</p>} />
          <Route element={<RequireAuth />}>
            <Route element={<RequireRole roles={['ADMIN']} />}>
              <Route path="/admin" element={<p>admin page</p>} />
            </Route>
          </Route>
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}

describe('route guards', () => {
  it('sends signed-out users to login', () => {
    renderAt('/admin', null);
    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  it('blocks roles without access', () => {
    renderAt('/admin', ['DEVELOPER']);
    expect(screen.getByText('Not allowed')).toBeInTheDocument();
    expect(screen.queryByText('admin page')).not.toBeInTheDocument();
  });

  it('lets the right role through', () => {
    renderAt('/admin', ['ADMIN']);
    expect(screen.getByText('admin page')).toBeInTheDocument();
  });
});
