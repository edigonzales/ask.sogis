<script>
  import { Button, TextArea } from 'carbon-components-svelte';
  import ChatBot from 'carbon-icons-svelte/lib/ChatBot.svelte';
  import CloseOutline from 'carbon-icons-svelte/lib/CloseOutline.svelte';
  import Help from 'carbon-icons-svelte/lib/Help.svelte';
  import { onMount } from 'svelte';

  const overlayId = 'chat-overlay';
  let isOpen = true;
  let isTransitioning = false;

  function toggleOverlay() {
    isTransitioning = true;
    isOpen = !isOpen;
    // Reset transition flag after animation completes
    setTimeout(() => {
      isTransitioning = false;
    }, 300);
  }

  // Close the overlay by default on initial load if needed
  onMount(() => {
    // If you want to start with the overlay closed by default, uncomment the next line:
    // isOpen = false;
  });
</script>

{#if isOpen}
  <div class="chat-overlay" id={overlayId}>
    <div class="chat-header">
      <h3>Chat with LLM</h3>
      <button
        class="close-button"
        type="button"
        on:click={toggleOverlay}
        aria-label="Close chat"
        aria-controls={overlayId}
      >
        <CloseOutline size={24} aria-hidden="true" />
      </button>
    </div>
    <div class="chat-messages">
      <!-- Chat messages will appear here -->
      <div class="message bot-message">
        Hello! How can I help you with the map today?
      </div>
    </div>
    <div class="chat-input">
      <TextArea
        placeholder="Type your message here..."
        rows={3}
        class="input-textarea"
      />
      <Button kind="primary" class="send-button">Send</Button>
    </div>
  </div>
{/if}

<div class="sidebar">
  <div
    class="sidebar-icons"
    on:click={toggleOverlay}
    role="button"
    tabindex="0"
    aria-controls={overlayId}
    aria-expanded={isOpen}
    aria-label={isOpen ? 'Close chat overlay' : 'Open chat overlay'}
    aria-pressed={isOpen}
    on:keydown={(e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        toggleOverlay();
      }
    }}
  >
    <div class="icon chat-icon" title="Open Chat">
      <ChatBot size={24} aria-hidden="true" />
    </div>
    <div class="icon help-icon" title="Help">
      <Help size={24} aria-hidden="true" />
    </div>
  </div>
</div>

<style>
  .chat-overlay {
    position: absolute;
    top: 0;
    left: 64px; /* Offset by sidebar width */
    width: 450px;
    height: 100vh;
    background-color: rgba(255, 255, 255, 0.9);
    backdrop-filter: blur(10px);
    border-radius: 0;
    box-shadow: -4px 0 12px rgba(0, 0, 0, 0.15);
    display: flex;
    flex-direction: column;
    z-index: 1000;
    padding: 16px;
    box-sizing: border-box;
    border: 0px solid rgba(0, 0, 0, 0.1); /* Remove border */
  }

  .chat-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    padding-bottom: 8px;
    border-bottom: 1px solid #e0e0e0;
  }

  .chat-header h3 {
    margin: 0;
    font-size: 1.2rem;
    color: #161616;
  }

  .close-button {
    background: none;
    border: none;
    font-size: 1.5rem;
    cursor: pointer;
    color: rgb(22, 22, 22);
    padding: 4px;
    border-radius: 50%;
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background-color 0.2s;
  }

  .close-button:hover {
    background-color: #e0e0e0;
  }

  .chat-messages {
    flex: 1;
    overflow-y: auto;
    margin-bottom: 16px;
    padding: 8px;
    background-color: #f4f4f4;
    border-radius: 4px;
  }

  .message {
    padding: 8px;
    margin-bottom: 8px;
    border-radius: 4px;
    max-width: 90%;
  }

  .bot-message {
    background-color: #e0e0e0;
    align-self: flex-start;
    margin-right: auto;
  }

  .user-message {
    background-color: #0f62fe;
    color: white;
    align-self: flex-end;
    margin-left: auto;
  }

  .chat-input {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .input-textarea {
    margin-bottom: 8px;
  }

  .send-button {
    align-self: flex-end;
  }

  .sidebar {
    position: fixed;
    top: 0;
    left: 0;
    width: 64px;
    height: 100vh;
    background-color: rgba(255, 255, 255, 0.9);
    backdrop-filter: blur(10px);
    display: flex;
    flex-direction: column;
    justify-content: flex-start;
    align-items: center;
    z-index: 1000;
    padding: 16px 0;
    box-sizing: border-box;
    border-right: 1px solid rgba(0, 0, 0, 0.1);
  }

  .sidebar-icons {
    display: flex;
    flex-direction: column;
    gap: 4px;
    margin-top: 60px; /* Add some top margin for spacing */
    cursor: pointer;
  }

  .icon {
    width: 48px;
    height: 48px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
    color: rgb(22, 22, 22)
    transition: background-color 0.2s;
  }

  .icon:hover {
    background-color: #e0e0e0;
  }



  /* Carbon Components Svelte specific styles */
  .input-textarea textarea {
    width: 100%;
  }

  /* Ensure Carbon components display properly */
  :global(.bx--text-area) {
    width: 100%;
  }
</style>