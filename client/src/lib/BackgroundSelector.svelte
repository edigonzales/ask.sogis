<script>
  import { createEventDispatcher } from 'svelte';
  export let selectedId = 'sw';
  
  const dispatch = createEventDispatcher();
  
  const BACKGROUND_OPTIONS = [
    { id: 'none', text: 'Kein Hintergrund', img: '/none.png' },
    { id: 'sw', text: 'Hintergrundkarte s/w', img: '/sw.png' },
    { id: 'color', text: 'Hintergrundkarte farbig', img: '/farbig.png' },
    { id: 'ortho', text: 'Hintergrundkarte Luftbild', img: '/ortho.png' }
  ];

  function handleThumbnailClick(id) {
    console.log('BackgroundSelector dispatching selectionChange with:', id);
    dispatch('selectionChange', { detail: id });
  }
</script>

<div class="background-selector-thumbnails">
  {#each BACKGROUND_OPTIONS as option}
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
  {/each}
</div>

<style>
  .background-selector-thumbnails {
    display: flex;
    gap: 4px;
    background: rgba(255, 255, 255, 0.9);
    padding: 8px;
    border-radius: 4px;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
  }

  .thumbnail-button {
    width: 40px;
    height: 40px;
    border: 2px solid transparent;
    border-radius: 4px;
    padding: 0;
    margin: 0;
    background: none;
    cursor: pointer;
    transition: border-color 0.2s ease;
  }

  .thumbnail-button:hover {
    border-color: #0f62fe;
  }

  .thumbnail-button.active {
    border-color: #0f62fe;
    background-color: #e5f3ff;
  }

  .thumbnail-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 2px;
  }
</style>