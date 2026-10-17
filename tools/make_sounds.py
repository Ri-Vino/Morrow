"""Makes Morrow's alarm sounds and the timers' Bell from scratch, so the app ships no recorded audio.

Most alarm sounds are 16-second loops, the classic ones a second or two, and Bell a 4-second one; each wraps seamlessly.
Run from the repo root:

    python tools/make_sounds.py

Needs numpy, scipy and soundfile. Writes Ogg Vorbis files to androidApp/src/main/res/raw/, and CAF files to
iosApp/iosApp/Sounds/ because iOS can't play Ogg and AlarmKit only takes CAF, WAV or AIFF under 30 seconds.
"""

from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import lfilter, resample_poly

RATE = 44_100
LOOP = 16.0
TAIL = 6.0
OUT = Path(__file__).resolve().parent.parent / "androidApp" / "src" / "main" / "res" / "raw"
IOS_OUT = Path(__file__).resolve().parent.parent / "iosApp" / "iosApp" / "Sounds"


def timeline(loop=LOOP, tail=TAIL):
    return np.zeros(int(RATE * (loop + tail)))


def add(buffer, start, sound):
    i = max(0, int(start * RATE))
    end = min(len(buffer), i + len(sound))
    buffer[i:end] += sound[: end - i]


def wrap(buffer, loop=LOOP):
    """Folds everything that rings past the loop's end back onto its start, a loop's length at a time."""
    n = int(RATE * loop)
    looped = buffer[:n].copy()
    for start in range(n, len(buffer), n):
        tail = buffer[start : start + n]
        looped[: len(tail)] += tail
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


def music_box_note(freq, velocity):
    """A music box tine: a bright pluck with a faint, slightly sharp overtone, ringing out over a second or so."""
    length = 2.5
    n = int(length * RATE)
    t = np.arange(n) / RATE
    tone = np.sin(2 * np.pi * freq * t) * np.exp(-t / 0.9)
    tone += 0.35 * np.sin(2 * np.pi * freq * 3.02 * t) * np.exp(-t / 0.25)
    tone += 0.12 * np.sin(2 * np.pi * freq * 5.4 * t) * np.exp(-t / 0.08)
    return velocity * np.minimum(1, t / 0.001) * tone


def music_box(rng):
    out = timeline()
    midi = lambda m: 440 * 2 ** ((m - 69) / 12)
    # A lullaby-like phrase in C major, high up, then its answer.
    phrase = [72, 76, 79, 84, 83, 79, 76, 77, 74, 79, 77, 76, 74, 72, 74, 76]
    answer = [76, 79, 84, 86, 84, 81, 79, 77, 76, 77, 79, 76, 74, 71, 72, None]
    step = LOOP / 32
    for i, note in enumerate(phrase + answer):
        if note is None:
            continue
        add(out, i * step + rng.uniform(-0.006, 0.006), 0.3 * music_box_note(midi(note), rng.uniform(0.6, 0.9)))
        if i % 4 == 0:
            add(out, i * step, 0.18 * music_box_note(midi(note - 24), 0.6))
    return reverb(out, mix=0.3, size=1.0)


def bowl(freq, velocity):
    """A struck singing bowl: a few inharmonic partials, each a pair a hair apart, so they beat slowly as they fade."""
    length = 9.0
    n = int(length * RATE)
    t = np.arange(n) / RATE
    tone = np.zeros(n)
    for ratio, level, decay, beat in [(1.0, 1.0, 5.5, 0.6), (2.71, 0.45, 3.2, 1.3), (5.15, 0.18, 1.6, 2.1), (8.4, 0.06, 0.7, 2.9)]:
        f = freq * ratio
        tone += level * 0.5 * (np.sin(2 * np.pi * f * t) + np.sin(2 * np.pi * (f + beat) * t)) * np.exp(-t / decay)
    return velocity * np.minimum(1, t / 0.004) * tone


def singing_bowl(rng):
    out = timeline(tail=10.0)
    # Two bowls a fifth apart taking turns, then a third, lower one.
    for i, (start, freq) in enumerate([(0.1, 220.0), (4.1, 329.6), (8.1, 220.0), (12.1, 293.7)]):
        add(out, start, 0.5 * bowl(freq, 1.0 if i % 2 == 0 else 0.8))
    return reverb(out, mix=0.2, size=1.4)


def coloured_noise(rng, seconds, alpha):
    """Noise whose power falls as 1/f^alpha: 0 is white, 1 pink, 2 brown. Made in one pass, so it loops seamlessly."""
    n = int(seconds * RATE)
    spectrum = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / RATE)
    f[0] = f[1]
    spectrum /= f ** (alpha / 2)
    noise = np.fft.irfft(spectrum, n)
    return noise / np.max(np.abs(noise))


