import type { Dataset } from './types';
const DATABASE = 'spotify-dash-v1';
async function database(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const r = indexedDB.open(DATABASE, 1);
    r.onupgradeneeded = () => r.result.createObjectStore('state');
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(new Error('Browser storage is unavailable. Your current session still works, but it may not survive a reload.'));
    r.onblocked = () => reject(new Error('Close other Spotify Dash tabs to unlock browser storage.'));
  });
}
async function read<T>(key: string): Promise<T | undefined> {
  const db = await database();
  try { return await new Promise<T | undefined>((resolve, reject) => {
    const r = db.transaction('state').objectStore('state').get(key);
    r.onsuccess = () => resolve(r.result); r.onerror = () => reject(r.error);
  }); } finally { db.close(); }
}
export const readSaved = () => read<Dataset>('dataset');
export const localDismissed = () => read<boolean>('localDismissed');
export async function saveDataset(dataset: Dataset) {
  const db = await database();
  try { await new Promise<void>((resolve, reject) => {
    const t = db.transaction('state', 'readwrite'); t.objectStore('state').put(dataset, 'dataset');
    t.oncomplete = () => resolve(); t.onerror = () => reject(new Error('Not enough browser storage to remember this history. The current session still works.')); t.onabort = () => reject(new Error('Browser storage was interrupted. The current session still works.'));
  }); } finally { db.close(); }
}
export async function deleteSaved() {
  const db = await database();
  try { await new Promise<void>((resolve, reject) => {
    const t = db.transaction('state', 'readwrite'); const s = t.objectStore('state'); s.delete('dataset'); s.put(true, 'localDismissed');
    t.oncomplete = () => resolve(); t.onerror = () => reject(t.error);
  }); } finally { db.close(); }
}
