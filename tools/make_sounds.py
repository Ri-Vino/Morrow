"""Makes Morrow's alarm sounds and the timers' Bell from scratch, so the app ships no recorded audio.

Most alarm sounds are 16-second loops, the classic bell and beeps a second or two, and Bell a 4-second one; each wraps seamlessly.
Run from the repo root:

    python tools/make_sounds.py                # every sound
    python tools/make_sounds.py kalimba campfire  # just these

Needs numpy, scipy and soundfile. Writes Ogg Vorbis files to androidApp/src/main/res/raw/, and CAF files to
iosApp/iosApp/Sounds/ because iOS can't play Ogg and AlarmKit only takes CAF, WAV or AIFF under 30 seconds.
"""

import sys
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


def kalimba_note(freq, velocity):
    """A kalimba tine: a clean, round fundamental with a short, bright "tink" from its bendy overtone, ringing a second or two."""
    length = 3.0
    n = int(length * RATE)
    t = np.arange(n) / RATE
    tone = np.sin(2 * np.pi * freq * t) * np.exp(-t / 1.1)
    tone += 0.25 * np.sin(2 * np.pi * freq * 5.4 * t) * np.exp(-t / 0.09)
    tone += 0.06 * np.sin(2 * np.pi * freq * 9.6 * t) * np.exp(-t / 0.03)
    # The thumb's tiny knock, low and dull.
    tone += 0.12 * np.sin(2 * np.pi * freq * 0.5 * t) * np.exp(-t / 0.02)
    return velocity * np.minimum(1, t / 0.0015) * tone


def kalimba(rng):
    out = timeline()
    midi = lambda m: 440 * 2 ** ((m - 69) / 12)
    # A bright, turning figure in G major pentatonic over four bars, then the same with a lift at the end; a quick two
    # notes then a held one, as thumbs play it.
    bar = [67, 71, 74, 79, 78, 74, 71, 74]
    lift = [69, 72, 76, 81, 79, 76, 72, 76]
    figure = bar + lift + [67, 71, 74, 79, 83, 79, 74, 71] + [69, 72, 76, 81, 79, 74, 71, 67]
    step = LOOP / 32
    for i, note in enumerate(figure):
        swing = 0.04 * step if i % 2 else 0.0
        add(out, i * step + swing + rng.uniform(-0.01, 0.01), 0.3 * kalimba_note(midi(note), rng.uniform(0.6, 0.95)))
        if i % 8 == 0:
            add(out, i * step, 0.25 * kalimba_note(midi(note - 24), 0.8))
    return reverb(out, mix=0.22, size=0.9)


def choir_pad(rng):
    """A soft choir on a slow chord change: each voice a handful of harmonics shaped to an open "ah", with a little
    vibrato. Every frequency is a whole number of cycles per loop, so the pad joins up exactly."""
    n = int(LOOP * RATE)
    t = np.arange(n) / RATE
    midi = lambda m: 440 * 2 ** ((m - 69) / 12)
    formants = [(800, 90, 1.0), (1150, 110, 0.5), (2900, 200, 0.12)]
    # Four chords of four seconds, each voice swelling in and out over its own stretch of the loop.
    chords = [[50, 57, 62, 66, 69], [47, 54, 59, 62, 66], [43, 50, 55, 59, 62], [45, 52, 57, 61, 64]]
    out = np.zeros(n)
    for c, chord in enumerate(chords):
        centre = (c * 4.0 + 2.0)
        # A raised cosine four seconds each side of the chord's middle, so neighbours overlap and the sound never drops out.
        phase = ((t - centre + LOOP / 2) % LOOP) - LOOP / 2
        swell = np.where(np.abs(phase) < 4.0, 0.5 + 0.5 * np.cos(np.pi * phase / 4.0), 0.0)
        for voice, note in enumerate(chord):
            for detune in (-0.0016, 0.0, 0.0016):
                base = midi(note) * (1 + detune)
                base = round(base * LOOP) / LOOP
                vib_cycles = int(round(LOOP * (4.6 + 0.3 * voice)))
                # A whole number of cycles a loop, so the vibrato adds up to nothing and the pitch comes back to where it began.
                vib = 0.0035 * np.sin(2 * np.pi * vib_cycles * t / LOOP + rng.uniform(0, 6.28))
                ph = np.cumsum(base * (1 + vib)) * 2 * np.pi / RATE + rng.uniform(0, 2 * np.pi)
                tone = np.zeros(n)
                for k in range(1, 24):
                    f = k * base
                    if f > 5000:
                        break
                    gain = sum(g * np.exp(-0.5 * ((f - fc) / bw) ** 2) for fc, bw, g in formants) + 0.02
                    tone += gain / k**0.6 * np.sin(k * ph)
                out += swell * tone / 3 / (1 + 0.15 * voice)
    # This pad has no tail to fold back, so the reverb runs over three loops end to end and keeps the middle one,
    # which starts already ringing from the loop before.
    return reverb(np.tile(out, 3), mix=0.4, size=1.5)[n : 2 * n]


