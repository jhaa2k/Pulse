import { FFTProcessor } from './FFTProcessor';

export interface AudioBands {
  bass: number;
  mid: number;
  treble: number;
}

export interface AudioAnalysisResult {
  bands: AudioBands;
  spectrum: Float32Array;
}

/**
 * Computes Bass (20–250 Hz), Mid (250–4000 Hz), and Treble (4000–16000 Hz) band energies.
 * Direct TypeScript port of com.vooc.visualizer.engine.AudioBandsAnalyzer.
 */
export class AudioBandsAnalyzer {
  private readonly sampleRate: number;
  private readonly fftSize: number;
  private readonly fft: FFTProcessor;

  constructor(sampleRate: number, fftSize = 2048) {
    this.sampleRate = sampleRate;
    this.fftSize = fftSize;
    this.fft = new FFTProcessor(fftSize);
  }

  public analyze(samples: Float32Array): AudioBands {
    const spectrum = this.fft.magnitudes(samples);
    return this.computeBands(spectrum);
  }

  public analyzeWithSpectrum(samples: Float32Array): AudioAnalysisResult {
    const spectrum = this.fft.magnitudes(samples);
    return {
      bands: this.computeBands(spectrum),
      spectrum,
    };
  }

  private computeBands(spectrum: Float32Array): AudioBands {
    const average = (lowHz: number, highHz: number): number => {
      const first = Math.max(1, Math.floor((lowHz * this.fftSize) / this.sampleRate));
      const last = Math.min(spectrum.length - 1, Math.floor((highHz * this.fftSize) / this.sampleRate));
      if (last < first) return 0;
      let sum = 0;
      for (let i = first; i <= last; i++) {
        sum += spectrum[i];
      }
      const avg = sum / (last - first + 1);
      return Math.max(0, Math.min(1, avg));
    };

    return {
      bass: average(20.0, 250.0),
      mid: average(250.0, 4000.0),
      treble: average(4000.0, 16000.0),
    };
  }
}
