import { ChangeEvent, DragEvent, useCallback, useEffect, useRef, useState } from 'react';
import { FolderOpen, Pause, Play, Plus } from 'lucide-react';
import { VisualizerCanvas } from './components/VisualizerCanvas';
import { AudioBands } from './engine/AudioBands';

type EditorTab = 'LAYERS' | 'EFFECTS' | 'AUDIO';

function formatTime(ms: number): string {
  const seconds = Math.max(0, Math.floor(ms / 1000));
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

export function App() {
  const [trackName, setTrackName] = useState<string>('No track selected');
  const [trackUrl, setTrackUrl] = useState<string | null>(null);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [durationMs, setDurationMs] = useState<number>(0);
  const [positionMs, setPositionMs] = useState<number>(0);
  const [isSeeking, setIsSeeking] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<EditorTab>('LAYERS');
  const [bands, setBands] = useState<AudioBands>({ bass: 0, mid: 0, treble: 0 });
  const [sampleRate, setSampleRate] = useState<number>(44100);
  const [isDragActive, setIsDragActive] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const sourceNodeRef = useRef<MediaElementAudioSourceNode | null>(null);
  const objectUrlRef = useRef<string | null>(null);

  const ensureAudioGraph = useCallback(() => {
    const audioEl = audioRef.current;
    if (!audioEl) return;

    if (!audioContextRef.current) {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const ctx = new AudioCtx();
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 2048;
      analyser.smoothingTimeConstant = 0;

      const source = ctx.createMediaElementSource(audioEl);
      source.connect(analyser);
      analyser.connect(ctx.destination);

      audioContextRef.current = ctx;
      analyserRef.current = analyser;
      sourceNodeRef.current = source;
      setSampleRate(ctx.sampleRate);
    }

    if (audioContextRef.current.state === 'suspended') {
      void audioContextRef.current.resume();
    }
  }, []);

  const loadAudioFile = useCallback((file: File) => {
    if (!file.type.startsWith('audio/') && !/\.(mp3|wav|ogg|m4a|flac|aac|opus|webm)$/i.test(file.name)) {
      setErrorMessage('Please select a supported audio file.');
      return;
    }

    setErrorMessage(null);

    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current);
    }

    const nextUrl = URL.createObjectURL(file);
    objectUrlRef.current = nextUrl;

    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current.src = nextUrl;
      audioRef.current.load();
    }

    setTrackUrl(nextUrl);
    setTrackName(file.name);
    setIsPlaying(false);
    setPositionMs(0);
    setDurationMs(0);
  }, []);

  const handleOpenPicker = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      loadAudioFile(file);
    }
    event.target.value = '';
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragActive(false);
    const file = event.dataTransfer.files?.[0];
    if (file) {
      loadAudioFile(file);
    }
  };

  const handleTogglePlay = async () => {
    const audioEl = audioRef.current;
    if (!audioEl || !trackUrl) {
      handleOpenPicker();
      return;
    }

    ensureAudioGraph();

    if (audioEl.paused) {
      try {
        await audioEl.play();
      } catch {
        setErrorMessage('Playback could not start for this audio file.');
      }
    } else {
      audioEl.pause();
    }
  };

  const handleSeekChange = (event: ChangeEvent<HTMLInputElement>) => {
    const nextPos = Number(event.target.value);
    setIsSeeking(true);
    setPositionMs(nextPos);
  };

  const handleSeekCommit = () => {
    if (audioRef.current) {
      audioRef.current.currentTime = positionMs / 1000;
    }
    setIsSeeking(false);
  };

  useEffect(() => {
    return () => {
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
      }
      if (sourceNodeRef.current) {
        sourceNodeRef.current.disconnect();
      }
      if (audioContextRef.current && audioContextRef.current.state !== 'closed') {
        void audioContextRef.current.close();
      }
    };
  }, []);

  const progressPercent = durationMs > 0 ? Math.min(100, (positionMs / durationMs) * 100) : 0;

  return (
    <div
      className="min-h-screen bg-[#10120E] text-[#E8EBDD] flex flex-col"
      onDragOver={(e) => {
        e.preventDefault();
        setIsDragActive(true);
      }}
      onDragLeave={() => setIsDragActive(false)}
      onDrop={handleDrop}
    >
      <input
        ref={fileInputRef}
        type="file"
        accept="audio/*"
        onChange={handleFileChange}
        className="hidden"
        aria-label="Import audio file"
      />

      <audio
        ref={audioRef}
        onLoadedMetadata={(e) => {
          const d = e.currentTarget.duration;
          setDurationMs(Number.isFinite(d) ? Math.max(0, d * 1000) : 0);
        }}
        onTimeUpdate={(e) => {
          if (!isSeeking) {
            setPositionMs(Math.max(0, e.currentTarget.currentTime * 1000));
          }
        }}
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
        onEnded={() => {
          setIsPlaying(false);
          setPositionMs(0);
        }}
        onError={() => {
          setIsPlaying(false);
          setErrorMessage('Unable to decode this audio file in the browser.');
        }}
      />

      {/* Top Bar */}
      <header className="w-full max-w-4xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-8">
        <span className="font-display text-lg font-medium tracking-[0.25em] text-[#E8EBDD] whitespace-nowrap shrink-0">
          VOOC
        </span>
        <button
          type="button"
          onClick={handleOpenPicker}
          className="inline-flex items-center gap-2 px-3 py-2 text-xs font-medium tracking-wider text-[#C6F36A] hover:bg-[#1A1D17] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[#C6F36A] transition-colors whitespace-nowrap shrink-0 cursor-pointer"
        >
          <FolderOpen className="w-4 h-4" aria-hidden="true" />
          <span>IMPORT</span>
        </button>
      </header>

      {/* Main Studio Content */}
      <main className="w-full max-w-4xl mx-auto px-4 sm:px-6 pb-24 flex-1 flex flex-col gap-3.5">
        {/* Studio Headline */}
        <section className="pt-2">
          <p className="text-xs font-medium tracking-[0.12em] text-[#C6F36A]">
            AUDIO VISUALIZER STUDIO
          </p>
          <h1 className="font-display text-[28px] sm:text-[32px] font-normal leading-tight text-[#E8EBDD] mt-0.5 text-balance">
            Create to the rhythm.
          </h1>
        </section>

        {/* Visualizer Canvas */}
        <section
          className={`relative transition-colors ${
            isDragActive ? 'ring-1 ring-[#C6F36A]' : ''
          }`}
        >
          <VisualizerCanvas
            isPlaying={isPlaying}
            hasTrack={trackUrl !== null}
            analyserNode={analyserRef.current}
            sampleRate={sampleRate}
            onBandsUpdate={setBands}
          />
          {isDragActive && (
            <div className="absolute inset-0 bg-[#10120E]/80 flex items-center justify-center pointer-events-none">
              <span className="text-sm font-medium tracking-wider text-[#C6F36A]">
                DROP AUDIO FILE TO LOAD
              </span>
            </div>
          )}
        </section>

        {/* Error banner if an unsupported file is dropped */}
        {errorMessage && (
          <div
            role="alert"
            className="bg-[#1A1D17] border border-[#DC2626]/60 px-4 py-2.5 text-xs text-[#FCA5A5] flex items-center justify-between gap-4"
          >
            <span>{errorMessage}</span>
            <button
              type="button"
              onClick={() => setErrorMessage(null)}
              className="text-[#E8EBDD] hover:text-[#C6F36A] underline cursor-pointer shrink-0"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* NOW PLAYING Panel */}
        <section className="bg-[#1A1D17] p-4 flex flex-col gap-2">
          <span className="text-[11px] font-medium tracking-[0.12em] text-[#C6F36A]">
            NOW PLAYING
          </span>
          <p className="text-base font-medium text-[#E8EBDD] truncate" title={trackName}>
            {trackName}
          </p>

          <div className="py-1.5">
            <input
              type="range"
              min={0}
              max={Math.max(1, durationMs)}
              value={Math.min(positionMs, Math.max(1, durationMs))}
              disabled={!trackUrl}
              onChange={handleSeekChange}
              onMouseUp={handleSeekCommit}
              onTouchEnd={handleSeekCommit}
              onKeyUp={handleSeekCommit}
              aria-label="Track seek position"
              className="vooc-slider w-full"
              style={{
                background: `linear-gradient(to right, #C6F36A ${progressPercent}%, #2F342A ${progressPercent}%)`,
              }}
            />
          </div>

          <div className="flex items-center justify-between w-full">
            <span className="font-mono-tabular text-[11px] text-[#D4D6CE]">
              {formatTime(positionMs)}
            </span>

            <button
              type="button"
              onClick={() => void handleTogglePlay()}
              aria-label={isPlaying ? 'Pause' : 'Play'}
              className="w-10 h-10 flex items-center justify-center text-[#C6F36A] hover:bg-[#242820] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[#C6F36A] transition-colors cursor-pointer"
            >
              {isPlaying ? (
                <Pause className="w-5 h-5 fill-current" aria-hidden="true" />
              ) : (
                <Play className="w-5 h-5 fill-current" aria-hidden="true" />
              )}
            </button>

            <span className="font-mono-tabular text-[11px] text-[#D4D6CE]">
              {formatTime(durationMs)}
            </span>
          </div>
        </section>

        {/* EDITOR Section */}
        <section className="flex flex-col gap-2.5 pt-1">
          <span className="text-xs font-medium tracking-[0.12em] text-[#C6F36A]">
            EDITOR
          </span>

          <div className="flex flex-wrap items-center gap-2" role="tablist" aria-label="Editor modules">
            {(['LAYERS', 'EFFECTS', 'AUDIO'] as const).map((tab) => {
              const selected = activeTab === tab;
              return (
                <button
                  key={tab}
                  type="button"
                  role="tab"
                  aria-selected={selected}
                  onClick={() => setActiveTab(tab)}
                  className={`px-3.5 py-2 text-xs font-medium tracking-wider border transition-colors whitespace-nowrap shrink-0 cursor-pointer focus-visible:outline focus-visible:outline-2 focus-visible:outline-[#C6F36A] ${
                    selected
                      ? 'bg-[#C6F36A] text-[#10120E] border-[#C6F36A]'
                      : 'bg-transparent text-[#E8EBDD] border-[#3B4035] hover:border-[#9DAA85]'
                  }`}
                >
                  {tab}
                </button>
              );
            })}
          </div>

          {activeTab === 'AUDIO' && (
            <div className="bg-[#1A1D17] p-3.5 grid grid-cols-1 sm:grid-cols-3 gap-3 border border-[#2A2F24]">
              {(
                [
                  { label: 'BASS · 20–250 Hz', value: bands.bass },
                  { label: 'MID · 250–4k Hz', value: bands.mid },
                  { label: 'TREBLE · 4k–16k Hz', value: bands.treble },
                ] as const
              ).map((band) => (
                <div key={band.label} className="flex flex-col gap-1.5">
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-[#9DAA85] tracking-wider">{band.label}</span>
                    <span className="font-mono-tabular text-[#E8EBDD]">
                      {Math.round(band.value * 100)}%
                    </span>
                  </div>
                  <div className="h-1.5 w-full bg-[#10120E] overflow-hidden">
                    <div
                      className="h-full bg-[#C6F36A] transition-[width] duration-75"
                      style={{ width: `${Math.round(band.value * 100)}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}

          <p className="text-xs leading-relaxed text-[#9DA394]">
            Import a track to begin. Visualizer editing and export modules are being added in the next implementation phases.
          </p>
        </section>
      </main>

      {/* Sharp Rectangular Floating Action Button */}
      <button
        type="button"
        onClick={handleOpenPicker}
        aria-label="Add audio"
        className="fixed bottom-6 right-6 w-14 h-14 bg-[#C6F36A] text-[#10120E] flex items-center justify-center shadow-lg hover:bg-[#d3f688] active:scale-95 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#C6F36A] transition-all cursor-pointer"
      >
        <Plus className="w-6 h-6" aria-hidden="true" />
      </button>
    </div>
  );
}

export default App;
