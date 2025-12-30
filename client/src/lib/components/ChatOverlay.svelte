<script lang="ts">
  import { Button, TextArea } from 'carbon-components-svelte';
  import ChatBot from 'carbon-icons-svelte/lib/ChatBot.svelte';
  import CloseOutline from 'carbon-icons-svelte/lib/CloseOutline.svelte';
  import Help from 'carbon-icons-svelte/lib/Help.svelte';
  import TrashCan from 'carbon-icons-svelte/lib/TrashCan.svelte';
  import { afterUpdate, onMount } from 'svelte';
  import { CHAT_OVERLAY_ID } from '$lib/constants';
  import type { AddLayerPayload, ChatResponse, Choice, MapAction } from '$lib/api/chat-response';
  import { MapActionType } from '$lib/api/chat-response';
  import { mapActionBus } from '$lib/stores/mapActions';

  type Role = 'bot' | 'user';
  type ChatMessage = { id: string; role: Role; text: string };

  let chatMessagesContainer: HTMLDivElement;
  const createSessionId = () => crypto.randomUUID?.() ?? Math.random().toString(36).slice(2);
  let sessionId = createSessionId();
  let isOpen = true;
  let isTransitioning = false;
  let prompt = '';
  let isSending = false;
  const createMessageId = () => crypto.randomUUID?.() ?? Math.random().toString(36).slice(2);
  const createWelcomeMessage = () => ({
    id: createMessageId(),
    role: 'bot' as const,
    text: 'Hello! How can I help you with the map today?'
  });

  let messages: ChatMessage[] = [createWelcomeMessage()];
  let pendingChoices: Choice[] = [];
  let pendingChoiceMessage = '';

  function toggleOverlay() {
    isTransitioning = true;
    isOpen = !isOpen;
    setTimeout(() => {
      isTransitioning = false;
    }, 300);
  }

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
    pendingChoices = [];
    pendingChoiceMessage = '';
    isSending = true;

    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ sessionId, userMessage: trimmed })
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

  async function sendChoice(choice: Choice) {
    if (!choice || isSending) {
      return;
    }

    appendMessage('user', `Ich wähle: ${choice.label}`);
    pendingChoices = [];
    pendingChoiceMessage = '';
    isSending = true;

    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ sessionId, choiceId: choice.id })
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

  function buildHighlightRemovalActions(choicesToClear: Choice[]): MapAction[] {
    return choicesToClear
      .flatMap((choice) => choice.mapActions ?? [])
      .map((action) => {
        const type = action.type as MapActionType | string;
        if (type === MapActionType.AddLayer) {
          const payload = action.payload as AddLayerPayload;
          return payload?.id
            ? ({
                type: MapActionType.RemoveLayer,
                payload: { id: payload.id }
              } as MapAction)
            : null;
        }
        if (type === MapActionType.AddMarker) {
          const payload = action.payload as { id?: string };
          return payload?.id
            ? ({
                type: MapActionType.RemoveMarker,
                payload: { id: payload.id }
              } as MapAction)
            : null;
        }
        return null;
      })
      .filter((action): action is MapAction => action !== null);
  }

  function extractHighlightActions(choice: Choice): MapAction[] {
    return (
      choice.mapActions?.filter((action) => {
        const type = action.type as MapActionType | string;
        return type === MapActionType.AddLayer || type === MapActionType.AddMarker;
      }) ?? []
    );
  }

  function handleChatResponse(response: ChatResponse) {
    let hasChoices = false;
    const choiceHighlightRemovals = buildHighlightRemovalActions(pendingChoices);
    if (choiceHighlightRemovals.length) {
      mapActionBus.dispatch(choiceHighlightRemovals);
    }
    response.steps?.forEach((step) => {
      if (step.message) {
        appendMessage('bot', step.message);
      }

      if (step.mapActions?.length) {
        mapActionBus.dispatch(step.mapActions);
      }

      if (step.choices?.length) {
        pendingChoices = step.choices;
        pendingChoiceMessage = step.message ?? 'Bitte wähle eine Option.';
        hasChoices = true;
      }
    });

    if (!hasChoices) {
      pendingChoices = [];
      pendingChoiceMessage = '';
    }
  }

  async function clearChatAndMap() {
    const previousSessionId = sessionId;
    messages = [createWelcomeMessage()];
    prompt = '';
    sessionId = createSessionId();
    pendingChoices = [];
    pendingChoiceMessage = '';

    try {
      const response = await fetch('/api/chat', {
        method: 'DELETE',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ sessionId: previousSessionId })
      });

      if (!response.ok) {
        throw new Error(`Server responded with ${response.status}`);
      }
    } catch (error) {
      const message = error instanceof Error
        ? error.message
        : 'Unexpected error while clearing the chat history on the server.';
      appendMessage('bot', `⚠️ ${message}`);
    }

    mapActionBus.dispatch([
      {
        type: MapActionType.ClearMap,
        payload: {}
      }
    ]);
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

  afterUpdate(() => {
    if (!chatMessagesContainer) {
      return;
    }

    chatMessagesContainer.scrollTo({
      top: chatMessagesContainer.scrollHeight,
      behavior: 'smooth'
    });
  });
