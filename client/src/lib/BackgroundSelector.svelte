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

  let expanded = false;

  $: selectedOption =
    BACKGROUND_OPTIONS.find((option) => option.id === selectedId) ?? BACKGROUND_OPTIONS[1];

  function handleThumbnailClick(id) {
    dispatch('selectionChange', id);
    expanded = false;
  }

  function openSelector() {
    expanded = true;
  }
</script>

<div class="background-selector" data-expanded={expanded}>
  {#if expanded}
    <div class="background-selector-thumbnails" role="list">
      {#each BACKGROUND_OPTIONS as option}
        <button
          type="button"
          class="thumbnail-button {selectedId === option.id ? 'active' : ''}"
          on:click={() => handleThumbnailClick(option.id)}
          title={option.text}
          aria-label={option.text}
          role="listitem"
        >
          <img
            src={option.img}
            alt={option.text}
            class="thumbnail-img"
            loading="lazy"
          />
          <span class="thumbnail-label">{option.label}</span>
        </button>
      {/each}
    </div>
  {:else}
    <button
      type="button"
      class="thumbnail-button collapsed"
      on:click={openSelector}
      aria-label="Hintergrund auswählen"
    >
      <img
        src={selectedOption.img}
        alt={`Aktueller Hintergrund: ${selectedOption.text}`}
        class="thumbnail-img"
      />
      <span class="thumbnail-label">Hintergrund</span>
    </button>
  {/if}
</div>

<style>
  .background-selector {
    display: inline-flex;
    align-items: flex-end;
  }

  .background-selector-thumbnails {
    display: flex;
    gap: 10px;
    /*padding: 8px 10px;*/
    background: rgba(255, 255, 255, 0);
    border-radius: 10px;
    /*box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);*/
  }

  .thumbnail-button {
    position: relative;
    width: 124px;
    height: 80px;
    border: 2px solid #4a4a4a;
    /*border-radius: 8px;*/
    padding: 0;
    margin: 0;
    background: #e9ecef;
    cursor: pointer;
    overflow: hidden;
    transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.1s ease;
  }

  .thumbnail-button:hover {
    border-color: #0f62fe;
    /*box-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);*/
  }

  .thumbnail-button:focus-visible {
    /*outline: 2px solid #0f62fe;*/
    /*outline-offset: 3px;*/
  }

  .thumbnail-button:active {
    /*transform: translateY(1px);*/
  }

  .thumbnail-button.active {
    /*border-color: #d62d20;*/
    border-color: #0f62fe;
    /*box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.25);*/
  }

  .thumbnail-button.collapsed {
    width: 124px;
    height: 80px;
    background: #f4f4f4;
    border-color: #5a5a5a;
  }

  .thumbnail-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  .thumbnail-label {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    padding: 4px 8px;
    background: rgba(0, 0, 0, 0.65);
    color: #ffffff;
    font-size: 12px;
    font-weight: 600;
    text-align: left;
  }
</style>