def pan_note(freq, velocity):
    """A steel pan note: a ringing fundamental with its octave and twelfth, each doubled a hair apart so it shimmers."""
    length = 1.8
    n = int(length * RATE)
    t = np.arange(n) / RATE
    tone = np.zeros(n)
    for ratio, level, decay, shimmer in [(1.0, 1.0, 0.75, 3.5), (2.0, 0.7, 0.55, 5.0), (3.0, 0.3, 0.3, 6.5), (4.02, 0.12, 0.12, 0.0)]:
        f = freq * ratio
        tone += level * 0.5 * (np.sin(2 * np.pi * f * t) + np.sin(2 * np.pi * (f + shimmer) * t)) * np.exp(-t / decay)
    # The mallet's soft tap.
    tone += 0.15 * np.sin(2 * np.pi * freq * 0.5 * t) * np.exp(-t / 0.015)
    return velocity * np.minimum(1, t / 0.003) * tone


def steel_pan(rng):
    out = timeline()
    midi = lambda m: 440 * 2 ** ((m - 69) / 12)
    beat = 0.5  # 120 bpm, 32 beats a loop
    # A lilting island tune over C, Am, F and G: eight-note phrases with the offbeat pushed.
    melody = [
        # (beat, midi)
        (0, 76), (0.75, 79), (1.5, 76), (2, 74), (2.75, 72), (3.5, 74),
        (4, 72), (4.75, 76), (5.5, 81), (6, 79), (6.75, 76), (7.5, 72),
        (8, 77), (8.75, 81), (9.5, 77), (10, 74), (10.75, 72), (11.5, 74),
        (12, 74), (12.75, 79), (13.5, 83), (14, 81), (14.5, 79), (15, 74),
    ]
    for bar in range(4):
        for b, m in melody[bar * 6 : bar * 6 + 6]:
            for rep in range(2):
                start = (b + rep * 16) * beat
                add(out, start + rng.uniform(-0.008, 0.008), 0.3 * pan_note(midi(m), rng.uniform(0.75, 1.0)))
    # Rhythm chords: a stab on the offbeats and a low pan on each bar's first beat.
    chords = [[60, 64, 67], [57, 60, 64], [60, 65, 69], [59, 62, 67]]
    for bar in range(8):
        chord = chords[bar // 2]
        base = bar * 4
        add(out, base * beat, 0.25 * pan_note(midi(chord[0] - 12), 0.9))
        for b in (1.5, 3.0):
            for note in chord:
                add(out, (base + b) * beat, 0.1 * pan_note(midi(note), 0.7))
    return reverb(out, mix=0.18, size=0.8)


def rhodes_note(freq, velocity, length=3.0):
    """An electric piano tine: a round fundamental that a bright bark fades out of, with a slow tremolo."""
    n = int(length * RATE)
    t = np.arange(n) / RATE
    index = 1.8 * velocity * np.exp(-t / 0.25) + 0.12
    tone = np.sin(2 * np.pi * freq * t + index * np.sin(2 * np.pi * freq * t))
    tone += 0.12 * np.sin(2 * np.pi * freq * 14 * t) * np.exp(-t / 0.03)
    return velocity * np.minimum(1, t / 0.004) * np.exp(-t / 1.6) * tone


def lofi_keys(rng):
    out = timeline()
    midi = lambda m: 440 * 2 ** ((m - 69) / 12)
    # Cmaj9, Am9, Dm9 and G13 as warm, rolled chords, a bass note under each, and a lazy swing.
    chords = [[60, 64, 67, 71, 74], [57, 60, 64, 67, 71], [62, 65, 69, 72, 76], [55, 59, 65, 69, 76]]
    basses = [36, 33, 38, 31]
    for c, chord in enumerate(chords):
        start = c * 4.0
        for hit in (0.0, 1.5, 3.0):
            vel = 0.8 if hit == 0.0 else 0.55
            for i, note in enumerate(chord):
                add(out, start + hit + i * 0.03 + rng.uniform(-0.01, 0.01), 0.12 * rhodes_note(midi(note), vel * rng.uniform(0.85, 1.0)))
        add(out, start, 0.3 * rhodes_note(midi(basses[c]), 0.8, length=3.5))
        add(out, start + 2.25, 0.2 * rhodes_note(midi(basses[c] + 7), 0.6))
    # A dusty kick and brushed hat in a slow hip-hop pattern.
    kick_n = int(0.25 * RATE)
    kt = np.arange(kick_n) / RATE
    kick = np.sin(2 * np.pi * (48 + 60 * np.exp(-kt / 0.03)) * kt) * np.exp(-kt / 0.09)
    hat_n = int(0.05 * RATE)
    ht = np.arange(hat_n) / RATE
    hat = rng.standard_normal(hat_n) * np.exp(-ht / 0.012)
    hat = hat - lowpass(hat, 5000)
    for bar in range(8):
        base = bar * 2.0
        add(out, base, 0.35 * kick)
        add(out, base + 1.25, 0.2 * kick)
        for h in range(4):
            add(out, base + h * 0.5 + (0.04 if h % 2 else 0.0), (0.05 if h % 2 == 0 else 0.03) * hat)
    mixed = wrap(reverb(out, mix=0.2, size=0.9))
    # Lo-fi: rolled-off highs and a quiet bed of vinyl crackle and hiss.
    mixed = lowpass(lowpass(mixed, 4200), 6000)
    crackle = np.zeros(len(mixed))
    for _ in range(int(LOOP * 14)):
        i = rng.integers(0, len(mixed) - 200)
        crackle[i : i + 120] += rng.uniform(0.01, 0.05) * rng.standard_normal(120) * np.exp(-np.arange(120) / 18)
    hiss = 0.004 * coloured_noise(rng, LOOP, 0.8)
    return mixed + crackle * np.max(np.abs(mixed)) * 0.3 + hiss * np.max(np.abs(mixed))


def campfire(rng):
    """A fire settling and snapping, a deep glow of rumble beneath, and a few crickets chirping in the dark around it."""
    n = int(LOOP * RATE)
    t = np.arange(n) / RATE
    # The glow: brown noise, breathing slowly (an exact number of times a loop), with a hint of hiss on top.
    rumble = lowpass(coloured_noise(rng, LOOP, 2.0), 380)
    breath = 0.75 + 0.25 * np.sin(2 * np.pi * 3 * t / LOOP + 1.0)
    hiss = lowpass(coloured_noise(rng, LOOP, 0.5), 3500)
    hiss = hiss - lowpass(hiss, 1200)
    out = timeline()
    out[:n] += 0.7 * rumble * breath + 0.1 * hiss * breath
    # Crackles: tiny bursts of filtered noise, a few of them louder wood snaps with a short low thump.
    for _ in range(int(LOOP * 22)):
        length = int(rng.uniform(0.002, 0.012) * RATE)
        burst = rng.standard_normal(length) * np.exp(-np.arange(length) / (length / 4))
        burst = burst - lowpass(burst, 800)
        add(out, rng.uniform(0, LOOP), rng.uniform(0.05, 0.4) * burst)
    for _ in range(9):
        length = int(0.05 * RATE)
        k = np.arange(length) / RATE
        snap = rng.standard_normal(length) * np.exp(-k / 0.006)
        snap = snap - lowpass(snap, 600)
        thump = np.sin(2 * np.pi * rng.uniform(90, 150) * k) * np.exp(-k / 0.02)
        add(out, rng.uniform(0.2, LOOP - 0.3), 0.5 * snap + 0.3 * thump)
    out = reverb(out, mix=0.12, size=0.7)
    # Crickets: three of them, each chirping in threes at its own pace and pitch.
    crickets = timeline()
    for pitch, period, offset, level in [(4300, 1.9, 0.3, 0.05), (4700, 2.3, 1.1, 0.04), (3900, 2.9, 0.7, 0.035)]:
        pulse_n = int(0.028 * RATE)
        pt = np.arange(pulse_n) / RATE
        pulse = np.sin(2 * np.pi * pitch * pt) * np.sin(np.pi * pt / 0.028) ** 2
        s = offset
        while s < LOOP:
            for k in range(3):
                add(crickets, s + k * 0.045, level * pulse)
            s += period
    return out + reverb(crickets, mix=0.2, size=0.6)


def cuckoo_call(rng, low=False):
    """One "cuc-koo": two soft, hollow notes falling a minor third."""
    n = int(0.62 * RATE)
    t = np.arange(n) / RATE
    first = 780.0 if not low else 700.0
    second = first * 0.79
    freq = np.where(t < 0.25, first * (1 - 0.04 * np.clip(t / 0.25, 0, 1)), second * (1 - 0.05 * np.clip((t - 0.25) / 0.35, 0, 1)))
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    tone = np.sin(phase) + 0.22 * np.sin(2 * phase) + 0.05 * np.sin(3 * phase)
    note1 = np.clip(np.minimum(t / 0.025, (0.2 - t) / 0.04), 0, 1)
    note2 = np.clip(np.minimum((t - 0.25) / 0.03, (0.6 - t) / 0.1), 0, 1)
    return tone * (note1 + 0.9 * note2)


def cuckoo_clock(rng):
    """A little wooden cuckoo clock: a soft tick and tock all the way round, and the cuckoo calling three times, twice a loop."""
    out = timeline()
    tick_n = int(0.04 * RATE)
    k = np.arange(tick_n) / RATE
    for i in range(int(LOOP / 0.5)):
        # Tick and tock a little apart in pitch, as a pendulum clock's are.
        f = 1500 if i % 2 == 0 else 1150
        tick = np.sin(2 * np.pi * f * k) * np.exp(-k / 0.006) + 0.5 * rng.standard_normal(tick_n) * np.exp(-k / 0.002)
        add(out, i * 0.5, 0.12 * tick)
    for start in (0.4, 8.4):
        # The little door clicks open, the cuckoo calls, and the door closes.
        click = rng.standard_normal(int(0.02 * RATE)) * np.exp(-np.arange(int(0.02 * RATE)) / (0.003 * RATE))
        add(out, start - 0.12, 0.2 * click)
        for call in range(3):
            add(out, start + call * 1.15, 0.55 * cuckoo_call(rng))
        add(out, start + 3 * 1.15, 0.15 * click)
    return reverb(out, mix=0.25, size=0.8)


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


SOUNDS = {
    "birdsong": lambda: finish("birdsong", birdsong(np.random.default_rng(7))),
    "wind_chimes": lambda: finish("wind_chimes", wind_chimes(np.random.default_rng(11))),
    "soft_piano": lambda: finish("soft_piano", soft_piano(np.random.default_rng(3))),
    "bell": lambda: finish("bell", bell(), BELL_LOOP),
    "music_box": lambda: finish("music_box", music_box(np.random.default_rng(5))),
    "singing_bowl": lambda: finish("singing_bowl", singing_bowl(np.random.default_rng(13))),
    "ocean_waves": lambda: finish("ocean_waves", ocean_waves(np.random.default_rng(17))),
    "gentle_rain": lambda: finish("gentle_rain", gentle_rain(np.random.default_rng(19))),
    "marimba": lambda: finish("marimba", marimba(np.random.default_rng(23))),
    "sunrise_arpeggio": lambda: finish("sunrise_arpeggio", sunrise_arpeggio(np.random.default_rng(29))),
    "alarm_bell": lambda: finish("alarm_bell", alarm_bell(), 2.0),
    "digital_beeps": lambda: finish("digital_beeps", digital_beeps(), 1.0, peak=0.45),
    "kalimba": lambda: finish("kalimba", kalimba(np.random.default_rng(31))),
    "morning_choir": lambda: finish("morning_choir", choir_pad(np.random.default_rng(37))),
    "campfire": lambda: finish("campfire", campfire(np.random.default_rng(41))),
    "steel_pan": lambda: finish("steel_pan", steel_pan(np.random.default_rng(43))),
    "lofi_keys": lambda: finish("lofi_keys", lofi_keys(np.random.default_rng(47))),
    "cuckoo_clock": lambda: finish("cuckoo_clock", cuckoo_clock(np.random.default_rng(53))),
}


if __name__ == "__main__":
    # With names, makes just those sounds; without, all of them.
    wanted = sys.argv[1:] or list(SOUNDS)
    unknown = [name for name in wanted if name not in SOUNDS]
    if unknown:
        sys.exit(f"Unknown sound {', '.join(unknown)}. Choose from: {', '.join(SOUNDS)}")
    for name in wanted:
        SOUNDS[name]()
