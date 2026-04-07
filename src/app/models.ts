export type StreamType = 'ORDER' | 'TRADE' | 'OPTION_UPDATE';

export interface SocketEnvelope<T> {
  type: StreamType;
  data: T;
}

export interface OrderEvent {
  orderId: string;
  clOrdID: string;
  symbol: string;
  side: '1' | '2';
  price: number;
  quantity: number;
  leavesQty: number;
  filledQty: number;
  status: 'NEW' | 'PARTIALLY_FILLED' | 'FILLED';
  timestamp: number;
}

export interface TradeEvent {
  execId: string;
  symbol: string;
  side: '1' | '2';
  price: number;
  quantity: number;
  cumQty: number;
  avgPx: number;
  incomingOrderId: string;
  incomingClOrdID: string;
  restingOrderId: string;
  restingClOrdID: string;
  timestamp: number;
}

export interface OptionUpdateEvent {
  optionSymbol: string;
  underlyingSymbol: string;
  spotPrice: number;
  strike: number;
  callPrice: number;
  putPrice: number;
  rate: number;
  volatility: number;
  timeToExpiry: number;
  timestamp: number;
}

export type ConnectionState = 'CONNECTING' | 'LIVE' | 'DISCONNECTED';
