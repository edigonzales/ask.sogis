import { render, screen } from '@testing-library/svelte';
import App from '../App.svelte';
import { describe, expect, it, vi } from 'vitest';

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

vi.mock('ol/source/WMTS', () => ({
  default: class {
    constructor(options) {
      this.options = options;
    }
  }
}));

vi.mock('ol/tilegrid/WMTS', () => ({
  default: class {
    constructor(options) {
      this.options = options;
    }
  }
}));

vi.mock('ol/proj', () => {
  const projection = {
    setExtent: vi.fn()
  };
  return {
    get: () => projection,
    transform: (coords) => coords
  };
});

vi.mock('ol/proj/proj4', () => ({
  register: () => {}
}));

vi.mock('proj4', () => ({
  default: {
    defs: vi.fn()
  }
}));

describe('App', () => {
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
