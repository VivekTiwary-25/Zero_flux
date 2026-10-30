# Install CPR Coach on a team phone (Windows PC)

You need: a Windows PC with the `cpr-coach` checkout, an Android 11+ phone, and a USB cable.

1. On the phone, turn on USB debugging: **Settings → About phone → tap Build number 7 times**, then **Settings → System → Developer options → turn on USB debugging**. Connect the phone by USB, tap **Allow** on the "Allow USB debugging?" prompt.
2. On the PC, open PowerShell in the `cpr-coach` folder and check the phone is seen: `adb devices` should list one device as `device`. If `adb` is not found, add the Android `platform-tools` folder to PATH (the script also looks in `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`). If several phones are attached, note each phone's serial for `-Serial`.
3. Copy the English Whisper files into the app assets before building: `.\tools\push-models.ps1 -CopyWhisper`. This copies `tiny.en-encoder.int8.onnx`, `tiny.en-decoder.int8.onnx` and `tiny.en-tokens.txt` from the iTantra assets folder into `app\src\main\assets`. These files are not in git (see `.gitignore`: `app/src/main/assets/tiny.en-*`), so this step is required on every fresh checkout.
4. Build the debug APK: `.\gradlew.bat assembleDebug`. The APK lands at `app\build\outputs\apk\debug\app-debug.apk`.
5. Install it: `adb install -r app-debug.apk`. The terminal should finish with **Success**. Open **CPR Coach** once and allow camera and microphone.
6. Push the speech models: `.\tools\push-models.ps1` (add `-Serial <serial>` if several phones are attached, `-ModelDir <folder>` if your IndicConformer model folder is not the default `D:\projects\sih\iTantra\model-staging\indicconformer`). The folder must contain `shared\encoder.weights.bin`, `kn.onnx` and `languages\kn\tokens.txt`. The copy takes about 0.7 GB on the phone and needs about 1.5 GB free during the copy; the script checks this and stops if space is short.
7. Check it worked: the script ends with `DONE: CPR Coach models are on phone <serial>` and lists the files in the app's private folder. On the phone, open the app, pick a non-English language from the language picker, and run a short practise session: you should hear coaching in that language and mic commands in that language should work.
8. Reinstalling later with `adb install -r` keeps the pushed models, so you do not need to push them again. Fully uninstalling (`adb uninstall com.zeroflux.cprcoach`, or uninstall from Settings) deletes the app and its private model files, so you must push the models again after a fresh install.

The model push is needed for every language except English. English speech recognition uses the Whisper files built into the APK (step 3); Hindi, Kannada, Tamil, Telugu, Malayalam, Marathi, Gujarati, Bengali and Odia use the pushed IndicConformer models.
