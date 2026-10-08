import { ThemeProvider } from '@mui/material/styles';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { http } from '../../api/http';
import { store } from '../../app/store';
import { theme } from '../../theme';
import LoginPage from './LoginPage';

function renderLogin() {
  return render(
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<p>dashboard</p>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
}

describe('LoginPage', () => {
  afterEach(() => vi.restoreAllMocks());

  it('shows the API error when credentials are wrong', async () => {
    vi.spyOn(http, 'post').mockRejectedValue(
      new AxiosError('Unauthorized', '401', undefined, undefined, {
        status: 401,
        statusText: 'Unauthorized',
        headers: {},
        config: { headers: new AxiosHeaders() },
        data: { title: 'Unauthorized', status: 401, detail: 'Invalid email or password' },
      }),
    );
    renderLogin();
    await userEvent.type(screen.getByLabelText(/email/i), 'admin@trackflow.dev');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument();
  });

  it('signs in and goes to the dashboard', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({
      data: {
        accessToken: 'jwt',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: { id: 1, email: 'admin@trackflow.dev', fullName: 'Alex Admin', roles: ['ADMIN'] },
      },
    });
    renderLogin();
    await userEvent.type(screen.getByLabelText(/email/i), ' admin@trackflow.dev ');
    await userEvent.type(screen.getByLabelText(/password/i), 'Password@123');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('dashboard')).toBeInTheDocument();
    expect(post).toHaveBeenCalledWith('/auth/login', { email: 'admin@trackflow.dev', password: 'Password@123' });
    expect(store.getState().auth.user?.fullName).toBe('Alex Admin');
  });
});