def lowpass(signal, cutoff):
    """A gentle one-pole low-pass, run over the loop twice so it starts where it ends."""
    a = np.exp(-2 * np.pi * cutoff / RATE)
    doubled = lfilter([1 - a], [1, -a], np.concatenate([signal, signal]))
    return doubled[len(signal):]


def ocean_waves(rng):
    """Waves rolling in and drawing back: a deep wash that swells and falls twice a loop, with spray on each crest."""
    t = np.arange(int(LOOP * RATE)) / RATE
    body = lowpass(coloured_noise(rng, LOOP, 1.6), 900)
    spray = coloured_noise(rng, LOOP, 0.3)
    spray = spray - lowpass(spray, 2500)
    swell = np.sin(np.pi * t / (LOOP / 2)) ** 2
    crest = np.clip(np.sin(np.pi * (t - 0.6) / (LOOP / 2)), 0, 1) ** 6
    return (0.15 + 0.85 * swell**1.4) * body + 0.35 * crest * spray


def gentle_rain(rng):
    """Steady rain on leaves: a soft hiss under a patter of drops, each a tiny falling ping."""
    out = 0.12 * lowpass(coloured_noise(rng, LOOP + 1.0, 0.6), 5000)
    n = int(0.03 * RATE)
    t = np.arange(n) / RATE
    for _ in range(int(LOOP * 38)):
        f = rng.uniform(1800, 5200)
        drop = np.sin(2 * np.pi * f * t * (1 - 3 * t)) * np.exp(-t / 0.006)
        add(out, rng.uniform(0, LOOP), rng.uniform(0.05, 0.35) * drop)
    return reverb(out, mix=0.15, size=0.6)


def marimba_note(freq, velocity):
    """A marimba bar: a warm fundamental and the bar's bright fourth partial, both dying away quickly."""
    length = 1.6
    n = int(length * RATE)
    t = np.arange(n) / RATE
    tone = np.sin(2 * np.pi * freq * t) * np.exp(-t / 0.45)
    tone += 0.3 * np.sin(2 * np.pi * freq * 3.93 * t) * np.exp(-t / 0.07)
    tone += 0.08 * np.sin(2 * np.pi * freq * 9.2 * t) * np.exp(-t / 0.02)
    return velocity * np.minimum(1, t / 0.0015) * tone


