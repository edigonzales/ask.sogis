<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import 'ol/ol.css';
  import OlMap from 'ol/Map';
  import View from 'ol/View';
  import type { AnimationOptions } from 'ol/View';
  import TileLayer from 'ol/layer/Tile';
  import WMTSSource from 'ol/source/WMTS';
  import WMTSTileGrid from 'ol/tilegrid/WMTS';
  import VectorLayer from 'ol/layer/Vector';
  import VectorSource from 'ol/source/Vector';
  import Feature from 'ol/Feature';
  import Point from 'ol/geom/Point';
  import Style from 'ol/style/Style';
  import Icon from 'ol/style/Icon';
  import Text from 'ol/style/Text';
  import Fill from 'ol/style/Fill';
  import Stroke from 'ol/style/Stroke';
  import { get as getProjection } from 'ol/proj';
  import { register } from 'ol/proj/proj4';
  import proj4 from 'proj4';
  import Zoom from 'ol/control/Zoom';
  import BackgroundSelector from './BackgroundSelector.svelte';
  import { mapActionBus } from '$lib/stores/mapActions';
  import { MapActionType } from '$lib/api/chat-response';
  import type {
    MapAction,
    SetViewPayload,
    AddMarkerPayload,
    AddLayerPayload,
    Coordinates
  } from '$lib/api/chat-response';

  const BACKGROUND_OPTIONS = [
    { id: 'none', text: 'Kein Hintergrund', layerName: null },
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

  const MARKER_ICON_URL = '/marker2.png';

  let el: HTMLDivElement;
  let map: OlMap | null = null;
  let markerSource: VectorSource | null = null;
  let markerLayer: VectorLayer<VectorSource> | null = null;
  let selectedBackgroundId = 'sw';
  const backgroundLayerMap = new globalThis.Map<string, TileLayer<WMTSSource> | null>();
  let mapActionUnsubscribe: (() => void) | null = null;

  function setBackground(selectedId: string) {
    selectedBackgroundId = selectedId;

    BACKGROUND_OPTIONS.forEach((option) => {
      const layer = backgroundLayerMap.get(option.id);
      if (layer) {
        layer.setVisible(false);
        layer.setZIndex(-1000);
      }
    });

    if (selectedId !== 'none') {
      const selectedLayer = backgroundLayerMap.get(selectedId);
      if (selectedLayer) {
        selectedLayer.setVisible(true);
        selectedLayer.setZIndex(-1000);
      }
    }

    if (!map) {
      return;
    }

    const viewport = map.getViewport?.();
    if (viewport) {
      viewport.style.backgroundColor = selectedBackgroundId === 'none' ? '#ffffff' : 'transparent';
    }

    map.render();
  }

  function handleBackgroundSelect(selectedId: string) {
    if (!selectedId || selectedId === selectedBackgroundId) {
      return;
    }

    setBackground(selectedId);
  }

  function extractXY(coord?: Coordinates): [number, number] | null {
    if (!Array.isArray(coord) || coord.length < 2) {
      return null;
    }
    const [x, y] = coord;
    if (!Number.isFinite(x) || !Number.isFinite(y)) {
      return null;
    }
    return [x, y];
  }

  function handleSetView(payload: SetViewPayload) {
    if (!map) {
      return;
    }
    const view = map.getView();
    const animation: AnimationOptions = { duration: 350 };
    const coords = extractXY(payload.center);
    if (coords) {
      animation.center = coords;
    }
    if (typeof payload.zoom === 'number' && Number.isFinite(payload.zoom)) {
      animation.zoom = payload.zoom;
    }
    view.animate(animation);
  }

  function createMarkerStyle(label: string) {
    return new Style({
      image: new Icon({
        src: MARKER_ICON_URL,
        anchor: [0.5, 1],
        scale: 0.6
      }),
      text: new Text({
        text: label,
        offsetY: -35,
        font: 'bold 14px "Helvetica Neue", Arial, sans-serif',
        fill: new Fill({ color: '#111' }),
        stroke: new Stroke({ color: 'rgba(255,255,255,0.85)', width: 3 })
      })
    });
  }

  function handleAddMarker(payload: AddMarkerPayload) {
    if (!markerSource) {
      return;
    }

    const coords = extractXY(payload.coord);
    if (!coords) {
      console.warn('Invalid marker coordinates', payload.coord);
      return;
    }

    const id = payload.id ?? crypto.randomUUID?.() ?? `marker-${Date.now()}`;
    const label = payload.label ?? id;
    const existing = markerSource.getFeatureById(id);
    if (existing) {
      markerSource.removeFeature(existing as Feature<Point>);
    }

    const feature = new Feature({
      geometry: new Point(coords)
    });
    feature.setId(id);
    feature.setStyle(createMarkerStyle(label));
    markerSource.addFeature(feature);
  }

  function handleAddLayer(payload: AddLayerPayload) {
    console.info('addLayer action received but not implemented yet', payload);
  }

  function handleMapAction(action: MapAction) {
    const type = action.type as MapActionType | string;
    switch (type) {
      case MapActionType.SetView:
        handleSetView(action.payload as SetViewPayload);
        break;
      case MapActionType.AddMarker:
        handleAddMarker(action.payload as AddMarkerPayload);
        break;
      case MapActionType.AddLayer:
        handleAddLayer(action.payload as AddLayerPayload);
        break;
      default:
        console.warn('Unknown map action type', action);
    }
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

    const createBackgroundLayer = (layerName: string) => {
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

    const backgroundLayers: TileLayer<WMTSSource>[] = [];
    BACKGROUND_OPTIONS.forEach((option) => {
      if (option.layerName) {
        const layer = createBackgroundLayer(option.layerName);
        backgroundLayerMap.set(option.id, layer);
        backgroundLayers.push(layer);
      } else {
        // For 'none' option, we don't create a layer, but we need to handle it in setBackground
        // So we add a null entry to the map for consistency
        backgroundLayerMap.set(option.id, null);
      }
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

    markerSource = new VectorSource();
    markerLayer = new VectorLayer({
      source: markerSource
    });
    markerLayer.setZIndex(1000);
    map.addLayer(markerLayer);

    mapActionUnsubscribe = mapActionBus.subscribe((actions) => {
      if (!actions.length) {
        return;
      }
      actions.forEach((action) => handleMapAction(action));
      mapActionBus.clear();
    });

    // Set the initial background after map is created
    setTimeout(() => {
      console.log('Background layers map contents:', Object.fromEntries(backgroundLayerMap));
      console.log('About to call setBackground with:', selectedBackgroundId);
      setBackground(selectedBackgroundId);
    }, 0);

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
    mapActionUnsubscribe?.();
    markerLayer = null;
    markerSource = null;
    backgroundLayerMap.clear();
  });
</script>

<div class="map-wrapper">
  <div bind:this={el} class="map"></div>
  <div class="background-selector">
    <BackgroundSelector 
      selectedId={selectedBackgroundId}
      on:selectionChange={(e) => handleBackgroundSelect(e.detail)}
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
    z-index: 1000;
  }
</style>
