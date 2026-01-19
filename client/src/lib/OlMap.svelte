<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import 'ol/ol.css';
  import OlMap from 'ol/Map';
  import View from 'ol/View';
  import type { AnimationOptions } from 'ol/View';
  import TileLayer from 'ol/layer/Tile';
  import ImageLayer from 'ol/layer/Image';
  import WMTSSource from 'ol/source/WMTS';
  import WMTSTileGrid from 'ol/tilegrid/WMTS';
  import VectorLayer from 'ol/layer/Vector';
  import VectorSource from 'ol/source/Vector';
  import GeoJSON from 'ol/format/GeoJSON';
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
  import { CHAT_OVERLAY_ID } from '$lib/constants';
  import { mapActionBus } from '$lib/stores/mapActions';
  import { layerStore, type TocLayer } from '$lib/stores/layers';
  import { MapActionType } from '$lib/api/chat-response';
  import type {
    MapAction,
    SetViewPayload,
    AddMarkerPayload,
    RemoveMarkerPayload,
    AddLayerPayload,
    SetLayerVisibilityPayload,
    RemoveLayerPayload,
    Coordinates,
    Extent
  } from '$lib/api/chat-response';
  import ImageWMS from 'ol/source/ImageWMS';

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
  const dynamicLayerMap = new Map<
    string,
    TileLayer<WMTSSource> | ImageLayer<ImageWMS> | VectorLayer<VectorSource>
  >();
  let selectedBackgroundId = 'sw';
  const backgroundLayerMap = new globalThis.Map<string, TileLayer<WMTSSource> | null>();
  let mapActionUnsubscribe: (() => void) | null = null;
  let layerStoreUnsubscribe: (() => void) | null = null;

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

  function extractExtent(extent?: Extent): Extent | null {
    if (!Array.isArray(extent) || extent.length < 4) {
      return null;
    }
    const [minX, minY, maxX, maxY] = extent;
    if (![minX, minY, maxX, maxY].every((value) => Number.isFinite(value))) {
      return null;
    }
    return [minX, minY, maxX, maxY];
  }

  function handleSetView(payload: SetViewPayload): Promise<void> {
    if (!map) {
      return Promise.resolve();
    }
    const view = map.getView();
    const animation: AnimationOptions = { duration: 350 };
    const coords = extractXY(payload.center);
    const extent = extractExtent(payload.extent);
    const targetZoom =
      typeof payload.zoom === 'number' && Number.isFinite(payload.zoom) ? payload.zoom : null;
    const targetResolution = targetZoom !== null
      ? view.getResolutionForZoom(targetZoom) ?? undefined
      : view.getResolution();
    if (extent) {
      const overlayEl = document.getElementById(CHAT_OVERLAY_ID);
      const padding: [number, number, number, number] = [24, 24, 24, 24];
      if (overlayEl) {
        const mapRect = el?.getBoundingClientRect();
        const overlayRect = overlayEl.getBoundingClientRect();
        const blockedWidth = Math.max(
          0,
          Math.min(mapRect?.right ?? 0, overlayRect.right) - (mapRect?.left ?? 0)
        );
        if (blockedWidth > 0) {
          padding[3] = Math.max(padding[3], blockedWidth + 24);
        }
      }
      return new Promise((resolve) => {
        view.fit(extent, {
          duration: 350,
          padding,
          maxZoom: targetZoom ?? undefined,
          callback: () => resolve()
        });
      });
    }
    if (coords) {
      const overlayEl = document.getElementById(CHAT_OVERLAY_ID);
      if (overlayEl) {
        const mapRect = el?.getBoundingClientRect();
        const overlayRect = overlayEl.getBoundingClientRect();
        const blockedWidth = Math.max(
          0,
          Math.min(mapRect?.right ?? 0, overlayRect.right) - (mapRect?.left ?? 0)
        );
        if (blockedWidth > 0 && targetResolution) {
          const offsetX = (blockedWidth / 2) * targetResolution;
          animation.center = [coords[0] - offsetX, coords[1]];
        } else {
          animation.center = coords;
        }
      } else {
        animation.center = coords;
      }
    }
    if (targetZoom !== null) {
      animation.zoom = targetZoom;
    }

    return new Promise((resolve) => {
      if (!animation.center && typeof animation.zoom !== 'number') {
        resolve();
        return;
      }

      view.animate(animation, () => resolve());
    });
  }

  function createMarkerStyle(label: string) {
    return new Style({
      image: new Icon({
        src: MARKER_ICON_URL,
        anchor: [0.5, 1],
        scale: 1.0
      }),
      text: new Text({
        text: label,
        offsetY: -50,
        font: 'bold 16px "Helvetica Neue", Arial, sans-serif',
        fill: new Fill({ color: '#111' }),
        stroke: new Stroke({ color: 'rgba(255,255,255,0.85)', width: 3 })
      })
    });
  }

  function handleAddMarker(payload: AddMarkerPayload): Promise<void> {
    if (!markerSource) {
      return Promise.resolve();
    }

    const coords = extractXY(payload.coord);
    if (!coords) {
      console.warn('Invalid marker coordinates', payload.coord);
      return Promise.resolve();
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
    return Promise.resolve();
  }

  function handleRemoveMarker(payload: RemoveMarkerPayload): Promise<void> {
    if (!markerSource || !payload?.id) {
      return Promise.resolve();
    }
    const feature = markerSource.getFeatureById(payload.id);
    if (feature) {
      markerSource.removeFeature(feature as Feature<Point>);
    }
    return Promise.resolve();
  }

  function handleAddLayer(payload: AddLayerPayload): Promise<void> {
    if (!map || !payload?.id) {
      return Promise.resolve();
    }

    const existing = dynamicLayerMap.get(payload.id);
    if (existing) {
      map.removeLayer(existing);
      dynamicLayerMap.delete(payload.id);
    }

    const tocExcludedKeywords = ['redline', 'redlining', 'highlight', 'rubberband', 'marker'];
    const shouldExcludeFromToc = () => {
      const label = typeof payload.label === 'string' ? payload.label : '';
      const style = typeof payload.source?.style === 'string' ? payload.source.style : '';
      const haystack = `${payload.id} ${payload.type} ${label} ${style}`.toLowerCase();
      return tocExcludedKeywords.some((keyword) => haystack.includes(keyword));
    };
    const shouldIncludeInToc = !shouldExcludeFromToc();

    if (payload.type === 'geojson') {
      const sourceData = payload.source?.data ?? payload.source;
      if (!sourceData) {
        return Promise.resolve();
      }
      const geojsonFormat = new GeoJSON({ dataProjection: 'EPSG:2056', featureProjection: 'EPSG:2056' });
      const features = geojsonFormat.readFeatures(sourceData);
      const vectorSource = new VectorSource({ features });

      const styleName = (payload.source?.style as string) ?? '';
      const style =
        styleName === 'highlight'
          ? new Style({
              stroke: new Stroke({ color: 'rgba(255,0,0,0.9)', width: 3 }),
              fill: new Fill({ color: 'rgba(255,0,0,0.1)' })
            })
          : undefined;

      const vectorLayer = new VectorLayer({
        source: vectorSource,
        style
      });
      vectorLayer.setZIndex(900);
      map.addLayer(vectorLayer);
      dynamicLayerMap.set(payload.id, vectorLayer);
      if (shouldIncludeInToc) {
        layerStore.upsertLayer({
          id: payload.id,
          label: (payload.label as string) ?? payload.id,
          visible: payload.visible ?? true,
          type: payload.type
        });
      }
      return Promise.resolve();
    }

    if (payload.type === 'wmts') {
      const url = (payload.source?.url ?? '') as string;
      const layerName = (payload.source?.layer ?? '') as string;
      if (!url || !layerName) {
        return Promise.resolve();
      }
      const wmtsLayer = new TileLayer({
        source: new WMTSSource({
          url,
          layer: layerName,
          matrixSet: (payload.source?.matrixSet as string) ?? '2056',
          format: (payload.source?.format as string) ?? 'image/png',
          style: (payload.source?.style as string) ?? 'default',
          wrapX: false
        }),
        visible: payload.visible ?? true
      });
      map.addLayer(wmtsLayer);
      dynamicLayerMap.set(payload.id, wmtsLayer);
      if (shouldIncludeInToc) {
        layerStore.upsertLayer({
          id: payload.id,
          label: (payload.label as string) ?? payload.id,
          visible: payload.visible ?? true,
          type: payload.type
        });
      }
      return Promise.resolve();
    }

    if (payload.type === 'wms') {
      const url = (payload.source?.url ?? '') as string;
      if (!url) {
        return Promise.resolve();
      }
      const params = { ...(payload.source ?? {}) } as Record<string, unknown>;
      delete params.url;
      const wmsLayer = new ImageLayer({
        source: new ImageWMS({
          url,
          params,
          serverType: 'qgis',
          ratio: 1
        }),
        visible: payload.visible ?? true
      });
      map.addLayer(wmsLayer);
      dynamicLayerMap.set(payload.id, wmsLayer);
      if (shouldIncludeInToc) {
        layerStore.upsertLayer({
          id: payload.id,
          label: (payload.label as string) ?? payload.id,
          visible: payload.visible ?? true,
          type: payload.type
        });
      }
      return Promise.resolve();
    }

    return Promise.resolve();
  }

  function syncLayerZIndices(layers: TocLayer[]) {
    const baseZIndex = 100;
    const lastIndex = layers.length - 1;
    layers.forEach((layer, index) => {
      const mapLayer = dynamicLayerMap.get(layer.id);
      if (!mapLayer) {
        return;
      }
      mapLayer.setZIndex(baseZIndex + (lastIndex - index));
    });
  }

  function handleRemoveLayer(payload: RemoveLayerPayload): Promise<void> {
    if (!map || !payload?.id) {
      return Promise.resolve();
    }
    const layer = dynamicLayerMap.get(payload.id);
    if (layer) {
      map.removeLayer(layer);
      dynamicLayerMap.delete(payload.id);
    }
    layerStore.removeLayer(payload.id);
    return Promise.resolve();
  }

  function handleSetLayerVisibility(payload: SetLayerVisibilityPayload): Promise<void> {
    if (!map || !payload?.id) {
      return Promise.resolve();
    }
    const layer = dynamicLayerMap.get(payload.id);
    if (layer) {
      layer.setVisible(Boolean(payload.visible));
    }
    layerStore.setVisibility(payload.id, Boolean(payload.visible));
    return Promise.resolve();
  }

  function clearVectorLayers() {
    if (!map) {
      return;
    }

    map.getLayers().getArray().forEach((layer) => {
      if (layer instanceof VectorLayer) {
        const source = layer.getSource();
        if (source instanceof VectorSource) {
          source.clear();
        }
      }
    });
  }

  function removeNonBackgroundTileLayers() {
    if (!map) {
      return;
    }

    const backgroundLayers = new Set(
      Array.from(backgroundLayerMap.values()).filter(
        (layer): layer is TileLayer<WMTSSource> => layer instanceof TileLayer
      )
    );

    map
      .getLayers()
      .getArray()
      .slice()
      .forEach((layer) => {
        if (layer instanceof TileLayer) {
          if (backgroundLayers.has(layer)) {
            return;
          }
          const source = layer.getSource();
          if (source instanceof WMTSSource) {
            map?.removeLayer(layer);
          }
          return;
        }
        if (layer instanceof ImageLayer) {
          const source = layer.getSource();
          if (source instanceof ImageWMS) {
            map?.removeLayer(layer);
          }
        }
      });
  }

  function handleClearMap(): Promise<void> {
    clearVectorLayers();
    removeNonBackgroundTileLayers();
    layerStore.clear();
    return Promise.resolve();
  }

  function handleMapAction(action: MapAction): Promise<void> {
    const type = action.type as MapActionType | string;
    switch (type) {
      case MapActionType.SetView:
        return handleSetView(action.payload as SetViewPayload);
      case MapActionType.AddMarker:
        return handleAddMarker(action.payload as AddMarkerPayload);
      case MapActionType.RemoveMarker:
        return handleRemoveMarker(action.payload as RemoveMarkerPayload);
      case MapActionType.AddLayer:
        return handleAddLayer(action.payload as AddLayerPayload);
      case MapActionType.RemoveLayer:
        return handleRemoveLayer(action.payload as RemoveLayerPayload);
      case MapActionType.SetLayerVisibility:
        return handleSetLayerVisibility(action.payload as SetLayerVisibilityPayload);
      case MapActionType.ClearMap:
        return handleClearMap();
      default:
        console.warn('Unknown map action type', action);
        return Promise.resolve();
    }
  }

  async function processMapActionsSequentially(actions: MapAction[]) {
    for (const action of actions) {
      await handleMapAction(action);
    }
    mapActionBus.clear();
  }

  let actionProcessingQueue: Promise<void> = Promise.resolve();

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
        center: [2606500, 1237000], // Center coordinate from Java code
        zoom: 6, // Initial zoom level from Java code
        resolutions: resolutions
      })
    });

    markerSource = new VectorSource();
    markerLayer = new VectorLayer({
      source: markerSource
    });
    markerLayer.setZIndex(1000);
    map.addLayer(markerLayer);

    layerStoreUnsubscribe = layerStore.subscribe((layers) => {
      syncLayerZIndices(layers);
    });

    mapActionUnsubscribe = mapActionBus.subscribe((actions) => {
      if (!actions.length) {
        return;
      }

      actionProcessingQueue = actionProcessingQueue
        .then(() => processMapActionsSequentially(actions))
        .catch((error) => {
          console.error('Error while processing map actions', error);
        });
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
    layerStoreUnsubscribe?.();
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
