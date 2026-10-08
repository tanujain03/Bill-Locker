import { LoaderCircle, Upload } from 'lucide-react';
import { useEffect, useState, type DragEvent } from 'react';
import { useUploadBill } from '../lib/useUploadBill';

/**
 * Drag a bill from your computer onto any signed-in page to upload it. While a file is
 * dragged over the window, a full-screen layer says "Drop to upload" and catches the drop
 * (so the browser doesn't open the file itself).
 */
export function DropAnywhere() {
  const [dragging, setDragging] = useState(false);
  const { upload, uploading } = useUploadBill();

  useEffect(() => {
    // Only files, not text or links dragged around inside the page.
    const onEnter = (e: globalThis.DragEvent) => {
      if (e.dataTransfer?.types.includes('Files')) setDragging(true);
    };
    window.addEventListener('dragenter', onEnter);
    return () => window.removeEventListener('dragenter', onEnter);
  }, []);

  if (!dragging && !uploading) return null;

  return (
    <div
      onDragOver={(e: DragEvent) => e.preventDefault()} // "a drop is allowed here"
      onDragLeave={(e: DragEvent) => e.target === e.currentTarget && setDragging(false)}
      onDrop={(e: DragEvent) => {
        e.preventDefault();
        setDragging(false);
        upload(e.dataTransfer.files[0]);
      }}
      className="fixed inset-0 z-[70] grid place-items-center bg-brand-950/40 p-6 backdrop-blur-sm"
    >
      {/* pointer-events-none: the drag stays on the layer, so dragleave only fires when leaving it. */}
      <div className="pointer-events-none flex flex-col items-center rounded-2xl border-2 border-dashed border-brand-300 bg-white px-12 py-10 text-center shadow-2xl">
        <span className="grid size-14 place-items-center rounded-2xl bg-brand-50 text-brand-600">
          {uploading ? <LoaderCircle className="size-7 animate-spin" aria-hidden /> : <Upload className="size-7" aria-hidden />}
        </span>
        <p className="mt-4 text-lg font-semibold">{uploading ? 'Uploading…' : 'Drop to upload your bill'}</p>
        <p className="mt-1 text-sm text-slate-500">PDF, JPG, PNG or WebP, up to 10 MB</p>
      </div>
    </div>
  );
}
