import { useState, type ReactNode } from 'react';
import {
  PlayCircle,
  RefreshCw,
  PackageX,
  Ban,
  CheckCircle2,
  XCircle,
  X,
  Waves,
  Activity,
  Terminal,
} from 'lucide-react';
import { apiClient, getErrorMessage } from '../api/client';
import { useRecentSimEvents, useSimulatorStatus } from '../api/hooks';
import { Card, CardBody, CardHeader } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { LiveTransactionFeed } from '../components/operations/LiveTransactionFeed';
import { cn } from '../lib/utils';
import { formatCompactNumber } from '../utils/formatters';
import type { PopularitySyncResponse, SimulatorStatus } from '../types';

interface Toast {
  id: number;
  kind: 'success' | 'error';
  message: string;
}

let toastSeq = 0;

function useToasts() {
  const [toasts, setToasts] = useState<Toast[]>([]);

  function push(kind: Toast['kind'], message: string) {
    const id = ++toastSeq;
    setToasts((current) => [...current, { id, kind, message }]);
    setTimeout(() => {
      setToasts((current) => current.filter((t) => t.id !== id));
    }, 5000);
  }

  function dismiss(id: number) {
    setToasts((current) => current.filter((t) => t.id !== id));
  }

  return { toasts, push, dismiss };
}

function ToastStack({ toasts, dismiss }: { toasts: Toast[]; dismiss: (id: number) => void }) {
  return (
    <div className="fixed bottom-6 right-6 z-50 flex w-80 flex-col gap-2">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={cn(
            'flex items-start gap-2 rounded-lg border px-4 py-3 shadow-lg',
            toast.kind === 'success'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
              : 'border-rose-200 bg-rose-50 text-rose-800',
          )}
        >
          {toast.kind === 'success' ? (
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
          ) : (
            <XCircle className="mt-0.5 h-4 w-4 shrink-0" />
          )}
          <p className="flex-1 text-sm">{toast.message}</p>
          <button onClick={() => dismiss(toast.id)} aria-label="Dismiss">
            <X className="h-3.5 w-3.5 opacity-60 hover:opacity-100" />
          </button>
        </div>
      ))}
    </div>
  );
}

function ActionCard({
  icon,
  accent,
  title,
  description,
  children,
}: {
  icon: ReactNode;
  accent: 'indigo' | 'emerald' | 'amber' | 'rose';
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <Card accent={accent}>
      <CardHeader title={title} description={description} action={icon} />
      <CardBody>{children}</CardBody>
    </Card>
  );
}

