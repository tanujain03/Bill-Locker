/** Delete independently so a failed request doesn't prevent the other selected files being removed. */
export async function deleteSelectedDocuments(ids: string[], deleteOne: (id: string) => Promise<unknown>) {
  const pending = [...new Set(ids)];
  const deleted: string[] = [];
  const failed: { id: string; error: unknown }[] = [];
  async function worker() {
    let id: string | undefined;
    while ((id = pending.shift()) !== undefined) {
      try {
        await deleteOne(id);
        deleted.push(id);
      } catch (error) {
        failed.push({ id, error });
      }
    }
  }
  // Bound concurrent requests, including when the user selects a large list.
  await Promise.all(Array.from({ length: Math.min(4, pending.length) }, worker));
  return { deleted, failed };
}
