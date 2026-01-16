<script lang="ts">
  import { Button, Checkbox, TextArea } from 'carbon-components-svelte';
  import ChatBot from 'carbon-icons-svelte/lib/ChatBot.svelte';
  import CloseOutline from 'carbon-icons-svelte/lib/CloseOutline.svelte';
  import Help from 'carbon-icons-svelte/lib/Help.svelte';
  import ListChecked from 'carbon-icons-svelte/lib/ListChecked.svelte';
  import TrashCan from 'carbon-icons-svelte/lib/TrashCan.svelte';
  import { afterUpdate, onMount } from 'svelte';
  import { CHAT_OVERLAY_ID, TOC_OVERLAY_ID } from '$lib/constants';
  import type { AddLayerPayload, ChatResponse, Choice, MapAction } from '$lib/api/chat-response';
  import { MapActionType } from '$lib/api/chat-response';
  import { mapActionBus } from '$lib/stores/mapActions';
  import { layerStore } from '$lib/stores/layers';

  type Role = 'bot' | 'user';
  type ChatMessage = { id: string; role: Role; text: string; isHtml?: boolean };

  let chatMessagesContainer: HTMLDivElement;
  const createSessionId = () => crypto.randomUUID?.() ?? Math.random().toString(36).slice(2);
  let sessionId = createSessionId();
  let isChatOpen = true;
  let isTocOpen = false;
  let prompt = '';
  let isSending = false;
  const createMessageId = () => crypto.randomUUID?.() ?? Math.random().toString(36).slice(2);
  const createWelcomeMessage = () => ({
    id: createMessageId(),
    role: 'bot' as const,
    text: 'Hello! How can I help you with the map today?',
    isHtml: false
  });

  let messages: ChatMessage[] = [createWelcomeMessage()];
  let pendingChoices: Choice[] = [];
  let pendingChoiceMessage = '';
  const blockedChoiceFragments = ['Quelle Bund', 'Quelle geodienste.ch', 'Quelle Emch+Berger'];

  function normalizeChoices(rawChoices: ChatResponse['steps'][number]['choices']): Choice[] {
    if (Array.isArray(rawChoices)) {
      return rawChoices;
    }
    if (!rawChoices) {
      return [];
    }
    return Object.values(rawChoices);
  }

  function toggleChatOverlay() {
    isChatOpen = !isChatOpen;
    if (isChatOpen) {
      isTocOpen = false;
    }
  }

  function toggleTocOverlay() {
    isTocOpen = !isTocOpen;
    if (isTocOpen) {
      isChatOpen = false;
    }
  }

  function handleTocVisibilityToggle(id: string, event: Event) {
    const target = event?.currentTarget as HTMLInputElement | null;
    const visible =
      (event as CustomEvent<{ checked?: boolean }>)?.detail?.checked ??
      target?.checked ??
      false;
    mapActionBus.dispatch([
      {
        type: MapActionType.SetLayerVisibility,
        payload: { id, visible }
      }
    ]);
  }

  function handleTocRemove(id: string) {
    mapActionBus.dispatch([
      {
        type: MapActionType.RemoveLayer,
        payload: { id }
      }
    ]);
  }

  function isBlockedChoice(choice: Choice) {
    const label = choice?.label ?? '';
    return blockedChoiceFragments.some((fragment) => label.includes(fragment));
  }

  function shouldSkipLayerAction(action: MapAction) {
    if (action.type !== MapActionType.AddLayer) {
      return false;
    }
    const payload = action.payload as AddLayerPayload;
    const label = typeof payload?.label === 'string' ? payload.label : '';
    return blockedChoiceFragments.some((fragment) => label.includes(fragment));
  }

  function filterMapActions(actions: MapAction[]) {
    return actions.filter((action) => !shouldSkipLayerAction(action));
  }

  function appendMessage(role: Role, text: string, isHtml = false) {
    messages = [...messages, { id: createMessageId(), role, text, isHtml }];
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

  function renderOerebExtractMessage(message: string | undefined) {
    if (!message) {
      return null;
    }
    const urls = message.match(/https?:\/\/\S+/g) ?? [];
    const pdfUrl = urls[0];
    const mapUrl = urls[1];
    if (!pdfUrl || !mapUrl) {
      return null;
    }
    const template = `
      <div>ÖREB-Auszug erstellt.</div>
      <div>PDF: <a href="${pdfUrl}" target="_blank" rel="noreferrer">${pdfUrl}</a></div>
      <div>Fachanwendung: <a href="${mapUrl}" target="_blank" rel="noreferrer">${mapUrl}</a></div>
    `;
    return template;
  }

  function renderCadastralPlanMessage(message: string | undefined) {
    if (!message) {
      return null;
    }
    const urls = message.match(/https?:\/\/\S+/g) ?? [];
    const pdfUrl = urls[0];
    if (!pdfUrl) {
      return null;
    }
    const template = `
      <div>Auszug aus dem Plan für das Grundbuch wurde erstellt. Laden sie ihn <a href="${pdfUrl}" target="_blank" rel="noreferrer">hier</a> herunter.</div>
    `;
    return template;
  }

  function handleChatResponse(response: ChatResponse) {
    let hasChoices = false;
    const choiceHighlightRemovals = buildHighlightRemovalActions(pendingChoices);
    if (choiceHighlightRemovals.length) {
      mapActionBus.dispatch(choiceHighlightRemovals);
    }
    response.steps?.forEach((step) => {
      if (step.message) {
        if (step.intent === 'oereb_extract') {
          const html = renderOerebExtractMessage(step.message);
          if (html) {
            appendMessage('bot', html, true);
          } else {
            appendMessage('bot', step.message);
          }
        } else if (step.intent === 'cadastral_plan') {
          const html = renderCadastralPlanMessage(step.message);
          if (html) {
            appendMessage('bot', html, true);
          } else {
            appendMessage('bot', step.message);
          }
        } else if (step.intent === 'geothermal_probe_assessment') {
          appendMessage('bot', step.message, true);
        } else {
          appendMessage('bot', step.message);
        }
      }

      if (step.mapActions?.length) {
        mapActionBus.dispatch(filterMapActions(step.mapActions));
      }

      const stepChoices = normalizeChoices(step.choices);
      if (stepChoices.length) {
        pendingChoices = stepChoices;
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

{#if isChatOpen}
  <div class="chat-overlay" id={CHAT_OVERLAY_ID}>
    <div class="chat-header">
      <div class="chat-header-left">
        <h3 class="overlay-title">Chat with LLM</h3>
        <div class="chat-toolbar" role="toolbar" aria-label="Chat actions">
          <button class="icon-button" type="button" on:click={clearChatAndMap} title="Clear chat & map">
            <TrashCan size={24} aria-hidden="true" />
            <span class="sr-only">Clear chat and map</span>
          </button>
        </div>
      </div>
      <button
        class="close-button"
        type="button"
        on:click={toggleChatOverlay}
        aria-label="Close chat"
        aria-controls={CHAT_OVERLAY_ID}
      >
        <CloseOutline size={24} aria-hidden="true" />
      </button>
    </div>
    <div class="chat-messages" aria-live="polite" bind:this={chatMessagesContainer}>
      {#each messages as message (message.id)}
        <div class={`message ${message.role === 'bot' ? 'bot-message' : 'user-message'}`}>
          {#if message.isHtml}
            {@html message.text}
          {:else}
            {message.text}
          {/if}
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
              disabled={isSending || isBlockedChoice(choice)}
              on:click={() => {
                if (!isBlockedChoice(choice)) {
                  sendChoice(choice);
                }
              }}
              on:mouseenter={() => {
                if (isBlockedChoice(choice)) {
                  return;
                }
                const highlightActions = extractHighlightActions(choice);
                mapActionBus.dispatch(highlightActions);
              }}
              on:mouseleave={() => {
                if (isBlockedChoice(choice)) {
                  return;
                }
                const removals = buildHighlightRemovalActions([choice]);
                if (removals.length) {
                  mapActionBus.dispatch(removals);
                }
              }}
              on:focus={() => {
                if (isBlockedChoice(choice)) {
                  return;
                }
                const highlightActions = extractHighlightActions(choice);
                mapActionBus.dispatch(highlightActions);
              }}
              on:blur={() => {
                if (isBlockedChoice(choice)) {
                  return;
                }
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

{#if isTocOpen}
  <div class="toc-overlay" id={TOC_OVERLAY_ID}>
    <div class="toc-header">
      <h3 class="overlay-title">Table of Contents</h3>
      <button
        class="close-button"
        type="button"
        on:click={toggleTocOverlay}
        aria-label="Close table of contents"
        aria-controls={TOC_OVERLAY_ID}
      >
        <CloseOutline size={24} aria-hidden="true" />
      </button>
    </div>
    <div class="toc-body">
      {#if $layerStore.length === 0}
        <p class="toc-empty">Noch keine Layer geladen.</p>
      {:else}
        <ul class="toc-list">
          {#each $layerStore as layer (layer.id)}
            <li class="toc-item">
              <Checkbox
                id={`toc-${layer.id}`}
                checked={layer.visible}
                labelText={layer.label}
                on:change={(event) => handleTocVisibilityToggle(layer.id, event)}
              />
              <button
                class="icon-button toc-remove"
                type="button"
                title="Layer entfernen"
                on:click={() => handleTocRemove(layer.id)}
              >
                <TrashCan size={20} aria-hidden="true" />
                <span class="sr-only">Layer entfernen</span>
              </button>
            </li>
          {/each}
        </ul>
      {/if}
    </div>
  </div>
{/if}

<div class="sidebar">
  <div class="sidebar-icons">
    <button
      class={`icon chat-icon ${isChatOpen ? 'is-active' : ''}`}
      type="button"
      title="Open Chat"
      aria-controls={CHAT_OVERLAY_ID}
      aria-expanded={isChatOpen}
      aria-pressed={isChatOpen}
      on:click={toggleChatOverlay}
    >
      <ChatBot size={24} aria-hidden="true" />
      <span class="sr-only">Open chat overlay</span>
    </button>
    <button
      class={`icon toc-icon ${isTocOpen ? 'is-active' : ''}`}
      type="button"
      title="Open Table of Contents"
      aria-controls={TOC_OVERLAY_ID}
      aria-expanded={isTocOpen}
      aria-pressed={isTocOpen}
      on:click={toggleTocOverlay}
    >
      <ListChecked size={24} aria-hidden="true" />
      <span class="sr-only">Open table of contents</span>
    </button>
    <button class="icon help-icon" type="button" title="Help">
      <Help size={24} aria-hidden="true" />
      <span class="sr-only">Help</span>
    </button>
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

  .chat-header,
  .toc-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    padding-bottom: 8px;
    border-bottom: 1px solid #e0e0e0;
  }

  .chat-header-left {
    display: flex;
    align-items: center;
    gap: 12px;
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

  .overlay-title {
    font-size: 1rem;
    font-weight: 600;
    margin: 0;
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
    max-height: 240px;
    overflow-y: auto;
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
    border: none;
    background: none;
    padding: 0;
    cursor: pointer;
  }

  .icon:hover {
    background-color: #e0e0e0;
  }

  .icon.is-active {
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

  .toc-overlay {
    position: absolute;
    top: 0;
    left: 64px;
    width: 380px;
    height: 100vh;
    background-color: rgba(255, 255, 255, 0.9);
    backdrop-filter: blur(10px);
    box-shadow: -4px 0 12px rgba(0, 0, 0, 0.15);
    display: flex;
    flex-direction: column;
    z-index: 1000;
    padding: 16px;
    box-sizing: border-box;
  }

  .toc-body {
    flex: 1;
    overflow-y: auto;
    padding-right: 4px;
  }

  .toc-empty {
    margin: 12px 0;
    color: #525252;
  }

  .toc-list {
    list-style: none;
    padding: 0;
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .toc-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 8px;
    border-radius: 6px;
    background: #f4f4f4;
  }

  .toc-remove {
    flex-shrink: 0;
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
