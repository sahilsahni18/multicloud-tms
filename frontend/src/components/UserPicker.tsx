import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import { useEffect, useState } from 'react';
import { useLookupUsersQuery } from '../api/api';
import type { UserRef } from '../api/types';

interface Props {
  /** Omit when the picker sits under its own caption. */
  label?: string;
  value: UserRef | null;
  onChange: (user: UserRef | null) => void;
  /** Restrict choices to these users (e.g. project members) instead of searching everyone. */
  options?: UserRef[];
  disabled?: boolean;
}

/** Searches users as you type (admin / PM lookup endpoint), or picks from a fixed list. */
export default function UserPicker({ label, value, onChange, options, disabled }: Props) {
  const [input, setInput] = useState('');
  const [query, setQuery] = useState('');
  useEffect(() => {
    const t = setTimeout(() => setQuery(input), 250);
    return () => clearTimeout(t);
  }, [input]);
  const { data = [], isFetching } = useLookupUsersQuery(query, { skip: !!options });

  return (
    <Autocomplete
      size="small"
      options={options ?? data}
      value={value}
      disabled={disabled}
      loading={!options && isFetching}
      onChange={(_e, user) => onChange(user)}
      onInputChange={(_e, text) => setInput(text)}
      filterOptions={options ? undefined : (x) => x}
      getOptionLabel={(u) => u.fullName}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      renderOption={(props, u) => (
        <li {...props} key={u.id}>
          {u.fullName}
          <span style={{ color: '#59636e', marginLeft: 8, fontSize: 12 }}>{u.email}</span>
        </li>
      )}
      renderInput={(params) => (
        <TextField {...params} label={label} placeholder={label ? undefined : 'Unassigned'} />
      )}
    />
  );
}
