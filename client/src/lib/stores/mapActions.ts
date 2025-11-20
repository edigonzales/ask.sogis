import { writable } from 'svelte/store';
import type { MapAction } from '$lib/api/chat-response';

function createMapActionBus() {
  const { subscribe, set } = writable<MapAction[]>([]);

  return {
    subscribe,
    dispatch(actions: MapAction[] | undefined | null) {
      if (!actions || actions.length === 0) {
        return;
      }
      // Spread to make sure downstream subscribers see a new reference.
      set([...actions]);
    },
    clear() {
      set([]);
    }
  };
}

export const mapActionBus = createMapActionBus();
