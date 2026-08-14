const compactNumber = new Intl.NumberFormat('en', {
  notation: 'compact',
  maximumFractionDigits: 1,
});

export function subscriberCountLabel(value?: number | null): string | undefined {
  if (value === undefined || value === null || !Number.isSafeInteger(value) || value < 0) return undefined;
  const count = compactNumber.format(value).replace('K', 'k').replace('M', 'm').replace('B', 'b');
  return `${count} ${value === 1 ? 'subscriber' : 'subscribers'}`;
}
