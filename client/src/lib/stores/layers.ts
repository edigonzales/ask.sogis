import { writable } from 'svelte/store';

export type TocLayer = {
  id: string;
  label: string;
  visible: boolean;
  type?: string;
};

function createLayerStore() {
  const { subscribe, set, update } = writable<TocLayer[]>([]);

  return {
    subscribe,
    upsertLayer(layer: TocLayer) {
      update((layers) => {
        const index = layers.findIndex((item) => item.id === layer.id);
        if (index >= 0) {
          const next = layers.slice();
          next[index] = { ...next[index], ...layer };
          return next;
        }
        return [...layers, layer];
      });
    },
    removeLayer(id: string) {
      update((layers) => layers.filter((layer) => layer.id !== id));
    },
    setVisibility(id: string, visible: boolean) {
      update((layers) =>
        layers.map((layer) => (layer.id === id ? { ...layer, visible } : layer))
      );
    },
    clear() {
      set([]);
    }
  };
}

export const layerStore = createLayerStore();
