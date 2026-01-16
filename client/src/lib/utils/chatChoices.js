/**
 * @typedef {import('$lib/api/chat-response').ChatResponse} ChatResponse
 * @typedef {import('$lib/api/chat-response').Choice} Choice
 */

/**
 * @typedef {ChatResponse['steps'][number]['choices'] | undefined} RawChoices
 */

/**
 * @param {unknown} value
 * @returns {value is Choice}
 */
export function isChoice(value) {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const candidate = /** @type {Choice} */ (value);
  return typeof candidate.id === 'string' && typeof candidate.label === 'string';
}

/**
 * @param {unknown} value
 * @returns {Choice | null}
 */
function coerceChoice(value) {
  if (isChoice(value)) {
    return value;
  }
  if (!value || typeof value !== 'object') {
    return null;
  }
  const candidate = /** @type {Choice} */ (value);
  const data = /** @type {{ id?: unknown; label?: unknown }} */ (candidate.data);
  if (typeof data?.id === 'string' && typeof data?.label === 'string') {
    return {
      id: data.id,
      label: data.label,
      confidence: candidate.confidence ?? null,
      mapActions: candidate.mapActions,
      data: candidate.data
    };
  }
  return null;
}

/**
 * @param {RawChoices} rawChoices
 * @returns {Choice[]}
 */
export function normalizeChoices(rawChoices) {
  if (Array.isArray(rawChoices)) {
    const normalized = rawChoices.map(coerceChoice).filter((choice) => choice !== null);
    return dedupeChoices(normalized);
  }
  if (!rawChoices) {
    return [];
  }
  const values = Object.values(rawChoices);
  const normalized = values.map(coerceChoice).filter((choice) => choice !== null);
  return dedupeChoices(normalized);
}

/**
 * @param {Choice[]} choices
 * @returns {Choice[]}
 */
function dedupeChoices(choices) {
  const seen = new Set();
  return choices.filter((choice) => {
    if (seen.has(choice.id)) {
      return false;
    }
    seen.add(choice.id);
    return true;
  });
}
