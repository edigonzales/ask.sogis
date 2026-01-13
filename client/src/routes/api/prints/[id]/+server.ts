import type { RequestHandler } from './$types';

export const GET: RequestHandler = async ({ params, fetch }) => {
  const { id } = params;
  const backendResponse = await fetch(`http://localhost/api/prints/${id}`);

  return new Response(backendResponse.body, {
    status: backendResponse.status,
    statusText: backendResponse.statusText,
    headers: backendResponse.headers
  });
};
