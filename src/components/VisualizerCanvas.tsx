import { useEffect, useRef } from 'react';
import { AudioBands, AudioBandsAnalyzer } from '../engine/AudioBands';

interface VisualizerCanvasProps {
  isPlaying: boolean;
  hasTrack: boolean;
  analyserNode: AnalyserNode | null;
  sampleRate: number;
  onBandsUpdate?: (bands: AudioBands) => void;
}

const BAR_COUNT = 72;
const BAR_GAP = 3;
const LIME_RGB = '198, 243, 106';

function idleWaveValue(index: number): number {
  const raw =
    0.12 +
    0.78 *
      Math.abs(Math.sin(index * 0.19)) *
      (0.55 + 0.45 * Math.cos(index * 0.071));
  return Math.max(0.08, Math.min(0.95, raw));
}

export function VisualizerCanvas({
  isPlaying,
  hasTrack,
  analyserNode,
  sampleRate,
  onBandsUpdate,
}: VisualizerCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const smoothedBarsRef = useRef<Float32Array>(
    Float32Array.from({ length: BAR_COUNT }, (_, i) => idleWaveValue(i))
  );
  const analyzerRef = useRef<AudioBandsAnalyzer | null>(null);
  const timeDataRef = useRef<Float32Array<ArrayBuffer>>(
    new Float32Array(new ArrayBuffer(2048 * 4))
  );
  const lastBandReportRef = useRef<number>(0);

  useEffect(() => {
    analyzerRef.current = new AudioBandsAnalyzer(sampleRate || 44100, 2048);
  }, [sampleRate]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let width = container.clientWidth;
    let height = container.clientHeight;

    const syncSize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      width = container.clientWidth;
      height = container.clientHeight;
      canvas.width = Math.max(1, Math.floor(width * dpr));
      canvas.height = Math.max(1, Math.floor(height * dpr));
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };

    syncSize();
    const resizeObserver = new ResizeObserver(syncSize);
    resizeObserver.observe(container);

    let rafId = 0;

    const drawFrame = (timestamp: number) => {
      const bars = smoothedBarsRef.current;

      if (isPlaying && analyserNode && analyzerRef.current) {
        if (timeDataRef.current.length !== analyserNode.fftSize) {
          timeDataRef.current = new Float32Array(
            new ArrayBuffer(analyserNode.fftSize * 4)
          );
        }
        analyserNode.getFloatTimeDomainData(timeDataRef.current);
        const { bands, spectrum } = analyzerRef.current.analyzeWithSpectrum(
          timeDataRef.current
        );

        const minBin = 1;
        const maxBin = Math.min(
          spectrum.length - 1,
          Math.floor((16000 * analyserNode.fftSize) / (sampleRate || 44100))
        );

        for (let i = 0; i < BAR_COUNT; i++) {
          const t0 = i / BAR_COUNT;
          const t1 = (i + 1) / BAR_COUNT;
          const startBin = Math.floor(
            minBin * Math.pow(maxBin / minBin, t0)
          );
          const endBin = Math.max(
            startBin,
            Math.floor(minBin * Math.pow(maxBin / minBin, t1))
          );

          let peak = 0;
          for (let b = startBin; b <= endBin && b < spectrum.length; b++) {
            if (spectrum[b] > peak) peak = spectrum[b];
          }

          const target = Math.max(0.08, Math.min(0.95, 0.08 + peak * 1.35));
          const smoothing = target > bars[i] ? 0.45 : 0.18;
          bars[i] = bars[i] + (target - bars[i]) * smoothing;
        }

        if (onBandsUpdate && timestamp - lastBandReportRef.current > 60) {
          lastBandReportRef.current = timestamp;
          onBandsUpdate(bands);
        }
      } else {
        for (let i = 0; i < BAR_COUNT; i++) {
          const target = idleWaveValue(i);
          bars[i] = bars[i] + (target - bars[i]) * 0.14;
        }
        if (onBandsUpdate && timestamp - lastBandReportRef.current > 120) {
          lastBandReportRef.current = timestamp;
          onBandsUpdate({ bass: 0, mid: 0, treble: 0 });
        }
      }

      ctx.fillStyle = '#000000';
      ctx.fillRect(0, 0, width, height);

      const barWidth = Math.max(1, (width - BAR_GAP * (BAR_COUNT - 1)) / BAR_COUNT);

      for (let i = 0; i < BAR_COUNT; i++) {
        const wave = bars[i];
        const barHeight = height * wave;
        const x = i * (barWidth + BAR_GAP);
        const y = (height - barHeight) / 2;
        const alpha = 0.45 + wave * 0.55;

        ctx.fillStyle = `rgba(${LIME_RGB}, ${alpha.toFixed(3)})`;
        ctx.beginPath();
        ctx.roundRect(x, y, barWidth, barHeight, 1);
        ctx.fill();
      }

      if (isPlaying || hasTrack) {
        rafId = window.requestAnimationFrame(drawFrame);
      } else {
        let settled = true;
        for (let i = 0; i < BAR_COUNT; i++) {
          if (Math.abs(bars[i] - idleWaveValue(i)) > 0.005) {
            settled = false;
            break;
          }
        }
        if (!settled) {
          rafId = window.requestAnimationFrame(drawFrame);
        }
      }
    };

    rafId = window.requestAnimationFrame(drawFrame);

    return () => {
      window.cancelAnimationFrame(rafId);
      resizeObserver.disconnect();
    };
  }, [isPlaying, hasTrack, analyserNode, sampleRate, onBandsUpdate]);

  return (
    <div
      ref={containerRef}
      className="relative w-full h-[230px] sm:h-[270px] bg-black overflow-hidden"
      aria-label="72-bar audio frequency visualizer"
    >
      <canvas ref={canvasRef} className="block w-full h-full" />
    </div>
  );
}
