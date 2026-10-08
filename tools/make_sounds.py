"""Makes Morrow's three alarm sounds from scratch, so the app ships no recorded audio.

Each is a 16-second loop that wraps seamlessly. Run from the repo root:

    python tools/make_sounds.py

Needs numpy, scipy and soundfile. Writes Ogg Vorbis files to androidApp/src/main/res/raw/.
"""

from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import lfilter

RATE = 44_100
LOOP = 16.0
TAIL = 6.0
OUT = Path(__file__).resolve().parent.parent / "androidApp" / "src" / "main" / "res" / "raw"


def timeline():
    return np.zeros(int(RATE * (LOOP + TAIL)))


def add(buffer, start, sound):
    i = max(0, int(start * RATE))
    end = min(len(buffer), i + len(sound))
    buffer[i:end] += sound[: end - i]


def wrap(buffer):
    """Folds everything that rings past the loop's end back onto its start."""
    n = int(RATE * LOOP)
    looped = buffer[:n].copy()
    looped[: len(buffer) - n] += buffer[n:]
    return looped


def reverb(signal, mix, size=1.0):
    """A small Schroeder reverb: four combs in parallel, then two all-passes."""
    wet = np.zeros_like(signal)
    for delay_ms, feedback in [(29.7, 0.80), (37.1, 0.78), (41.1, 0.76), (43.7, 0.74)]:
        d = int(RATE * delay_ms * size / 1000)
        a = np.zeros(d + 1)
        a[0], a[d] = 1, -feedback
        wet += lfilter([1], a, signal)
    for delay_ms, gain in [(5.0, 0.7), (1.7, 0.7)]:
        d = int(RATE * delay_ms / 1000)
        b = np.zeros(d + 1)
        b[0], b[d] = -gain, 1
        a = np.zeros(d + 1)
        a[0], a[d] = 1, -gain
        wet = lfilter(b, a, wet)
    return (1 - mix) * signal + mix * wet / 4


def envelope(n, attack, decay):
    t = np.arange(n) / RATE
    return np.minimum(1, t / attack) * np.exp(-t / decay)


def chirp(f_start, f_end, length, trill=0.0, trill_depth=0.0):
    """One bird note: a glide with an optional fast trill and a faint second harmonic."""
    n = int(length * RATE)
    t = np.arange(n) / RATE
    glide = f_start * (f_end / f_start) ** (t / length)
    freq = glide * (1 + trill_depth * np.sin(2 * np.pi * trill * t))
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    shape = np.sin(np.pi * np.clip(t / length, 0, 1)) ** 1.5
    return shape * (np.sin(phase) + 0.15 * np.sin(2 * phase))


def birdsong(rng):
    out = timeline()
    # A warbler: quick bright phrases that rise and fall.
    t = 0.4
    while t < LOOP - 1:
        notes = rng.integers(3, 7)
        base = rng.uniform(3200, 4200)
        for k in range(notes):
            up = rng.random() < 0.5
            f0 = base * rng.uniform(0.9, 1.15)
            f1 = f0 * (rng.uniform(1.15, 1.4) if up else rng.uniform(0.7, 0.85))
            length = rng.uniform(0.05, 0.11)
            add(out, t, 0.5 * chirp(f0, f1, length, trill=rng.uniform(30, 55), trill_depth=0.03))
            t += length + rng.uniform(0.03, 0.08)
        t += rng.uniform(1.0, 2.2)
    # A slower, lower bird answering with two-note whistles.
    t = 1.6
    while t < LOOP - 1:
        f = rng.uniform(1900, 2400)
        add(out, t, 0.35 * chirp(f, f * 1.08, 0.28))
        add(out, t + 0.36, 0.3 * chirp(f * 0.84, f * 0.8, 0.34))
        t += rng.uniform(2.6, 3.8)
    return reverb(out, mix=0.25, size=0.8)


def chime(freq, velocity):
    """A struck tube: inharmonic partials, the higher ones dying away faster, with a little shimmer."""
    length = 5.0
    n = int(length * RATE)
    t = np.arange(n) / RATE
    tone = np.zeros(n)
    for ratio, level, decay in [(1.0, 1.0, 1.6), (2.76, 0.5, 0.9), (5.40, 0.25, 0.45), (8.93, 0.12, 0.25)]:
        for detune in (-0.0012, 0.0012):
            tone += 0.5 * level * np.sin(2 * np.pi * freq * ratio * (1 + detune) * t) * np.exp(-t / decay)
    return velocity * np.minimum(1, t / 0.002) * tone


def wind_chimes(rng):
    out = timeline()
    notes = [1046.5, 1174.7, 1318.5, 1568.0, 1760.0, 2093.0]  # C major pentatonic, two octaves up
    t = 0.2
    while t < LOOP - 0.5:
        # A gust knocks several tubes together, then the air settles.
        for _ in range(rng.integers(2, 6)):
            add(out, t, 0.35 * chime(rng.choice(notes), rng.uniform(0.4, 1.0)))
            t += rng.uniform(0.08, 0.35)
        t += rng.uniform(0.6, 1.8)
    return reverb(out, mix=0.3, size=1.2)


def piano_note(freq, velocity, length=4.5):
    """A soft piano note: slightly stretched partials on three gently detuned strings, each fading fast then slowly."""
    n = int(length * RATE)
    t = np.arange(n) / RATE
    tone = np.zeros(n)
    stretch = 0.0004
    for k in range(1, 11):
        partial = k * freq * np.sqrt(1 + stretch * k * k)
        if partial > RATE / 2.2:
            break
        level = velocity ** (0.5 + 0.15 * k) / k**1.6
        decay = 2.8 / (1 + 0.35 * k)
        fade = 0.7 * np.exp(-t / decay) + 0.3 * np.exp(-t / (decay * 0.15))
        for cents in (-0.8, 0.0, 0.8):
            tone += level / 3 * fade * np.sin(2 * np.pi * partial * 2 ** (cents / 1200) * t)
    return np.minimum(1, t / 0.006) * tone


def soft_piano(rng):
    out = timeline()
    midi = lambda m: 440 * 2 ** ((m - 69) / 12)
    chords = [
        [50, 57, 62, 64, 66, 69],  # Dmaj9
        [47, 54, 59, 62, 66, 69],  # Bm7
        [43, 50, 59, 62, 66, 71],  # Gmaj7
        [45, 52, 57, 62, 64, 69],  # Asus
    ]
    beat = 4.0 / 6
    for c, chord in enumerate(chords):
        start = c * 4.0
        add(out, start, 0.5 * piano_note(midi(chord[0] - 12), 0.5, length=5.0))
        for i, note in enumerate(chord):
            humanise = rng.uniform(-0.015, 0.015)
            add(out, start + i * beat + humanise, 0.32 * piano_note(midi(note), rng.uniform(0.35, 0.55)))
    return reverb(out, mix=0.35, size=1.3)


def finish(name, signal):
    looped = wrap(signal)
    looped -= looped.mean()
    looped *= 0.89 / np.max(np.abs(looped))  # about -1 dBFS, so the app's volume ramp has the full range
    OUT.mkdir(parents=True, exist_ok=True)
    sf.write(OUT / f"{name}.ogg", looped.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print(f"{name}.ogg: {LOOP:.0f} s, {(OUT / f'{name}.ogg').stat().st_size // 1024} KB")


if __name__ == "__main__":
    finish("birdsong", birdsong(np.random.default_rng(7)))
    finish("wind_chimes", wind_chimes(np.random.default_rng(11)))
    finish("soft_piano", soft_piano(np.random.default_rng(3)))
