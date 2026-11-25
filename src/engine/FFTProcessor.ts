/**
 * Radix-2 FFT with Hann window and normalized logarithmic magnitude output.
 * Direct TypeScript port of com.vooc.visualizer.engine.FFTProcessor.
 */
export class FFTProcessor {
  public readonly size: number;
  private readonly bitReversedIndices: Uint32Array;
  private readonly hannWindow: Float64Array;
  private readonly real: Float64Array;
  private readonly imag: Float64Array;

  constructor(size = 2048) {
    if (size < 2 || (size & (size - 1)) !== 0) {
      throw new Error('FFT size must be a power of two');
    }
    this.size = size;
    this.bitReversedIndices = new Uint32Array(size);
    this.hannWindow = new Float64Array(size);
    this.real = new Float64Array(size);
    this.imag = new Float64Array(size);

    const bits = Math.round(Math.log2(size));
    for (let i = 0; i < size; i++) {
      let reversed = 0;
      for (let b = 0; b < bits; b++) {
        if ((i & (1 << b)) !== 0) {
          reversed |= 1 << (bits - 1 - b);
        }
      }
      this.bitReversedIndices[i] = reversed;
      this.hannWindow[i] = 0.5 - 0.5 * Math.cos((2.0 * Math.PI * i) / (size - 1));
    }
  }

  public magnitudes(input: Float32Array): Float32Array {
    if (input.length < this.size) {
      throw new Error(`Input must contain at least ${this.size} samples`);
    }

    const { size, real, imag, bitReversedIndices, hannWindow } = this;
    imag.fill(0);

    for (let i = 0; i < size; i++) {
      real[bitReversedIndices[i]] = input[i] * hannWindow[i];
    }

    for (let length = 2; length <= size; length <<= 1) {
      const halfLength = length >> 1;
      const angle = (-2.0 * Math.PI) / length;
      for (let base = 0; base < size; base += length) {
        for (let j = 0; j < halfLength; j++) {
          const wr = Math.cos(angle * j);
          const wi = Math.sin(angle * j);
          const even = base + j;
          const odd = even + halfLength;
          const tr = wr * real[odd] - wi * imag[odd];
          const ti = wr * imag[odd] + wi * real[odd];
          real[odd] = real[even] - tr;
          imag[odd] = imag[even] - ti;
          real[even] += tr;
          imag[even] += ti;
        }
      }
    }

    const halfSize = size >> 1;
    const output = new Float32Array(halfSize);
    for (let i = 0; i < halfSize; i++) {
      const normalized = Math.hypot(real[i], imag[i]) / halfSize;
      output[i] = Math.log(1.0 + normalized * 20.0);
    }
    return output;
  }
}
