<script>
  import { createEventDispatcher } from 'svelte';
  export let selectedId = 'sw';
  
  const dispatch = createEventDispatcher();

  const BACKGROUND_OPTIONS = [
    { id: 'none', text: 'Kein Hintergrund', label: 'Kein Hintergrund', img: '/none.png' },
    { id: 'sw', text: 'Hintergrundkarte s/w', label: 'Karte SW', img: '/sw.png' },
    { id: 'color', text: 'Hintergrundkarte farbig', label: 'Karte farbig', img: '/farbig.png' },
    { id: 'ortho', text: 'Hintergrundkarte Luftbild', label: 'Luftbild', img: '/ortho.png' }
  ];

  function handleThumbnailClick(id) {
    console.log('BackgroundSelector dispatching selectionChange with:', id);
    dispatch('selectionChange', id);
  }
</script>

<div class="background-selector">
  <div class="background-selector-thumbnails">
    {#each BACKGROUND_OPTIONS as option}
      <div class="thumbnail-item">
        <span class="thumbnail-label">{option.label}</span>
        <button
          class="thumbnail-button {selectedId === option.id ? 'active' : ''}"
          on:click={() => handleThumbnailClick(option.id)}
          title={option.text}
          aria-label={option.text}
        >
          <img
            src={option.img}
            alt={option.text}
            class="thumbnail-img"
          />
        </button>
      </div>
    {/each}
  </div>
</div>

<style>
  .background-selector {
    display: flex;
    flex-direction: column;
    gap: 6px;
    background: rgba(255, 255, 255, 0.9);
    padding: 8px;
    border-radius: 4px;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
  }

  .selector-title {
    margin: 0;
    font-size: 12px;
    font-weight: 600;
  }

  .background-selector-thumbnails {
    display: flex;
    gap: 8px;
  }

  .thumbnail-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
    align-items: center;
  }

  .thumbnail-label {
    font-size: 12px;
    font-weight: 600;
  }

  .thumbnail-button {
    width: 72px;
    border: 2px solid transparent;
    border-radius: 4px;
    padding: 0;
    margin: 0;
    background: none;
    cursor: pointer;
    transition: border-color 0.2s ease, box-shadow 0.2s ease;
  }

  .thumbnail-button:hover {
    border-color: #0f62fe;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
  }

  .thumbnail-button.active {
    border-color: #0f62fe;
    background-color: #e5f3ff;
  }

  .thumbnail-img {
    width: 68px;
    height: 42.75px;
    max-height: 80px;
    object-fit: fill;
    border-radius: 2px;
    display: block;
  }
</style>
