import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { normalizeChoices } from '../../src/lib/utils/chatChoices.js';

describe('normalizeChoices', () => {
  it('returns valid choices from arrays', () => {
    const choices = [
      { id: 'one', label: 'Layer One' },
      { id: 'two', label: 'Layer Two' }
    ];

    assert.deepEqual(normalizeChoices(choices), choices);
  });

  it('returns values from object maps and ignores invalid entries', () => {
    const choices = {
      first: { id: 'one', label: 'Layer One' },
      second: { id: 'two', label: 'Layer Two' },
      invalid: { id: 'bad' }
    };

    assert.deepEqual(normalizeChoices(choices), [
      { id: 'one', label: 'Layer One' },
      { id: 'two', label: 'Layer Two' }
    ]);
  });

  it('uses data fallback when id/label are nested', () => {
    const choices = [
      {
        data: { id: 'nested', label: 'Nested Layer' },
        confidence: null
      }
    ];

    assert.deepEqual(normalizeChoices(choices), [
      {
        id: 'nested',
        label: 'Nested Layer',
        confidence: null,
        mapActions: undefined,
        data: { id: 'nested', label: 'Nested Layer' }
      }
    ]);
  });

  it('returns an empty array for null or undefined', () => {
    assert.deepEqual(normalizeChoices(null), []);
    assert.deepEqual(normalizeChoices(undefined), []);
  });

  it('deduplicates choices with the same id', () => {
    const choices = [
      { id: 'dup', label: 'Layer One' },
      { id: 'dup', label: 'Layer One Duplicate' },
      { id: 'unique', label: 'Layer Two' }
    ];

    assert.deepEqual(normalizeChoices(choices), [
      { id: 'dup', label: 'Layer One' },
      { id: 'unique', label: 'Layer Two' }
    ]);
  });
});
