import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { UploadDialog } from './UploadDialog';
import { UploadContext, type OpenUploadOptions } from './upload-context';

/** Makes the upload dialog available anywhere inside the app shell via `useUpload()`. */
export function UploadProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ open: boolean; session: number; options: OpenUploadOptions }>({
    open: false,
    session: 0,
    options: {},
  });

  const openUpload = useCallback((options: OpenUploadOptions = {}) => {
    setState((current) => ({ open: true, session: current.session + 1, options }));
  }, []);

  const close = useCallback(() => setState((current) => ({ ...current, open: false })), []);

  const value = useMemo(() => ({ openUpload }), [openUpload]);

  return (
    <UploadContext.Provider value={value}>
      {children}
      {/* A new key per opening gives every upload session a clean slate. */}
      <UploadDialog key={state.session} open={state.open} options={state.options} onClose={close} />
    </UploadContext.Provider>
  );
}
