export enum IntentType {
  GotoAddress = 'goto_address',
  LoadLayer = 'load_layer',
  SearchPlace = 'search_place'
}

export enum MapActionType {
  SetView = 'setView',
  AddMarker = 'addMarker',
  RemoveMarker = 'removeMarker',
  AddLayer = 'addLayer',
  RemoveLayer = 'removeLayer',
  SetLayerVisibility = 'setLayerVisibility',
  ClearMap = 'clearMap'
}

export type Coordinates = [number, number, ...number[]];
export type Extent = [number, number, number, number];

export interface BasePayload {
  [key: string]: unknown;
}

export interface SetViewPayload extends BasePayload {
  center: Coordinates;
  zoom: number;
  crs: string;
  extent?: Extent;
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

export interface RemoveLayerPayload extends BasePayload {
  id: string;
}

export interface SetLayerVisibilityPayload extends BasePayload {
  id: string;
  visible: boolean;
}

export type MapActionPayload =
  | SetViewPayload
  | AddMarkerPayload
  | RemoveMarkerPayload
  | AddLayerPayload
  | RemoveLayerPayload
  | SetLayerVisibilityPayload
  | BasePayload;

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
