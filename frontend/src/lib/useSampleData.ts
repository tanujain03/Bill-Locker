import { useCallback, useEffect, useState } from 'react';
import { errorMessage } from './api';
import { useFeedback } from './feedback-context';
import { addSampleData, getSampleData, removeSampleData } from './sample-data';

/**
 * The sample data on the dashboard and in Settings: how many sample bills there are, and
 * adding or removing them (removing asks first). `onChanged` runs after either, so the page
 * can reload its numbers.
 */
export function useSampleData(onChanged?: () => void) {
  const { confirm, toast } = useFeedback();
  const [count, setCount] = useState<number | null>(null); // null while loading
  const [busy, setBusy] = useState<'adding' | 'removing' | null>(null);

  const load = useCallback(() => {
    getSampleData()
      .then((s) => setCount(s.sampleBills))
      .catch(() => setCount(0)); // the rest of the page still works without it
  }, []);

  useEffect(load, [load]);

  async function add() {
    setBusy('adding');
    try {
      const s = await addSampleData();
      setCount(s.sampleBills);
      toast(`Added ${s.sampleBills} sample bills. Look around, then remove them any time.`);
      onChanged?.();
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setBusy(null);
    }
  }

  async function remove() {
    const ok = await confirm({
      title: 'Remove the sample data?',
      message: `This deletes the ${count ?? ''} sample bills. Bills you added yourself stay.`,
      confirmLabel: 'Remove sample data',
      danger: true,
    });
    if (!ok) return;
    setBusy('removing');
    try {
      await removeSampleData();
      setCount(0);
      toast('Sample data removed.');
      onChanged?.();
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setBusy(null);
    }
  }

  return { count, busy, add, remove };
}
