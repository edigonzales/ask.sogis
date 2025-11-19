<script lang="ts">
  import { Button, TextArea } from 'carbon-components-svelte';
  import ChatBot from 'carbon-icons-svelte/lib/ChatBot.svelte';
  import CloseOutline from 'carbon-icons-svelte/lib/CloseOutline.svelte';
  import Help from 'carbon-icons-svelte/lib/Help.svelte';
  import { onMount } from 'svelte';
  import type { ChatResponse } from '$lib/api/chat-response';
  import { mapActionBus } from '$lib/stores/mapActions';

  type Role = 'bot' | 'user';
  type ChatMessage = { id: string; role: Role; text: string };

  const overlayId = 'chat-overlay';
  let isOpen = true;
  let isTransitioning = false;
  let prompt = '';
  let isSending = false;
  let messages: ChatMessage[] = [
    { id: crypto.randomUUID?.() ?? 'welcome', role: 'bot', text: 'Hello! How can I help you with the map today?' }
  ];

  function toggleOverlay() {
    isTransitioning = true;
    isOpen = !isOpen;
    setTimeout(() => {
      isTransitioning = false;
    }, 300);
  }

  const createMessageId = () => crypto.randomUUID?.() ?? Math.random().toString(36).slice(2);

  function appendMessage(role: Role, text: string) {
    messages = [...messages, { id: createMessageId(), role, text }];
  }

  async function sendMessage() {
    const trimmed = prompt.trim();
    if (!trimmed || isSending) {
      return;
    }

    appendMessage('user', trimmed);
    prompt = '';
    isSending = true;

    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ prompt: trimmed })
      });

      if (!response.ok) {
        throw new Error(`Server responded with ${response.status}`);
      }

      const chatResponse = (await response.json()) as ChatResponse;
      handleChatResponse(chatResponse);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unexpected error while contacting the chat service.';
      appendMessage('bot', `⚠️ ${message}`);
    } finally {
      isSending = false;
    }
  }

  function handleChatResponse(response: ChatResponse) {
    response.steps?.forEach((step) => {
      if (step.message) {
        appendMessage('bot', step.message);
      }

      if (step.mapActions?.length) {
        mapActionBus.dispatch(step.mapActions);
      }
    });
  }

  function handleInputKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      sendMessage();
    }
  }

  onMount(() => {
    // Placeholder for potential future initialization logic
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
    <div class="chat-messages" aria-live="polite">
      {#each messages as message (message.id)}
        <div class={`message ${message.role === 'bot' ? 'bot-message' : 'user-message'}`}>
          {message.text}
        </div>
      {/each}
    </div>
    <div class="chat-input">
      <TextArea
        labelText="&nbsp;"
        placeholder="Type your message here..."
        rows={3}
        cols={10}
        maxCount={200}
        class="input-textarea"
        bind:value={prompt}
        on:keydown={handleInputKeydown}
      />
      <Button
        kind="primary"
        class="send-button"
        type="button"
        disabled={!prompt.trim() || isSending}
        on:click={sendMessage}
      >
        {isSending ? 'Sending…' : 'Send'}
      </Button>
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
    width: 550px;
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
    background-color: rgba(255, 255, 255, 0.0);
    border-radius: 4px;
  }

  .message {
    padding: 8px;
    margin-bottom: 16px;
    border-radius: 4px;
    max-width: 90%;
    line-height: 1.5;
  }

  .bot-message {
    background-color: rgb(244, 244, 244);
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

  :global(.send-button) {
    align-self: flex-start;
    width: auto;
    max-width: none;
    min-width: 0;
    inline-size: auto;
    max-inline-size: none;
    min-inline-size: 0;
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
    color: rgb(22, 22, 22);
    transition: background-color 0.2s;
  }

  .icon:hover {
    background-color: #e0e0e0;
  }



  /* Carbon Components Svelte specific styles */
  :global(.input-textarea textarea) {
    width: 100%;
  }

  /* Ensure Carbon components display properly */
  :global(.bx--text-area) {
    width: 100%;
  }
</style>