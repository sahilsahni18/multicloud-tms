import { describe, expect, it } from 'vitest';
import { humanize, timeAgo } from './format';

describe('format', () => {
  it('humanizes enum values', () => {
    expect(humanize('IN_PROGRESS')).toBe('In progress');
    expect(humanize('PROJECT_MANAGER')).toBe('Project manager');
    expect(humanize(undefined)).toBe('');
  });

  it('describes recent times relatively', () => {
    const now = Date.parse('2026-10-08T12:00:00Z');
    expect(timeAgo('2026-10-08T11:59:30Z', now)).toBe('just now');
    expect(timeAgo('2026-10-08T11:45:00Z', now)).toBe('15 min ago');
    expect(timeAgo('2026-10-08T09:00:00Z', now)).toBe('3 h ago');
    expect(timeAgo('2026-10-05T12:00:00Z', now)).toBe('3 d ago');
  });
});
