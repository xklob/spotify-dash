import type { DatasetMeta, Filters, HistoryPage, QueryResult } from './types';
const worker = new Worker(new URL('./worker.ts', import.meta.url), { type: 'module' });
let nextId = 0;
const pending = new Map<number, { resolve: (value: unknown) => void; reject: (reason: Error) => void; progress?: (message: string) => void }>();
worker.onmessage = ({ data }) => {
  const task = pending.get(data.id); if (!task) return;
  if (data.progress) { task.progress?.(data.progress); return; }
  pending.delete(data.id);
  if (data.error) task.reject(new Error(data.error)); else task.resolve(data.result);
};
worker.onerror = () => {
  for (const p of pending.values()) p.reject(new Error('The background data engine stopped. Reload the page to recover your saved history.'));
  pending.clear();
};
function request<T>(method: string, args: unknown[] = [], progress?: (message: string) => void): Promise<T> {
  return new Promise((resolve, reject) => {
    const id = ++nextId;
    pending.set(id, { resolve: value => resolve(value as T), reject, progress });
    worker.postMessage({ id, method, args });
  });
}
export const initialize = () => request<DatasetMeta | null>('initialize');
export const importFiles = (files: File[], progress?: (message: string) => void) => request<DatasetMeta>('importFiles', [files], progress);
export const loadDemo = () => request<DatasetMeta>('loadDemo');
export const query = (filters: Filters) => request<QueryResult>('query', [filters]);
export const history = (filters: Filters, page = 0, pageSize = 50, order: 'newest' | 'oldest' = 'newest') => request<HistoryPage>('history', [filters, page, pageSize, order]);
export const exportCsv = (filters: Filters) => request<string>('exportCsv', [filters]);
export const clearData = () => request<void>('clearData');
