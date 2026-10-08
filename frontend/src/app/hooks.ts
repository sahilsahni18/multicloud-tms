import { useDispatch, useSelector } from 'react-redux';
import type { RoleName } from '../api/types';
import type { AppDispatch, RootState } from './store';

export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();

export function useCurrentUser() {
  return useAppSelector((s) => s.auth.user);
}

/** True if the signed-in user holds at least one of the roles. */
export function useHasRole(...roles: RoleName[]) {
  const user = useCurrentUser();
  return !!user && roles.some((r) => user.roles.includes(r));
}
