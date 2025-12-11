import type { RequestHandler } from './$types';

export const POST: RequestHandler = async ({ request, fetch }) => {
  const { sessionId, userMessage, choiceId } = await request.json();

  const backendResponse = await fetch('http://localhost:8080/api/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ sessionId, userMessage, choiceId }),
    // required for streaming bodies in Node's undici implementation
    duplex: 'half'
  });

  return new Response(backendResponse.body, {
    status: backendResponse.status,
    statusText: backendResponse.statusText,
    headers: backendResponse.headers
  });
};

export const DELETE: RequestHandler = async ({ request, fetch }) => {
  const { sessionId } = await request.json();

  const backendResponse = await fetch('http://localhost:8080/api/chat', {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ sessionId })
  });

  return new Response(backendResponse.body, {
    status: backendResponse.status,
    statusText: backendResponse.statusText,
    headers: backendResponse.headers
  });
};
