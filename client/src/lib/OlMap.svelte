<script>
    import { onMount, onDestroy } from 'svelte';
    import 'ol/ol.css';
    import Map from 'ol/Map';
    import View from 'ol/View';
    import TileLayer from 'ol/layer/Tile';
    import OSM from 'ol/source/OSM';
    import { fromLonLat } from 'ol/proj';
  
    let el;     // div ref
    let map;    // OL map instance
  
    onMount(() => {
      map = new Map({
        target: el,
        layers: [ new TileLayer({ source: new OSM() }) ],
        view: new View({ center: fromLonLat([0, 0]), zoom: 2 })
      });
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
    }
</style>  