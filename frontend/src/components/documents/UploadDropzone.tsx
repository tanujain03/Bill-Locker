import { Camera, CloudUpload, Upload } from 'lucide-react';
import { useRef, useState, type DragEvent } from 'react';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/cn';
import { config } from '@/lib/config';
import { FILE_ACCEPT } from '@/utils/file';

interface UploadDropzoneProps {
  onFiles: (files: File[]) => void;
  multiple?: boolean;
  disabled?: boolean;
  compact?: boolean;
}

/** Drag & drop area with "Browse files" and, on phones, a direct camera capture. */
export function UploadDropzone({ onFiles, multiple = true, disabled = false, compact = false }: UploadDropzoneProps) {
  const [dragging, setDragging] = useState(false);
  const dragDepth = useRef(0);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);

  function emit(list: FileList | null) {
    if (disabled || !list || list.length === 0) return;
    const files = Array.from(list);
    onFiles(multiple ? files : files.slice(0, 1));
  }

  function onDragEnter(event: DragEvent) {
    event.preventDefault();
    dragDepth.current += 1;
    setDragging(true);
  }

  function onDragLeave(event: DragEvent) {
    event.preventDefault();
    dragDepth.current -= 1;
    if (dragDepth.current <= 0) setDragging(false);
  }

  function onDrop(event: DragEvent) {
    event.preventDefault();
    dragDepth.current = 0;
    setDragging(false);
    emit(event.dataTransfer.files);
  }

  return (
    <div
      onDragEnter={onDragEnter}
      onDragOver={(event) => event.preventDefault()}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
      className={cn(
        'flex flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 text-center transition-colors',
        compact ? 'py-7' : 'py-10 sm:py-12',
        dragging ? 'border-brand-500 bg-brand-50/70' : 'border-slate-300 bg-slate-50/70 hover:border-slate-400',
        disabled && 'pointer-events-none opacity-60',
      )}
    >
      <div className="mb-3 flex size-12 items-center justify-center rounded-2xl bg-white text-brand-600 shadow-card">
        <CloudUpload className="size-6" aria-hidden />
      </div>
      <p className="text-sm font-semibold text-slate-900">
        {dragging ? 'Drop to upload' : (
          <>
            <span className="hidden sm:inline">Drag & drop your bills here</span>
            <span className="sm:hidden">Add a bill, invoice or warranty card</span>
          </>
        )}
      </p>
      <p className="mt-1 text-xs text-slate-500">PDF, JPG, PNG or WEBP · up to {config.maxUploadMb} MB each</p>

      <div className="mt-4 flex flex-wrap justify-center gap-2">
        <Button
          size="sm"
          onClick={() => fileInputRef.current?.click()}
          leftIcon={<Upload className="size-4" aria-hidden />}
          disabled={disabled}
        >
          Browse files
        </Button>
        <Button
          size="sm"
          variant="secondary"
          className="sm:hidden"
          onClick={() => cameraInputRef.current?.click()}
          leftIcon={<Camera className="size-4" aria-hidden />}
          disabled={disabled}
        >
          Take photo
        </Button>
      </div>

      <input
        ref={fileInputRef}
        data-testid="file-input"
        type="file"
        className="sr-only"
        tabIndex={-1}
        aria-hidden
        accept={FILE_ACCEPT}
        multiple={multiple}
        onChange={(event) => {
          emit(event.target.files);
          event.target.value = '';
        }}
      />
      <input
        ref={cameraInputRef}
        type="file"
        className="sr-only"
        tabIndex={-1}
        aria-hidden
        accept="image/*"
        capture="environment"
        onChange={(event) => {
          emit(event.target.files);
          event.target.value = '';
        }}
      />
    </div>
  );
}
