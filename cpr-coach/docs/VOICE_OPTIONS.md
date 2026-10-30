# Pre-Generated Voice Options (EN, HI, KN)

For CPR Coach's pre-rendered WAV coaching clips, offline MMS VITS is robotic in Hindi and Kannada. Below are realistic alternatives evaluated for quality, runtime, and hackathon licensing.

## Evaluated Options

### 1. Microsoft Edge Neural Voices (via `edge-tts`)
- **Languages & Quality:** Excellent naturalness and prosody across English (`en-IN-NeerjaNeural`), Hindi (`hi-IN-SwaraNeural`), Kannada (`kn-IN-SapnaNeural`), and all 10 project languages. Eliminates robotic artifacting.
- **Runtime:** Minimal. Requires Python (`pip install edge-tts`), internet, and `ffmpeg` for WAV conversion. Generates in <1s per sentence on CPU without a GPU.
- **Licence & Terms:** The Python CLI is LGPLv3 ([github.com/rany2/edge-tts](https://github.com/rany2/edge-tts)). However, the underlying service uses Microsoft Edge's Read Aloud endpoint ([microsoft.com/servicesagreement](https://www.microsoft.com/servicesagreement)). Microsoft's terms do *not* grant explicit third-party extraction or redistribution rights; scraping this endpoint is technically unauthorized under general service terms. For an offline, free student hackathon demo baking static WAV assets into an APK, enforcement risk is practically nil, but rights are formally unauthorized.

### 2. AI4Bharat Indic Parler-TTS
- **Languages & Quality:** SOTA open-weights Indic model supporting 21 Indian languages + English, including Hindi (`Rohit`, `Divya`) and Kannada (`Suresh`, `Anu`). High naturalness (Kannada NSS 88.2%, Hindi NSS 84.8%) with description prompt control (e.g. urgent, command, clear audio).
- **Runtime:** Heavy. ~938M parameter seq2seq model requiring PyTorch, Hugging Face `transformers`, and `parler-tts` ([github.com/huggingface/parler-tts](https://github.com/huggingface/parler-tts)). Gated repository requiring HF token approval. CPU generation without a GPU takes ~30–60s per phrase; realistically requires an Nvidia GPU (e.g. Google Colab).
- **Licence & Terms:** Permissive Apache 2.0 ([huggingface.co/ai4bharat/indic-parler-tts](https://huggingface.co/ai4bharat/indic-parler-tts)). Fully and indisputably permitted for student hackathons, open source, and redistribution.

### 3. Piper TTS (sherpa-onnx / Rhasspy)
- **Languages & Quality:** Fast, clean offline neural voices for English and Hindi.
- **Limitation:** *No official Kannada voice model exists* ([github.com/rhasspy/piper](https://github.com/rhasspy/piper)), disqualifying it as a unified solution tonight. License is MIT/Apache 2.0.

## Recommendation

**Use Microsoft Edge Neural Voices via `edge-tts` tonight.** It produces superior, natural audio across English, Hindi, and Kannada in seconds without GPU hardware. Pre-rendered WAV assets remain 100% offline inside the APK. If project judges demand strict FOSS license purity, generate fallback clips via **AI4Bharat Indic Parler-TTS** on a cloud GPU (Google Colab).

## Sample Audio (Generated on this PC)

Samples are located outside the repository in **`D:\voice-samples\`**:
- Hindi: `hindi_sentence1_hands.wav` ("छाती के बीच में दोनों हाथ रखें") & `hindi_sentence2_faster.wav` ("छाती और तेज़ दबाएँ")
- Kannada: `kannada_sentence1_hands.wav` ("ಎದೆಯ ಮಧ್ಯದಲ್ಲಿ ಎರಡೂ ಕೈಗಳನ್ನು ಇರಿಸಿ") & `kannada_sentence2_faster.wav` ("ಎದೆಯನ್ನು ಇನ್ನಷ್ಟು ವೇಗವಾಗಿ ಒತ್ತಿರಿ")
- Additional gender variants (`hi_swara_*`, `hi_madhur_*`, `kn_sapna_*`, `kn_gagan_*`) and manifest-exact clips are detailed in `D:\voice-samples\README.txt`.
