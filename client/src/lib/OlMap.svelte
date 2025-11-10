<script>
    import { onMount, onDestroy } from 'svelte';
    import 'ol/ol.css';
    import Map from 'ol/Map';
    import View from 'ol/View';
    import TileLayer from 'ol/layer/Tile';
    import WMTSSource from 'ol/source/WMTS';
    import WMTSTileGrid from 'ol/tilegrid/WMTS';
    import { get as getProjection, fromLonLat, toLonLat } from 'ol/proj';
    import { register } from 'ol/proj/proj4';
    import proj4 from 'proj4';
    import Zoom from 'ol/control/Zoom';

    let el;     // div ref
    let map;    // OL map instance

    onMount(() => {
      // Define the custom projection EPSG:2056 (Swiss CH1903+ / LV95)
      proj4.defs("EPSG:2056", "+proj=somerc +lat_0=46.95240555555556 +lon_0=7.439583333333333 +k_0=1 +x_0=2600000 +y_0=1200000 +ellps=bessel +towgs84=674.374,15.056,405.346,0,0,0,0 +units=m +no_defs");
      register(proj4);

      const projection = getProjection('EPSG:2056');
      const projectionExtent = [2420000, 1030000, 2900000, 1350000];
      projection.setExtent(projectionExtent);

      // Define the resolutions array from the Java code
      const resolutions = [4000.0, 2000.0, 1000.0, 500.0, 250.0, 100.0, 50.0, 20.0, 10.0, 5.0, 2.5, 1.0, 0.5, 0.25, 0.1];

      // Create WMTS tile grid with proper matrix IDs that match the resolutions
      const matrixIds = resolutions.map((_, index) => index.toString());
      const tileGrid = new WMTSTileGrid({
        extent: projectionExtent,
        resolutions: resolutions,
        matrixIds: matrixIds, // Using string representations of index as matrix IDs
        tileSize: [256, 256]
      });

      // Create WMTS source with the Swiss WMTS service
      const wmtsSource = new WMTSSource({
        url: 'https://geo.so.ch/api/wmts/1.0.0/ch.so.agi.hintergrundkarte_sw/default/2056/{TileMatrix}/{TileRow}/{TileCol}',
        layer: 'hintergrundkarte_sw',
        matrixSet: '2056',
        format: 'image/jpeg',
        projection: projection,
        tileGrid: tileGrid,
        style: 'default',
        requestEncoding: 'REST',
        wrapX: false
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
        delta: 1,
      });

      // Create the map
      map = new Map({
        target: el,
        controls: [zoomControl],
        layers: [
          new TileLayer({
            source: wmtsSource
          })
        ],
        view: new View({
          projection: projection,
          center: [2616500, 1237000], // Center coordinate from Java code
          zoom: 5, // Initial zoom level from Java code
          resolutions: resolutions
        })
      });

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
      map && map.setTarget(null);
    });
  </script>

  <div bind:this={el} class="map"></div>

  <style>
    .map {
        width: 100%;
        height: 100%;
        position: relative;
    }
  </style>