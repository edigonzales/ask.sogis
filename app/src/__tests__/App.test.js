import { render, screen } from '@testing-library/svelte';
import App from '../App.svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('ol/Map', () => ({
  default: class {
    constructor(options) {
      this.options = options;
    }
    setTarget() {}
  }
}));

vi.mock('ol/View', () => ({
  default: class {
    constructor(options) {
      this.options = options;
    }
  }
}));

vi.mock('ol/layer/Tile', () => ({
  default: class {
    constructor(options) {
      this.options = options;
    }
  }
}));

vi.mock('ol/source/WMTS', () => {
  const resolutions = [1, 0.5, 0.25];
  return {
    default: class {
      constructor(options) {
        this.options = options;
      }
    },
    optionsFromCapabilities: () => ({
      tileGrid: {
        getResolutions: () => resolutions
      }
    })
  };
});

vi.mock('ol/format/WMTSCapabilities', () => ({
  default: class {
    read() {
      return {};
    }
  }
}));

vi.mock('ol/proj', () => ({
  get: () => ({}),
  transform: (coords) => coords
}));

vi.mock('ol/proj/proj4', () => ({
  register: () => {}
}));

vi.mock('proj4', () => ({
  default: {
    defs: vi.fn()
  }
}));

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      text: async () => '<Capabilities />'
    })));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the chat panel', () => {
    render(App);
    expect(screen.getByRole('heading', { name: /ask\.sogis/i })).toBeInTheDocument();
    expect(screen.getByText(/Chat will appear here/i)).toBeInTheDocument();
  });

  it('renders the map container', () => {
    render(App);
    const mapElement = document.getElementById('map');
    expect(mapElement).toBeInTheDocument();
  });
});
