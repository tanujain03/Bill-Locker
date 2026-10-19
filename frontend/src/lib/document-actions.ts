/** Process a selection with bounded concurrency and keep failures available for retry. */
export async function runDocumentAction<T>(ids: string[], action: (id: string) => Promise<T>, concurrency = 4) {
  const pending = [...new Set(ids)];
  const completed: { id: string; value: T }[] = [];
  const failed: { id: string; error: unknown }[] = [];
  async function worker() {
    let id: string | undefined;
    while ((id = pending.shift()) !== undefined) {
      try {
        completed.push({ id, value: await action(id) });
      } catch (error) {
        failed.push({ id, error });
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(Math.max(1, concurrency), pending.length) }, worker));
  return { completed, failed };
}
