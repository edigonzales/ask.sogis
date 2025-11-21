import type { RequestHandler } from './$types';

export const POST: RequestHandler = async ({ request, fetch }) => {
  const backendResponse = await fetch('http://localhost:8080/api/chat', {
    method: 'POST',
    headers: request.headers,
    body: request.body
  });

  return new Response(backendResponse.body, {
    status: backendResponse.status,
    statusText: backendResponse.statusText,
    headers: backendResponse.headers
  });
};
