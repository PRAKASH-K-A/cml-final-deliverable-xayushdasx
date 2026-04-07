import { Injectable } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';

import { ConnectionState, SocketEnvelope } from '../models';

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private socket: WebSocket | null = null;
  private reconnectHandle: number | null = null;

  readonly messages = new Subject<SocketEnvelope<unknown>>();
  readonly connectionState = new BehaviorSubject<ConnectionState>('CONNECTING');

  constructor() {
    this.connect();
  }

  private connect(): void {
    this.connectionState.next('CONNECTING');
    this.socket = new WebSocket('ws://localhost:8090');

    this.socket.onopen = () => {
      this.connectionState.next('LIVE');
    };

    this.socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data) as SocketEnvelope<unknown>;
        this.messages.next(payload);
      } catch (error) {
        console.error('Failed to parse WebSocket message', error);
      }
    };

    this.socket.onclose = () => {
      this.connectionState.next('DISCONNECTED');
      this.scheduleReconnect();
    };

    this.socket.onerror = () => {
      this.connectionState.next('DISCONNECTED');
      this.socket?.close();
    };
  }

  private scheduleReconnect(): void {
    if (this.reconnectHandle !== null) {
      return;
    }

    this.reconnectHandle = window.setTimeout(() => {
      this.reconnectHandle = null;
      this.connect();
    }, 2500);
  }
}
