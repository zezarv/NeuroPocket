# Сборка NeuroPocket с нуля (Windows)

Проверено на: Windows 10/11 x64, JDK 17, Android SDK 34, NDK r26d.

## 1. Инструменты

| Что | Как |
|---|---|
| JDK 17 | `winget install Microsoft.OpenJDK.17` |
| Android cmdline-tools | [раздел SDK](https://developer.android.com/studio#command-line-tools-only), распаковать |
| Gradle 8.7+ | системный (`choco`/`winget`/`scoop`) или wrapper |
| Git, CMake 3.22+, Python 3 (для скриптов) | обычно уже есть |
| Vulkan SDK *(опционально, для GPU)* | `winget install KhronosGroup.VulkanSDK`, нужен `VULKAN_SDK` в окружении |
| MinGW GCC *(только для сборки Vulkan-шейдеров)* | `winget install BrechtSanders.WinLibs.POSIX.UCRT` |

```bat
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;26.3.11579264" "cmake;3.22.1"
```

## 2. Зависимости (натив)

```powershell
.\scripts\setup-deps.ps1
```

Скрипт клонирует пины: `llama.cpp`, `whisper.cpp (+сборка пребилда)`,
`stable-diffusion.cpp (+ggml сабмодуль, +сборка пребилда)`, `sherpa-onnx`
(только для справки — AAR уже лежит в `app/libs/`).

Версии пребилдов и JNI описаны в `app/src/main/cpp/CMakeLists.txt`.

## 3. Подпись релиза (своя!)

`release.keystore` в репозиторий **не входит** и входить не должен.

```bat
keytool -genkeypair -keystore release.keystore -alias neuropocket -keyalg RSA -keysize 2048 -validity 10000
```

Пароли — в `keystore.properties` рядом с корнем (создай по образцу ниже, файл в git не идёт).
Можно и через переменные окружения `NP_STORE_FILE/NP_STORE_PASS/NP_KEY_ALIAS/NP_KEY_PASS`.

```properties
storeFile=../release.keystore
storePassword=***
keyAlias=neuropocket
keyPassword=***
```

## 4. Сборка

```bat
gradle -p . assembleRelease
:: APK: app\build\outputs\apk\release\app-release.apk
```

Debug: `assembleDebug` (пакет `com.neuropocket.app.debug`).

## 5. Структура нативных либ в APK (arm64-v8a)

`libllama + libmtmd + libggml*` (чат/vision), `libnpwhisper` (STT),
`libnpsd` (SD ~51 МБ), `libsherpa-onnx-jni + libonnxruntime` (TTS/VAD),
`libggml-vulkan` (GPU, если собрано с Vulkan SDK).

## 6. FAQ сборки

- `Could not find SPIRV-Headers` → поставь Vulkan SDK, выставь `VULKAN_SDK`.
- `shader-gen` не собирается GCC16+ → нужен MSVC BT или GCC ≤14 (см. `CMakeLists.txt`).
- `ccache ... CreateProcess failed` → уже выключен флагом `GGML_CCACHE=OFF`.
- Kotlin `Platform declaration clash` — не называй `fun setX` при `var x` (JVM-сигнатуры).
- Все исходники — строго UTF-8 без BOM. Не открывай `.kt` в PowerShell-текстовиках.
