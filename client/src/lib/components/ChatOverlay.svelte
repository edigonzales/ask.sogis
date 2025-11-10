<script>
  import { Button, TextArea } from 'carbon-components-svelte';
</script>

<div class="chat-overlay">
  <div class="chat-header">
    <h3>Chat with LLM</h3>
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

<style>
  .chat-overlay {
    position: absolute;
    top: 10px;
    left: 10px;
    width: 300px;
    height: calc(100% - 20px);
    background-color: rgba(255, 255, 255, 0.9);
    backdrop-filter: blur(10px);
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    display: flex;
    flex-direction: column;
    z-index: 1000;
    padding: 16px;
    box-sizing: border-box;
    border: 1px solid rgba(0, 0, 0, 0.1);
  }

  .chat-header {
    margin-bottom: 16px;
    padding-bottom: 8px;
    border-bottom: 1px solid #e0e0e0;
  }

  .chat-header h3 {
    margin: 0;
    font-size: 1.2rem;
    color: #161616;
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

  /* Carbon Components Svelte specific styles */
  .input-textarea textarea {
    width: 100%;
  }
  
  /* Ensure Carbon components display properly */
  :global(.bx--text-area) {
    width: 100%;
  }
</style>