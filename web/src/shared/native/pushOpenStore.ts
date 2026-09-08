export type OpenRecord = { key: string; userId: number; messageId: string; notificationId: string; expiresAt: number; outcome: 'COMPLETED' | 'UNAVAILABLE' }
export type ReadIntent = { key: string; userId: number; notificationId: string; expiresAt: number; nextAttemptAt: number; attempts: number }
export interface PushOpenStore {
  get(key: string): Promise<OpenRecord | undefined>
  complete(record: OpenRecord, read?: ReadIntent): Promise<void>
  reads(userId: number): Promise<ReadIntent[]>
  update(read: ReadIntent, remove?: boolean): Promise<void>
}
let database: Promise<IDBDatabase> | undefined
function db() {
  return database ??= new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open('nalq-push-open-v1', 1)
    request.onupgradeneeded = () => {
      request.result.createObjectStore('opens', { keyPath: 'key' })
      request.result.createObjectStore('reads', { keyPath: 'key' })
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => { database = undefined; reject(request.error) }
  })
}
async function transaction<T>(names: string[], work: (tx: IDBTransaction, result: (value: T) => void) => void): Promise<T> {
  const database = await db()
  return new Promise<T>((resolve, reject) => {
    const tx = database.transaction(names, 'readwrite')
    let value: T
    tx.oncomplete = () => resolve(value)
    tx.onabort = tx.onerror = () => reject(tx.error ?? new Error('Push storage failed'))
    work(tx, (next) => { value = next })
  })
}
export const pushOpenStore: PushOpenStore = {
  get(key) { return transaction(['opens'], (tx, done) => {
    const store = tx.objectStore('opens'); const request = store.get(key)
    request.onsuccess = () => {
      const record = request.result as OpenRecord | undefined
      if (record && record.expiresAt <= Date.now()) { store.delete(key); done(undefined) }
      else done(record)
    }
  }) },
  complete(record, read) { return transaction(['opens', 'reads'], (tx) => {
    tx.objectStore('opens').put(record)
    if (read) tx.objectStore('reads').put(read)
    const request = tx.objectStore('opens').openCursor()
    request.onsuccess = () => { const cursor = request.result; if (cursor) { if (cursor.value.expiresAt <= Date.now()) cursor.delete(); cursor.continue() } }
  }) },
  reads(userId) { return transaction(['reads'], (tx, done) => {
    const items: ReadIntent[] = []; const request = tx.objectStore('reads').openCursor()
    request.onsuccess = () => {
      const cursor = request.result
      if (!cursor) { done(items); return }
      const item = cursor.value as ReadIntent
      if (item.expiresAt <= Date.now()) cursor.delete()
      else if (item.userId === userId) items.push(item)
      cursor.continue()
    }
  }) },
  update(read, remove = false) { return transaction(['reads'], (tx) => {
    const store = tx.objectStore('reads'); if (remove) store.delete(read.key); else store.put(read)
  }) },
}

export async function clearPushOpenUser(userId: number) {
  await transaction<void>(['opens', 'reads'], (tx) => {
    for (const name of ['opens', 'reads']) {
      const request = tx.objectStore(name).openCursor()
      request.onsuccess = () => { const cursor = request.result; if (cursor) { if (cursor.value.userId === userId) cursor.delete(); cursor.continue() } }
    }
  })
}
