<script>
  import { onMount } from 'svelte';
  import 'ol/ol.css';
  import Map from 'ol/Map';
  import View from 'ol/View';
  import TileLayer from 'ol/layer/Tile';
  import WMTS, { optionsFromCapabilities } from 'ol/source/WMTS';
  import WMTSCapabilities from 'ol/format/WMTSCapabilities';
  import proj4 from 'proj4';
  import { register } from 'ol/proj/proj4';
  import { get as getProjection, transform } from 'ol/proj';

  const capabilitiesUrl = 'https://geo.so.ch/api/wmts/1.0.0/WMTSCapabilities.xml';
  const layerId = 'ch.so.agi.hintergrundkarte_sw';
  const matrixSet = 'EPSG:2056';

  proj4.defs(
    'EPSG:2056',
    '+proj=somerc +lat_0=46.95240555555556 +lon_0=7.439583333333333 +k_0=1 +x_0=2600000 +y_0=1200000 +ellps=bessel +towgs84=674.374,15.056,405.346,0,0,0,0 +units=m +no_defs'
  );
  register(proj4);

  let mapElement;
  let mapInstance;
  let loadingError = '';

  const centerWgs84 = [7.539, 47.208];

  onMount(() => {
    let cancelled = false;

    async function initMap() {
      try {
        const response = await fetch(capabilitiesUrl);
        if (!response.ok) {
          throw new Error(`WMTS capabilities request failed with status ${response.status}`);
        }

        const capabilitiesText = await response.text();
        const parser = new WMTSCapabilities();
        const capabilities = parser.read(capabilitiesText);
        const sourceOptions = optionsFromCapabilities(capabilities, {
          layer: layerId,
          matrixSet
        });

        const projection = getProjection(matrixSet);
        const view = new View({
          projection,
          center: transform(centerWgs84, 'EPSG:4326', matrixSet),
          resolutions: sourceOptions.tileGrid.getResolutions(),
          zoom: 0
        });

        if (!cancelled) {
          mapInstance = new Map({
            target: mapElement,
            layers: [
              new TileLayer({
                source: new WMTS({
                  ...sourceOptions,
                  wrapX: false
                })
              })
            ],
            view
          });
        }
      } catch (error) {
        console.error(error);
        if (!cancelled) {
          loadingError = error.message;
        }
      }
    }

    initMap();

    return () => {
      cancelled = true;
      if (mapInstance) {
        mapInstance.setTarget(null);
        mapInstance = undefined;
      }
    };
  });
</script>

<div class="app-shell">
  <aside class="chat-panel" aria-label="Chat overlay">
    <header>
      <h1>ask.sogis</h1>
      <p class="subtitle">Chat with the canton Solothurn geo services.</p>
    </header>
    <section class="chat-body" aria-live="polite">
      <p class="placeholder">Chat will appear here in a future iteration.</p>
    </section>
  </aside>

  <div class="map-wrapper">
    <div
      id="map"
      bind:this={mapElement}
      class="map"
      role="presentation"
      aria-hidden="true"
    ></div>
    {#if loadingError}
      <div class="map-status" role="alert">{loadingError}</div>
    {/if}
  </div>
</div>

<style>
  .app-shell {
    height: 100%;
    display: flex;
    gap: 1.5rem;
    padding: 1.5rem;
  }

  .chat-panel {
    width: min(28rem, 32vw);
    min-width: 18rem;
    height: 100%;
    background: rgba(255, 255, 255, 0.95);
    border-radius: 1.25rem;
    padding: 1.75rem 1.5rem;
    display: flex;
    flex-direction: column;
    box-shadow: 0 20px 45px rgba(22, 22, 22, 0.1);
  }

  .chat-panel header {
    margin-bottom: 1.5rem;
  }

  .chat-panel h1 {
    margin: 0;
    font-size: 1.75rem;
    font-weight: 600;
  }

  .subtitle {
    margin: 0.35rem 0 0;
    color: #525252;
    font-size: 0.95rem;
  }

  .chat-body {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    text-align: center;
    border: 1px dashed #d0d0d0;
    border-radius: 1rem;
    background: linear-gradient(145deg, rgba(244, 244, 244, 0.85), rgba(255, 255, 255, 0.7));
    padding: 1.5rem;
  }

  .placeholder {
    color: #8d8d8d;
    margin: 0;
    line-height: 1.4;
  }

  .map-wrapper {
    position: relative;
    flex: 1;
    border-radius: 1.25rem;
    overflow: hidden;
    background: #000;
    min-height: 0;
  }

  .map {
    width: 100%;
    height: 100%;
  }

  .map-status {
    position: absolute;
    inset: 1rem;
    background: rgba(255, 255, 255, 0.92);
    border-radius: 0.75rem;
    padding: 1rem 1.25rem;
    color: #da1e28;
    font-weight: 500;
    box-shadow: inset 0 0 0 1px rgba(218, 30, 40, 0.3);
    display: flex;
    align-items: center;
    justify-content: center;
    text-align: center;
  }

  @media (max-width: 960px) {
    .app-shell {
      flex-direction: column;
    }

    .chat-panel {
      width: 100%;
      height: auto;
      min-height: 16rem;
    }

    .map-wrapper {
      height: 50vh;
    }
  }
</style>
