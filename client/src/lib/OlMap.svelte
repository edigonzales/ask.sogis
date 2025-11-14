<script>
  import { onMount, onDestroy } from 'svelte';
  import 'ol/ol.css';
  import OlMap from 'ol/Map';
  import View from 'ol/View';
  import TileLayer from 'ol/layer/Tile';
  import WMTSSource from 'ol/source/WMTS';
  import WMTSTileGrid from 'ol/tilegrid/WMTS';
  import { get as getProjection } from 'ol/proj';
  import { register } from 'ol/proj/proj4';
  import proj4 from 'proj4';
  import Zoom from 'ol/control/Zoom';
  import { ComboBox } from 'carbon-components-svelte';

  const BACKGROUND_OPTIONS = [
    { id: 'none', text: 'Kein Hintergrund' },
    {
      id: 'sw',
      text: 'Hintergrundkarte s/w',
      layerName: 'ch.so.agi.hintergrundkarte_sw'
    },
    {
      id: 'color',
      text: 'Hintergrundkarte farbig',
      layerName: 'ch.so.agi.hintergrundkarte_farbig'
    },
    {
      id: 'ortho',
      text: 'Hintergrundkarte Luftbild',
      layerName: 'ch.so.agi.hintergrundkarte_ortho'
    }
  ];

  let el;
  let map;
  let selectedBackgroundId = 'sw';
  const backgroundLayerMap = new globalThis.Map();

  function setBackground(selectedId) {
    selectedBackgroundId = selectedId;

    BACKGROUND_OPTIONS.forEach((option) => {
      const layer = backgroundLayerMap.get(option.id);
      if (layer) {
        layer.setVisible(option.id === selectedBackgroundId);
        layer.setZIndex(-1000);
      }
    });

    if (!map) {
      return;
    }

    const viewport = map.getViewport?.();
    if (viewport) {
      viewport.style.backgroundColor =
        selectedBackgroundId === 'none' ? '#ffffff' : 'transparent';
    }

    map.render();
  }

  function handleBackgroundSelect(event) {
    const { selectedId } = event.detail;
    if (!selectedId || selectedId === selectedBackgroundId) {
      return;
    }

    setBackground(selectedId);
  }

  onMount(() => {
    // Define the custom projection EPSG:2056 (Swiss CH1903+ / LV95)
    proj4.defs(
      'EPSG:2056',
      '+proj=somerc +lat_0=46.95240555555556 +lon_0=7.439583333333333 +k_0=1 +x_0=2600000 +y_0=1200000 +ellps=bessel +towgs84=674.374,15.056,405.346,0,0,0,0 +units=m +no_defs'
    );
    register(proj4);

    const projection = getProjection('EPSG:2056');
    const projectionExtent = [2420000, 1030000, 2900000, 1350000];
    projection.setExtent(projectionExtent);

    // Define the resolutions array from the Java code
    const resolutions = [
      4000.0,
      2000.0,
      1000.0,
      500.0,
      250.0,
      100.0,
      50.0,
      20.0,
      10.0,
      5.0,
      2.5,
      1.0,
      0.5,
      0.25,
      0.1
    ];

    // Create WMTS tile grid with proper matrix IDs that match the resolutions
    const matrixIds = resolutions.map((_, index) => index.toString());
    const tileGrid = new WMTSTileGrid({
      extent: projectionExtent,
      resolutions: resolutions,
      matrixIds: matrixIds, // Using string representations of index as matrix IDs
      tileSize: [256, 256]
    });

    const createBackgroundLayer = (layerName) => {
      return new TileLayer({
        visible: false,
        source: new WMTSSource({
          url: `https://geo.so.ch/api/wmts/1.0.0/${layerName}/default/2056/{TileMatrix}/{TileRow}/{TileCol}`,
          layer: layerName.split('.').pop(),
          matrixSet: '2056',
          format: 'image/jpeg',
          projection: projection,
          tileGrid: tileGrid,
          style: 'default',
          requestEncoding: 'REST',
          wrapX: false
        })
      });
    };

    const backgroundLayers = [];
    BACKGROUND_OPTIONS.forEach((option) => {
      if (!option.layerName) {
        return;
      }

      const layer = createBackgroundLayer(option.layerName);
      backgroundLayerMap.set(option.id, layer);
      backgroundLayers.push(layer);
    });

    // Create zoom controls
    const zoomControl = new Zoom({
      className: 'ol-zoom',
      zoomInClassName: 'ol-zoom-in',
      zoomOutClassName: 'ol-zoom-out',
      zoomInLabel: '+',
      zoomOutLabel: '-',
      zoomInTipLabel: 'Zoom in',
      zoomOutTipLabel: 'Zoom out',
      delta: 1
    });

    // Create the map
    map = new OlMap({
      target: el,
      controls: [zoomControl],
      layers: backgroundLayers,
      view: new View({
        projection: projection,
        center: [2616500, 1237000], // Center coordinate from Java code
        zoom: 5, // Initial zoom level from Java code
        resolutions: resolutions
      })
    });

    setBackground(selectedBackgroundId);

    // After the map is rendered, reposition the zoom control using JavaScript
    setTimeout(() => {
      const zoomElement = document.querySelector('.ol-zoom');
      if (zoomElement && zoomElement instanceof HTMLElement) {
        zoomElement.style.left = 'auto';
        zoomElement.style.right = '10px';
        zoomElement.style.top = '10px';
      }
    }, 100);
  });

  onDestroy(() => {
    // Cleanly detach to avoid lingering observers
    if (map) {
      map.setTarget(null);
    }
    backgroundLayerMap.clear();
  });
</script>

<div class="map-wrapper">
  <div bind:this={el} class="map"></div>
  <div class="background-selector">
    <ComboBox
      items={BACKGROUND_OPTIONS}
      selectedId={selectedBackgroundId}
      placeholder="Hintergrund auswählen"
      titleText="Hintergrund"
      hideLabel={true}
      direction="top"
      on:select={handleBackgroundSelect}
    />
  </div>
</div>

<style>
  .map-wrapper {
    position: relative;
    width: 100%;
    height: 100%;
  }

  .map {
    width: 100%;
    height: 100%;
  }

  .background-selector {
    position: absolute;
    right: 1rem;
    bottom: 1rem;
    min-width: 240px;
    z-index: 1000;
  }
</style>
