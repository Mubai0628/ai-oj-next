import { i18n } from './locale';

const EXACT_MESSAGE_KEYS: Record<string, string> = {
  'Invalid account or password': 'errors.invalidCredentials',
  'Account is disabled. Please contact an administrator.': 'errors.accountDisabled',
  'User is disabled': 'errors.accountDisabled',
  'Account already exists': 'errors.accountExists',
  'Current password is incorrect': 'errors.currentPasswordIncorrect',
  'Refresh token is invalid': 'errors.refreshTokenInvalid',
  'Refresh token required': 'errors.tokenRequired',
  'Bearer token required': 'errors.tokenRequired',
  'Invalid token': 'errors.invalidToken',
  'Public registration only supports student or teacher roles': 'errors.publicRegistrationRole',
  'Invalid roles': 'errors.invalidRoles',
  'Student and teacher roles are mutually exclusive': 'errors.roleConflict',
  'Problem not found': 'errors.problemNotFound',
  'Submission not found': 'errors.submissionNotFound',
  'Language is required': 'errors.languageRequired',
  'Cannot query other users\' submissions': 'errors.cannotQuerySubmission',
  'Cannot read other users\' submissions': 'errors.cannotReadSubmission',
  'AI quota exceeded': 'errors.aiQuotaExceeded',
  'Only .zip testcase packages are supported': 'errors.testcaseZipOnly',
  'Testcase package manifest.json is required': 'errors.testcaseManifestRequired',
  'Testcase manifest cases are required': 'errors.testcaseManifestCasesRequired',
  'Not all testcase chunks have been uploaded': 'errors.testcaseChunksIncomplete',
  'Testcase package file name is required': 'errors.testcaseFileNameRequired',
  'Testcase package file size exceeds limit': 'errors.testcaseFileTooLarge',
  'Chunk size exceeds configured limit': 'errors.testcaseChunkTooLarge'
};

const PREFIX_MESSAGE_KEYS: Array<[string, string]> = [
  ['Unsupported language:', 'errors.unsupportedLanguage'],
  ['Testcase upload has failed:', 'errors.testcaseUploadFailed'],
  ['Unsafe testcase zip path:', 'errors.testcaseUnsafePath'],
  ['Missing testcase upload chunk:', 'errors.testcaseMissingChunk'],
  ['Uploaded testcase chunk is missing on disk:', 'errors.testcaseMissingChunk'],
  ['Duplicate testcase zip entry:', 'errors.testcaseDuplicateEntry'],
  ['Testcase zip entry is too large:', 'errors.testcaseEntryTooLarge']
];

function translate(key: string) {
  const localized = i18n.global.t(key);
  return localized && localized !== key ? localized : undefined;
}

export function resolveApiErrorMessage(code: number, fallback: string): string | undefined {
  const exactKey = EXACT_MESSAGE_KEYS[fallback];
  if (exactKey) {
    const localized = translate(exactKey);
    if (localized) return localized;
  }

  const prefix = PREFIX_MESSAGE_KEYS.find(([prefixText]) => fallback.startsWith(prefixText));
  if (prefix) {
    const localized = translate(prefix[1]);
    if (localized) return localized;
  }

  const codeMessage = translate(`errors.${code}`);
  if (codeMessage) return codeMessage;
  return translate('errors.unknown') ?? fallback;
}
