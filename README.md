# VOOC — Audio Visualizer Studio

A React + TypeScript + Vite web application rewritten from the original Android Jetpack Compose `com.vooc.visualizer` app.

## Features

- **Audio Import & Playback**: Load local audio files via file picker or drag-and-drop with play/pause, seek scrubbing, and elapsed/total timecodes.
- **72-Bar Symmetric Visualizer Canvas**: Real-time Web Audio API playback connected to a Radix-2 FFT engine (`FFTProcessor`) with Hann windowing and logarithmic magnitude normalization (`ln(1 + mag * 20)`), falling back to the standing waveform when paused or idle.
- **Frequency Band Analysis**: `AudioBandsAnalyzer` computes Bass (20–250 Hz), Mid (250–4000 Hz), and Treble (4000–16000 Hz) band energies, viewable in the `AUDIO` editor module.
- **Editor Modules**: Interactive `LAYERS`, `EFFECTS`, and `AUDIO` selector tabs matching the VOOC studio layout and `#10120E` / `#1A1D17` / `#C6F36A` palette.

## Development

```bash
npm install
npm run dev
```

Runs on `http://0.0.0.0:3000`.

## Build

```bash
npm run build
```
