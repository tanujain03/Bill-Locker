import { CircleAlert, CircleCheck, Copy, LoaderCircle, Upload, X } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError, errorMessage } from '../lib/api';
import { uploadDocument } from '../lib/documents';
import { useFeedback } from '../lib/feedback-context';
import { processUploadBatch, type UploadEntry, type UploadResult } from '../lib/upload-batch';
import { UploadContext } from '../lib/upload-context';
import { Button } from './Button';

/** One queue shared by every upload button and drag-and-drop, retained during page navigation. */
export function UploadProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const { toast } = useFeedback();
  const [jobs, setJobs] = useState<UploadResult[]>([]);
  const [uploading, setUploading] = useState(false);
  const [visible, setVisible] = useState(false);
  const locked = useRef(false);
  const mounted = useRef(true);
  const counter = useRef(0);
  const multiple = useRef(false);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  async function run(entries: UploadEntry[], batch: boolean) {
    if (locked.current || !entries.length) return;
    locked.current = true;
    setUploading(true);
    try {
      const results = await processUploadBatch(entries, {
        upload: (file) => uploadDocument(file, batch),
        duplicateId: (error) => error instanceof ApiError && error.code === 'DUPLICATE_FILE' ? error.documentId : null,
        errorText: errorMessage,
        shouldContinue: () => mounted.current,
        onUpdate: (result) => setJobs((current) => current.map((job) => job.key === result.key ? result : job)),
      });
      if (!mounted.current) return;
      const successful = results.filter((r) => r.documentId);
      const failures = results.filter((r) => r.status === 'failed');
      if (!batch && successful[0]) {
        const result = successful[0];
        navigate(`/documents/${result.documentId}${result.status === 'uploaded' ? '?read=1' : ''}`);
        if (result.status === 'duplicate') toast('You already uploaded this file, so here it is.', 'info');
      } else if (batch && successful.length) {
        navigate(`/documents?uploaded=${Date.now()}`);
      }
      if (failures.length) setVisible(true);
    } finally {
      locked.current = false;
      if (mounted.current) setUploading(false);
    }
  }

  async function upload(input: File | File[] | undefined) {
    if (!input || locked.current) return;
    const files = Array.isArray(input) ? input : [input];
    if (!files.length) return;
    const entries = files.map((file) => ({ key: ++counter.current, file }));
    multiple.current = files.length > 1;
    setJobs(entries.map((entry) => ({ ...entry, status: 'waiting' })));
    setVisible(multiple.current);
    await run(entries, multiple.current);
  }

  async function retryFailed() {
    if (locked.current) return;
    const failed = jobs.filter((job) => job.status === 'failed');
    setJobs((current) => current.map((job) => job.status === 'failed' ? { ...job, status: 'waiting', message: undefined } : job));
    await run(failed, multiple.current);
  }

  const completed = jobs.filter((job) => !['waiting', 'uploading'].includes(job.status)).length;
  const failed = jobs.filter((job) => job.status === 'failed').length;
  const uploaded = jobs.filter((job) => job.status === 'uploaded').length;
  const duplicates = jobs.filter((job) => job.status === 'duplicate').length;

  return (
    <UploadContext.Provider value={{ upload, uploading }}>
      {children}
      {visible && (
        <section aria-labelledby="upload-progress-title" className="fixed right-4 bottom-20 left-4 z-50 rounded-2xl border border-slate-200 bg-white p-4 shadow-xl sm:bottom-4 sm:left-auto sm:w-96">
          <div className="flex items-center justify-between gap-3">
            <h2 id="upload-progress-title" className="font-semibold">{uploading ? `Uploading ${completed} of ${jobs.length}` : 'Upload results'}</h2>
            {!uploading && <Button size="sm" variant="ghost" icon={X} onClick={() => { setVisible(false); setJobs([]); }} aria-label="Dismiss upload results" />}
          </div>
          <progress value={completed} max={jobs.length || 1} aria-label="Files processed" className="mt-3 h-2 w-full accent-brand-600" />
          <p role="status" className="mt-2 text-xs text-slate-600">{uploaded} uploaded · {duplicates} already in locker · {failed} failed</p>
          <ul className="mt-3 max-h-56 space-y-3 overflow-y-auto">
            {jobs.map((job) => {
              const Icon = job.status === 'uploading' ? LoaderCircle : job.status === 'failed' ? CircleAlert : job.status === 'uploaded' ? CircleCheck : job.status === 'duplicate' ? Copy : Upload;
              return (
                <li key={job.key} className="flex items-start gap-2 text-sm">
                  <Icon aria-hidden className={`mt-0.5 size-4 shrink-0 ${job.status === 'uploading' ? 'animate-spin text-brand-600' : job.status === 'failed' ? 'text-rose-600' : 'text-slate-500'}`} />
                  <div className="min-w-0 flex-1">
                    {job.documentId ? <Link to={`/documents/${job.documentId}`} className="block truncate font-medium hover:underline">{job.file.name}</Link> : <p className="truncate font-medium">{job.file.name}</p>}
                    <p className={`text-xs ${job.status === 'failed' ? 'text-rose-700' : 'text-slate-500'}`}>{job.message ?? { waiting: 'Waiting', uploading: 'Uploading...', uploaded: 'Uploaded', duplicate: 'Already in your locker', failed: 'Failed' }[job.status]}</p>
                  </div>
                </li>
              );
            })}
          </ul>
          {!uploading && failed > 0 && <Button className="mt-4" size="sm" onClick={retryFailed}>Retry failed files</Button>}
          {multiple.current && <p className="mt-3 text-xs text-slate-500">New documents are read in the background. Review their details in Documents.</p>}
        </section>
      )}
    </UploadContext.Provider>
  );
}
