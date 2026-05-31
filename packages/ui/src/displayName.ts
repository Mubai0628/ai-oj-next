const DISPLAY_NAME_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';

export function createDefaultDisplayName(prefix = 'User'): string {
  const values = new Uint32Array(8);
  const cryptoApi = globalThis.crypto;

  if (cryptoApi?.getRandomValues) {
    cryptoApi.getRandomValues(values);
  } else {
    for (let index = 0; index < values.length; index += 1) {
      values[index] = Math.floor(Math.random() * DISPLAY_NAME_CHARS.length);
    }
  }

  const suffix = Array.from(values, (value) => DISPLAY_NAME_CHARS[value % DISPLAY_NAME_CHARS.length]).join('');
  return `${prefix}${suffix}`;
}
