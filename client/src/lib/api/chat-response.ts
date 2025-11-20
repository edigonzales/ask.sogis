export enum IntentType {
  GotoAddress = 'goto_address',
  LoadLayer = 'load_layer',
  SearchPlace = 'search_place'
}

export enum MapActionType {
  SetView = 'setView',
  AddMarker = 'addMarker',
  AddLayer = 'addLayer'
}

export type Coordinates = [number, number, ...number[]];

export interface BasePayload {
  [key: string]: unknown;
}

export interface SetViewPayload extends BasePayload {
  center: Coordinates;
  zoom: number;
  crs: string;
}

export interface AddMarkerPayload extends BasePayload {
  id: string;
  coord: Coordinates;
  style?: string;
  label?: string;
}

export interface AddLayerPayload extends BasePayload {
  id: string;
  type: string;
  source: Record<string, unknown>;
  visible?: boolean;
}

export type MapActionPayload = SetViewPayload | AddMarkerPayload | AddLayerPayload | BasePayload;

export interface MapAction<TPayload extends MapActionPayload = MapActionPayload> {
  type: MapActionType | string;
  payload: TPayload;
}

export interface Choice {
  id: string;
  label: string;
  confidence?: number;
  mapActions?: MapAction[];
  data?: unknown;
}

export interface ChatStep {
  intent: IntentType | string;
  status: string;
  message: string;
  mapActions: MapAction[];
  choices: Choice[];
}

export interface ChatResponse {
  requestId: string;
  steps: ChatStep[];
  overallStatus: string;
}
