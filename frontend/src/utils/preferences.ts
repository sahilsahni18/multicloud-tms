const PAGE_SIZE_KEY = 'trackflow.pageSize';
export const PAGE_SIZES = [20, 50, 100];

/** Per-browser preference; falls back to 20 when storage is unavailable. */
export function getPreferredPageSize(): number {
  try {
    const value = Number(localStorage.getItem(PAGE_SIZE_KEY));
    return PAGE_SIZES.includes(value) ? value : 20;
  } catch {
    return 20;
  }
}

export function setPreferredPageSize(size: number) {
  try {
    localStorage.setItem(PAGE_SIZE_KEY, String(size));
  } catch {
    // storage blocked: the preference lasts for this page only
  }
}