</script>

{#if isOpen}
  <div class="chat-overlay" id={CHAT_OVERLAY_ID}>
    <div class="chat-header">
      <div class="chat-toolbar" role="toolbar" aria-label="Chat actions">
        <button class="icon-button" type="button" on:click={clearChatAndMap} title="Clear chat & map">
          <TrashCan size={24} aria-hidden="true" />
          <span class="sr-only">Clear chat and map</span>
        </button>
      </div>
      <button
        class="close-button"
        type="button"
        on:click={toggleOverlay}
        aria-label="Close chat"
        aria-controls={CHAT_OVERLAY_ID}
      >
        <CloseOutline size={24} aria-hidden="true" />
      </button>
    </div>
    <div class="chat-messages" aria-live="polite" bind:this={chatMessagesContainer}>
      {#each messages as message (message.id)}
        <div class={`message ${message.role === 'bot' ? 'bot-message' : 'user-message'}`}>
          {message.text}
        </div>
      {/each}
    </div>
    {#if pendingChoices.length}
      <div class="choice-panel" role="alert">
        <p class="choice-message">{pendingChoiceMessage || 'Bitte wähle eine Option:'}</p>
        <div class="choice-buttons">
          {#each pendingChoices as choice (choice.id)}
            <Button
              kind="tertiary"
              disabled={isSending}
              on:click={() => sendChoice(choice)}
              on:mouseenter={() => {
                const highlightActions = extractHighlightActions(choice);
                mapActionBus.dispatch(highlightActions);
              }}
              on:mouseleave={() => {
                const removals = buildHighlightRemovalActions([choice]);
                if (removals.length) {
                  mapActionBus.dispatch(removals);
                }
              }}
              on:focus={() => {
                const highlightActions = extractHighlightActions(choice);
                mapActionBus.dispatch(highlightActions);
              }}
              on:blur={() => {
                const removals = buildHighlightRemovalActions([choice]);
                if (removals.length) {
                  mapActionBus.dispatch(removals);
                }
              }}
            >
              {choice.label}
            </Button>
          {/each}
        </div>
      </div>
    {/if}
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
    aria-controls={CHAT_OVERLAY_ID}
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

  .chat-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .icon-button {
    background: none;
    border: none;
    cursor: pointer;
    padding: 6px;
    border-radius: 6px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: #161616;
    transition: background-color 0.2s;
  }

  .icon-button:hover,
  .icon-button:focus-visible {
    background-color: #e0e0e0;
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

  .choice-panel {
    border: 1px solid #e0e0e0;
    border-radius: 6px;
    padding: 12px;
    margin: 0 8px 12px 8px;
    background: #f4f4f4;
  }

  .choice-message {
    margin: 0 0 8px 0;
    font-weight: 600;
  }

  .choice-buttons {
    display: flex;
    flex-wrap: wrap;
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

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
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