def marimba(rng):
    out = timeline()
    midi = lambda m: 440 * 2 ** ((m - 69) / 12)
    beat = 0.25  # 120 bpm in eighths
    # A bouncy pattern over F, Dm, Bb and C, two bars each.
    chords = [[65, 69, 72], [62, 65, 69], [58, 62, 65], [60, 64, 67]]
    pattern = [0, 1, 2, 1, 2, 3, 2, 1]
    for bar in range(8):
        chord = chords[bar // 2]
        notes = chord + [chord[0] + 12]
        for i, k in enumerate(pattern):
            accent = 1.0 if i % 4 == 0 else 0.7
            add(out, bar * 2.0 + i * beat, 0.32 * marimba_note(midi(notes[k]), accent * rng.uniform(0.85, 1.0)))
        add(out, bar * 2.0, 0.28 * marimba_note(midi(chord[0] - 12), 0.9))
    return reverb(out, mix=0.18, size=0.9)


def pluck(freq, velocity, length=0.9):
    """A bright synth pluck: a handful of harmonics whose top end closes quickly, like a filter sweeping shut."""
    n = int(length * RATE)
    t = np.arange(n) / RATE
    tone = np.zeros(n)
    for k in range(1, 9):
        if k * freq > RATE / 2.2:
            break
        tone += np.sin(2 * np.pi * k * freq * t) / k * np.exp(-t * (2 + 6 * k))
    return velocity * np.minimum(1, t / 0.002) * tone


def sunrise_arpeggio(rng):
    out = timeline()
    midi = lambda m: 440 * 2 ** ((m - 69) / 12)
    chords = [[60, 64, 67, 71], [57, 60, 64, 67], [53, 57, 60, 64], [55, 59, 62, 67]]  # Cmaj7, Am7, Fmaj7, G
    step = 0.125
    for bar in range(8):
        chord = chords[bar % 4]
        up = chord + [n + 12 for n in chord]
        for i in range(16):
            # Straight up and down the chord, then skipping through it the second time round.
            note = up[i % 8] if bar < 4 else up[(i * 3) % 8]
            add(out, bar * 2.0 + i * step, 0.22 * pluck(midi(note), rng.uniform(0.7, 1.0)))
        add(out, bar * 2.0, 0.3 * pluck(midi(chord[0] - 12), 1.0, length=1.8))
    return reverb(out, mix=0.25, size=1.0)


def alarm_bell():
    """A wind-up alarm clock: the hammer rattling between two bells for a second and a half, then a breath."""
    loop = 2.0
    out = timeline(loop, tail=1.0)
    t = 0.0
    k = 0
    while t < 1.45:
        add(out, t, 0.25 * bell_strike(2093.0 if k % 2 == 0 else 2349.3, 0.9))
        t += 1 / 18
        k += 1
    return reverb(out, mix=0.1, size=0.5)


def digital_beeps():
    """The classic digital alarm: four quick beeps, then a pause."""
    loop = 1.0
    out = timeline(loop, tail=0.1)
    n = int(0.08 * RATE)
    t = np.arange(n) / RATE
    edge = np.minimum(1, np.minimum(t, t[::-1]) / 0.004)
    beep = edge * (np.sin(2 * np.pi * 2800 * t) + 0.2 * np.sin(2 * np.pi * 8400 * t))
    for i in range(4):
        add(out, 0.02 + i * 0.13, 0.5 * beep)
    return out


BELL_LOOP = 4.0


def bell_strike(freq, velocity):
    """A small handbell: a low hum, the struck note and a few bright partials, the higher ones fading first."""
    length = 4.0
    n = int(length * RATE)
    t = np.arange(n) / RATE
    tone = np.zeros(n)
    for ratio, level, decay in [(0.5, 0.2, 1.8), (1.0, 1.0, 1.5), (2.0, 0.35, 0.8), (2.4, 0.15, 0.6), (3.0, 0.12, 0.4), (4.2, 0.05, 0.25)]:
        for detune in (-0.0008, 0.0008):
            tone += 0.5 * level * np.sin(2 * np.pi * freq * ratio * (1 + detune) * t) * np.exp(-t / decay)
    return velocity * np.minimum(1, t / 0.004) * tone


def bell():
    """Two soft strikes, a falling third, then a rest while they ring out; it says "time's up" without startling."""
    out = timeline(BELL_LOOP, tail=BELL_LOOP)
    add(out, 0.05, 0.5 * bell_strike(1318.5, 1.0))  # E6
    add(out, 0.55, 0.5 * bell_strike(1046.5, 0.85))  # C6
    return reverb(out, mix=0.3, size=1.1)


def finish(name, signal, loop=LOOP, peak=0.89):
    looped = wrap(signal, loop)
    looped -= looped.mean()
    # About -1 dBFS, so the app's volume ramp has the full range; a harsh sound peaks lower to sit as loud as the rest.
    looped *= peak / np.max(np.abs(looped))
    OUT.mkdir(parents=True, exist_ok=True)
    sf.write(OUT / f"{name}.ogg", looped.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print(f"{name}.ogg: {loop:.0f} s, {(OUT / f'{name}.ogg').stat().st_size // 1024} KB")
    # Uncompressed, so half the sample rate keeps the files small; these sounds have almost nothing above 11 kHz.
    IOS_OUT.mkdir(parents=True, exist_ok=True)
    sf.write(IOS_OUT / f"{name}.caf", resample_poly(looped, 1, 2).astype(np.float32), RATE // 2, format="CAF", subtype="PCM_16")
    print(f"{name}.caf: {loop:.0f} s, {(IOS_OUT / f'{name}.caf').stat().st_size // 1024} KB")


if __name__ == "__main__":
    finish("birdsong", birdsong(np.random.default_rng(7)))
    finish("wind_chimes", wind_chimes(np.random.default_rng(11)))
    finish("soft_piano", soft_piano(np.random.default_rng(3)))
    finish("bell", bell(), BELL_LOOP)
    finish("music_box", music_box(np.random.default_rng(5)))
    finish("singing_bowl", singing_bowl(np.random.default_rng(13)))
    finish("ocean_waves", ocean_waves(np.random.default_rng(17)))
    finish("gentle_rain", gentle_rain(np.random.default_rng(19)))
    finish("marimba", marimba(np.random.default_rng(23)))
    finish("sunrise_arpeggio", sunrise_arpeggio(np.random.default_rng(29)))
    finish("alarm_bell", alarm_bell(), 2.0)
    finish("digital_beeps", digital_beeps(), 1.0, peak=0.45)
