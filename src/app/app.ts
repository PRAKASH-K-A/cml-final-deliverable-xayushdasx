import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ConnectionState, OptionUpdateEvent, OrderEvent, TradeEvent } from './models';
import { WebsocketService } from './services/websocket.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly destroyRef = inject(DestroyRef);
  private readonly wsService = inject(WebsocketService);

  protected readonly connectionState = signal<ConnectionState>('CONNECTING');
  protected readonly orders = signal<OrderEvent[]>([]);
  protected readonly trades = signal<TradeEvent[]>([]);
  protected readonly latestOption = signal<OptionUpdateEvent | null>(null);

  protected readonly filledOrders = computed(
    () => this.orders().filter((order) => order.status === 'FILLED').length
  );

  protected readonly buyVolume = computed(
    () => this.orders()
      .filter((order) => order.side === '1')
      .reduce((total, order) => total + order.quantity, 0)
  );

  protected readonly sellVolume = computed(
    () => this.orders()
      .filter((order) => order.side === '2')
      .reduce((total, order) => total + order.quantity, 0)
  );

  protected readonly tradedNotional = computed(
    () => this.trades().reduce((total, trade) => total + (trade.price * trade.quantity), 0)
  );

  constructor() {
    this.wsService.connectionState
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((state) => this.connectionState.set(state));

    this.wsService.messages
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((message) => {
        if (message.type === 'ORDER') {
          this.pushOrder(message.data as OrderEvent);
          return;
        }

        if (message.type === 'TRADE') {
          this.trades.update((trades) => [message.data as TradeEvent, ...trades].slice(0, 24));
          return;
        }

        if (message.type === 'OPTION_UPDATE') {
          this.latestOption.set(message.data as OptionUpdateEvent);
        }
      });
  }

  protected sideLabel(side: '1' | '2'): string {
    return side === '1' ? 'BUY' : 'SELL';
  }

  protected connectionLabel(): string {
    const state = this.connectionState();
    if (state === 'LIVE') {
      return 'STREAM LIVE';
    }
    if (state === 'CONNECTING') {
      return 'CONNECTING';
    }
    return 'FEED OFFLINE';
  }

  private pushOrder(incoming: OrderEvent): void {
    this.orders.update((orders) => {
      const next = [incoming, ...orders.filter((order) => order.orderId !== incoming.orderId)];
      return next.slice(0, 24);
    });
  }
}
