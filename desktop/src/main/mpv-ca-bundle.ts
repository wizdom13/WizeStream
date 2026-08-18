import path from 'node:path';
import { readFile, writeFile } from 'node:fs/promises';
import { rootCertificates } from 'node:tls';

const BUNDLE_FILE_NAME = 'mpv-ca-bundle.pem';

export async function ensureMpvCaBundle(
  userDataDirectory: string,
  certificates: readonly string[] = rootCertificates,
): Promise<string> {
  const trustedCertificates = certificates.filter((certificate) =>
    certificate.includes('-----BEGIN CERTIFICATE-----')
      && certificate.includes('-----END CERTIFICATE-----'));
  if (trustedCertificates.length === 0) {
    throw new Error('Electron did not provide any trusted root certificates');
  }

  const bundlePath = path.join(userDataDirectory, BUNDLE_FILE_NAME);
  const bundle = `${trustedCertificates.join('\n')}\n`;
  try {
    if (await readFile(bundlePath, 'utf8') === bundle) return bundlePath;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
  }
  await writeFile(bundlePath, bundle, { encoding: 'utf8', mode: 0o600 });
  return bundlePath;
}
