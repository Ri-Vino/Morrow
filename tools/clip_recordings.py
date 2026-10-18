"""Cuts Morrow's recorded alarm sounds from public domain recordings on Wikimedia Commons.

Each clip is the first 24 seconds of the piece, from its first note, with a short fade at the end so it repeats
without a jolt. iOS's AlarmKit only takes sounds under 30 seconds. Every source and its licence is listed in
shared/src/commonMain/composeResources/files/licenses/recordings.md.

Download the files named in RECORDINGS into a folder of their own, then run from the repo root:

    python tools/clip_recordings.py path/to/that/folder

Needs numpy, scipy and soundfile. Writes Ogg Vorbis to androidApp/src/main/res/raw/ and CAF to iosApp/iosApp/Sounds/,
as tools/make_sounds.py does.
"""

import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

RATE = 44_100
CLIP = 24.0
FADE_OUT = 2.5
OUT = Path(__file__).resolve().parent.parent / "androidApp" / "src" / "main" / "res" / "raw"
IOS_OUT = Path(__file__).resolve().parent.parent / "iosApp" / "iosApp" / "Sounds"

# The app's name for each clip, and the file downloaded for it.
RECORDINGS = {
    "morning_mood": "grieg.ogg",  # Musopen_-_Morning.ogg
    "gymnopedie": "satie.ogg",  # Erik_Satie_-_gymnopedies_-_la_1_ere._lent_et_douloureux.ogg
    "the_lark": "haydn.ogg",  # Haydn_-_String_Quartet,_Op._64_No._5 ... IV._Finale._Vivace_(Musopen_String_Quartet).flac
    "figaro": "mozart.ogg",  # Mozart_-_Le_nozze_di_Figaro,_K492_-_Overture_(Musopen_Symphony).flac
    "goldberg_aria": "bach.ogg",  # Bach,_J_S_-_Goldberg_Variations,_BWV988_-_01._Aria_(Shelley_Katz).flac
    "italian_symphony": "mendelssohn.ogg",  # Mendelssohn_-_Symphony_No._4 ... I._Allegro_vivace_(Musopen_Symphony).flac
}

# Every clip sits at about the same loudness, as the made-up sounds do, without peaking past about -1 dBFS.
TARGET_RMS = 0.12
PEAK = 0.89


def first_note(signal, threshold=0.01):
    """Where the music starts: the first sample a hundredth of full scale or louder, less a breath before it."""
    loud = np.flatnonzero(np.abs(signal) >= threshold * np.max(np.abs(signal)))
    return max(0, loud[0] - int(0.05 * RATE)) if len(loud) else 0


def clip(path):
    audio, rate = sf.read(path, always_2d=True)
    mono = audio.mean(axis=1)
    if rate != RATE:
        mono = resample_poly(mono, RATE, rate)
    start = first_note(mono)
    out = mono[start : start + int(CLIP * RATE)].copy()
    out -= out.mean()
    fade = int(FADE_OUT * RATE)
    out[-fade:] *= np.cos(np.linspace(0, np.pi / 2, fade)) ** 2
    out[: int(0.01 * RATE)] *= np.linspace(0, 1, int(0.01 * RATE))
    rms = np.sqrt(np.mean(out**2))
    return out * min(TARGET_RMS / rms, PEAK / np.max(np.abs(out)))


def main(folder):
    for name, file in RECORDINGS.items():
        out = clip(Path(folder) / file)
        # In blocks: handed a whole clip at once, libsndfile's Vorbis encoder overflows its stack on Windows.
        with sf.SoundFile(OUT / f"{name}.ogg", "w", RATE, 1, format="OGG", subtype="VORBIS") as ogg:
            for block in range(0, len(out), 4096):
                ogg.write(out[block : block + 4096].astype(np.float32))
        # Uncompressed, so half the sample rate keeps the files small.
        sf.write(IOS_OUT / f"{name}.caf", resample_poly(out, 1, 2).astype(np.float32), RATE // 2, format="CAF", subtype="PCM_16")
        print(f"{name}: {len(out) / RATE:.1f} s, {(OUT / f'{name}.ogg').stat().st_size // 1024} KB ogg, "
              f"{(IOS_OUT / f'{name}.caf').stat().st_size // 1024} KB caf")


if __name__ == "__main__":
    main(sys.argv[1])
