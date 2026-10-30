# Coaching clip generator

`generate_clips.py` synthesizes the adjacent `phrases_<code>.json` files with sherpa-onnx OfflineTts. It writes mono, 16-bit PCM WAVs and a per-language `manifest.json` under the app's `assets/clips/` directory. Manifest entries contain the language code, phrase key, original phrase text, and SHA-256 of that UTF-8 text.

```powershell
python generate_clips.py --models D:\projects\sih\iTantra\app\src\main\assets --out ..\..\app\src\main\assets\clips --lang en,hi,kn,bn,or,gu,mr,te,ta,ml
```

Install `sherpa-onnx` in the Python environment first. Models are resolved by their fixed `vits-*` folder names beneath `--models`. English, Hindi, and Malayalam use Piper with their bundled `espeak-ng-data`; the other supported voices use MMS. MMS phrases are filtered by the model's character tokens, dropped characters are reported, and any MMS phrase containing digits fails. Output is silence-trimmed and peak-normalized to approximately -3 dBFS. The model-provided sample rate is preserved.

`--lang` accepts a comma-separated subset of `en,hi,kn,bn,or,gu,mr,te,ta,ml`; without it all supported codes are attempted. `--dry-run` checks phrase files and voice initialization without creating audio. The command continues after per-phrase failures, prints a summary, and returns non-zero if anything failed. Generated clips must be reviewed by a person before their quality is treated as verified.