export function OperationsPage() {

  const { status, refresh: refreshStatus } = useSimulatorStatus();
  // Live Transaction Feed -- polls every 2s per this task's spec.
  const { events: recentEvents, isLoading: eventsLoading } = useRecentSimEvents();

  const [simulatorLoading, setSimulatorLoading] = useState(false);
  const [pulseLoading, setPulseLoading] = useState(false);
  const [syncLoading, setSyncLoading] = useState(false);
  const [stockProductId, setStockProductId] = useState('');
  const [stockLoading, setStockLoading] = useState(false);
  const [orderId, setOrderId] = useState('');
  const [cancelLoading, setCancelLoading] = useState(false);

  async function runSimulatorStart() {
    setSimulatorLoading(true);
    try {
      // POST body is optional (SimulatorStartRequest, @RequestBody(required = false));
      // omitting it falls back to the app.simulator.* config defaults.
      const { data } = await apiClient.post<SimulatorStatus>('/simulator/start');
      push(
        'success',
        `Simulator running -- target ${data.targetIterations} iterations, ${data.actionsCompleted} completed so far.`,
      );
      refreshStatus();
    } catch (error) {
      push('error', getErrorMessage(error));
    } finally {
      setSimulatorLoading(false);
    }
  }

  async function runSimulatorStop() {
    setSimulatorLoading(true);
    try {
      const { data } = await apiClient.post<SimulatorStatus>('/simulator/stop');
      push('success', `Simulator stop requested -- ${data.actionsCompleted} actions completed this run.`);
      refreshStatus();
    } catch (error) {
      push('error', getErrorMessage(error));
    } finally {
      setSimulatorLoading(false);
    }
  }

  /**
   * (POST /simulator/pulse/start, /pulse/stop -- additive to, independent
   * of, the burst/iterations mode above). A 409 Conflict here most often
   * means pulse mode is already in the state you're asking for (e.g.
   * clicking "Start pulse" twice) -- getErrorMessage surfaces the backend's
   * own message ("Simulator pulse is already running.") rather than a
   * generic failure.
   **/
  async function runPulseToggle(start: boolean) {
    setPulseLoading(true);
    try {
      await apiClient.post<SimulatorStatus>(
        start ? '/simulator/pulse/start' : '/simulator/pulse/stop',
      );
      push(
        'success',
        start
          ? 'Continuous pulse started -- 2-5 events every ~12s.'
          : 'Continuous pulse stopped.',
      );
      refreshStatus();
    } catch (error) {
      push('error', getErrorMessage(error));
    } finally {
      setPulseLoading(false);
    }
  }

  async function runPopularitySync() {
    setSyncLoading(true);
    try {
      const { data } = await apiClient.post<PopularitySyncResponse>('/search/admin/sync/popularity');
      if (data.sourceStatus !== 'AVAILABLE') {
        push(
          'error',
          `Spark batch popularity table is ${data.sourceStatus.toLowerCase()} -- run the Spark batch job first.`,
        );
      } else {
        push('success', `Synced popularityScore for ${data.productsUpdated} products to Elasticsearch.`);
      }
    } catch (error) {
      push('error', getErrorMessage(error));
    } finally {
      setSyncLoading(false);
    }
  }

  async function runToggleStock(inStock: boolean) {
    const id = stockProductId.trim();
    if (!id) {
      push('error', 'Enter a product ID first.');
      return;
    }
    setStockLoading(true);
    try {
      await apiClient.patch(`/search/admin/products/${id}/availability`, null, {
        params: { inStock },
      });
      push('success', `Product ${id} marked ${inStock ? 'in stock' : 'out of stock'}.`);
    } catch (error) {
      push('error', getErrorMessage(error));
    } finally {
      setStockLoading(false);
    }
  }

  async function runCancelOrder() {
    const id = orderId.trim();
    if (!id) {
      push('error', 'Enter an order ID first.');
      return;
    }
    setCancelLoading(true);
    try {
      await apiClient.patch(`/orders/${id}/status`, null, {
        params: { status: 'CANCELLED' },
      });
      push('success', `Order ${id} cancelled -- salesCount will be decremented.`);
    } catch (error) {
      push('error', getErrorMessage(error));
    } finally {
      setCancelLoading(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* PHASE 2: live status card -- polls GET /simulator/status every 4s. */}
      <Card accent={status?.running || status?.pulseRunning ? 'emerald' : 'slate'}>
        <CardHeader
          title="Simulator status"
          description="Live, polled every 4 seconds"
          action={<Activity className="h-4 w-4 text-slate-400" />}
        />
        <CardBody>
          {!status ? (
            <p className="text-sm text-slate-400">Loading...</p>
          ) : (
            <div className="flex flex-wrap items-center gap-6">
              <div className="flex items-center gap-2">
                <Badge variant={status.running ? 'success' : 'neutral'}>
                  {status.running ? 'Burst running' : 'Burst idle'}
                </Badge>
                <Badge variant={status.pulseRunning ? 'success' : 'neutral'}>
                  {status.pulseRunning ? 'Pulse running' : 'Pulse idle'}
                </Badge>
              </div>
              <div>
                <p className="text-xs text-slate-400">Actions completed</p>
                <p className="font-mono text-sm font-semibold text-slate-900">
                  {formatCompactNumber(status.actionsCompleted)}
                </p>
              </div>
              <div>
                <p className="text-xs text-slate-400">Actions failed</p>
                <p className="font-mono text-sm font-semibold text-slate-900">
                  {formatCompactNumber(status.actionsFailed)}
                </p>
              </div>
              {status.lastError && (
                <div className="min-w-0 flex-1">
                  <p className="text-xs text-slate-400">Last error</p>
                  <p className="truncate text-xs text-rose-600">{status.lastError}</p>
                </div>
              )}
            </div>
          )}
        </CardBody>
      </Card>

      {/* PHASE 3B: Live Transaction Feed -- see component doc for why this
          is styled as a dark terminal window instead of the light
          DataTable used by the Dashboard's data grids. */}
      <Card accent="slate" className="overflow-hidden bg-slate-950">
        <CardHeader
          title="Live transaction feed"
          description="Raw simulator events as they happen, polled every 2 seconds"
          action={<Terminal className="h-4 w-4 text-slate-400" />}
          className="bg-white"
        />
        <CardBody>
          <LiveTransactionFeed events={recentEvents} isLoading={eventsLoading} />
        </CardBody>
      </Card>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <ActionCard
          icon={<PlayCircle className="h-4 w-4 text-indigo-500" />}
          accent="indigo"
          title="Order simulator (burst)"
          description="Runs a fixed batch of synthetic events, then stops on its own"
        >
          <div className="flex gap-2">
            <Button onClick={runSimulatorStart} isLoading={simulatorLoading} disabled={status?.running}>
              Start burst
            </Button>
            <Button
              variant="secondary"
              onClick={runSimulatorStop}
              isLoading={simulatorLoading}
              disabled={!status?.running}
            >
              Stop
            </Button>
          </div>
        </ActionCard>

        <ActionCard
          icon={<Waves className="h-4 w-4 text-indigo-500" />}
          accent="indigo"
          title="Continuous pulse"
          description="Phase 1: trickles 2-5 realistic events into Kafka every ~12s until stopped -- independent of the burst mode above"
        >
          <div className="flex gap-2">
            <Button
              onClick={() => runPulseToggle(true)}
              isLoading={pulseLoading}
              disabled={status?.pulseRunning}
            >
              Start pulse
            </Button>
            <Button
              variant="secondary"
              onClick={() => runPulseToggle(false)}
              isLoading={pulseLoading}
              disabled={!status?.pulseRunning}
            >
              Stop pulse
            </Button>
          </div>
        </ActionCard>

        <ActionCard
          icon={<RefreshCw className="h-4 w-4 text-emerald-500" />}
          accent="emerald"
          title="Spark popularity sync"
          description="Push the latest time-decayed popularityScore batch into Elasticsearch"
        >
          <Button onClick={runPopularitySync} isLoading={syncLoading} variant="secondary">
            Sync now
          </Button>
        </ActionCard>

        <ActionCard
          icon={<PackageX className="h-4 w-4 text-amber-500" />}
          accent="amber"
          title="Toggle stock status"
          description="Flip a product's inStock flag directly in the search index"
        >
          <div className="flex gap-2">
            <input
              type="text"
              value={stockProductId}
              onChange={(e) => setStockProductId(e.target.value)}
              placeholder="Product ID"
              className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-50"
            />
            <Button
              variant="secondary"
              isLoading={stockLoading}
              onClick={() => runToggleStock(true)}
            >
              Mark in stock
            </Button>
            <Button
              variant="destructive"
              isLoading={stockLoading}
              onClick={() => runToggleStock(false)}
            >
              Mark out
            </Button>
          </div>
        </ActionCard>

        <ActionCard
          icon={<Ban className="h-4 w-4 text-rose-500" />}
          accent="rose"
          title="Cancel order"
          description="Trigger the compensating transaction that decrements salesCount"
        >
          <div className="flex gap-2">
            <input
              type="text"
              value={orderId}
              onChange={(e) => setOrderId(e.target.value)}
              placeholder="Order ID"
              className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-50"
            />
            <Button variant="destructive" isLoading={cancelLoading} onClick={runCancelOrder}>
              Cancel order
            </Button>
          </div>
        </ActionCard>
      </div>

      <ToastStack toasts={toasts} dismiss={dismiss} />
    </div>
  );
}
