import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

export const POST: RequestHandler = async ({ request }) => {
  // Consume the payload even though the mock response does not use it yet.
  await request.json().catch(() => ({}));

  const response = {
    requestId: '1',
    steps: [
      {
        intent: 'goto_address',
        status: 'ok',
        message: '1 Treffer gefunden.',
        mapActions: [
          {
            type: 'setView',
            payload: {
              zoom: 17,
              center: [2605899.0, 1229278.0, 2605899.0, 1229278.0],
              crs: 'EPSG:2056'
            }
          },
          {
            type: 'addMarker',
            payload: {
              style: 'pin-default',
              coord: [2605899.0, 1229278.0, 2605899.0, 1229278.0],
              id: 'addr-623490242',
              label: 'Gefundene Adresse'
            }
          }
        ],
        choices: []
      }
    ],
    overallStatus: 'ok'
  };

  return json(response);
};
