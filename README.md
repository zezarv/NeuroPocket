# NeuroPocket 🤖📱

Локальный ИИ-хаб на Android: чат, персоны, голос, фото, RAG — **всё на устройстве, без облаков по умолчанию**.

Offline-first AI hub for Android: chat, personas, voice, image gen, RAG — on-device by default.

![minSdk](https://img.shields.io/badge/minSdk-28-blue) ![arch](https://img.shields.io/badge/arch-arm64--v8a-green) ![license](https://img.shields.io/badge/license-MIT-lightgrey)

## Что умеет

| Модуль | Движок | Статус |
|---|---|---|
| 💬 Чат + мульти-сессии, стрим токенов | llama.cpp (CPU, Vulkan готов) | ✅ |
| 🎭 Персоны: карточки, аватары, голоса, свои движки, 18+ | локально | ✅ |
| 🗣 Голосовой чат hands-free (VAD → whisper → LLM → TTS) | sherpa-onnx + whisper | ✅ |
| 🎙 Транскрибация (wav/mp3/m4a, таймкоды, диктофон) | whisper.cpp | ✅ |
| 🎨 Генерация фото 384–768px, img2img, TAESD | stable-diffusion.cpp | ✅ |
| 👁 Вопросы про фото (камера/галерея/шаринг) | llama.cpp mtmd (Qwen2-VL) | ✅ |
| 🧠 RAG по своим заметкам (E5-small) | llama.cpp embeddings | ✅ |
| 🤖 Агент с планом и шагами | любой текстовый движок | ✅ |
| 🌐 ПК и API: LM Studio, Ollama, OpenRouter, Gemini, Groq, Pollinations | OpenAI-совместимый клиент | ✅ |
| 📰 Лента с хэштегами, лайками, автопостингом | локально | ✅ |
| 📦 Бэкап/восстановление (AES), автобэкап | локально | ✅ |
| 📊 Бенчмарк токенов/с, история замеров | нативно | ✅ |
| 🛡 Ключи API в EncryptedSharedPreferences | Keystore | ✅ |

Подробный каталог моделей (текст/vision/whisper/голоса/SD) — в [docs/MODELS.md](docs/MODELS.md).
Сборка с нуля — в [docs/BUILD.md](docs/BUILD.md).

## Быстрый старт (пользователь)

1. Установи APK из [Releases](../../releases) (нужен ARM64, Android 9+ / minSdk 28).
2. Пройди мастера: телефон / ПК / бесплатное облако.
3. Для телефона: Модели → скачай GGUF (начни с Llama 3.2 3B) → «В RAM» → Чат.
4. Для ПК: подставь IP в пресете LM Studio/Ollama → «Проверить».

## Приватность

- Весь инференс по умолчанию — на устройстве, тексты никуда не уходят.
- Ключи API — только в шифрованном хранилище, в бэкап попадают лишь под паролем.
- Сеть нужна только для скачивания моделей и внешних API, которые ты сам подключил.

## Структура проекта

```
app/src/main/
  java/com/neuropocket/app/
    MainActivity.kt        — табы, overlay-маршруты, шаринг, автожизнь моделей
    AppViewModel.kt        — всё состояние, движки, загрузки, бэкапы
    data/                  — модели, Store (DataStore), каталоги, Vault, воркеры
    engine/                — JNI-обёртки: llama, whisper, SD, remote, fallback
    ui/                    — Compose-экраны (хаб, чат, персоны, лента, модели…)
    voice/                 — STT/TTS, диктофон, VAD-хелперы, ресемплинг
  cpp/
    bridge.cpp             — JNI: llama + sampler + bench + mtmd-vision + embeddings
    whisper_bridge.cpp     — JNI: whisper (+таймкоды сегментов)
    sd_bridge.cpp          — JNI: txt2img + img2img
    llama.cpp/ whisper.cpp/ sd.cpp/ sherpa-onnx/  — вендор (см. setup-deps)
  libs/sherpa_onnx.aar     — собранный AAR (TDD/VAD/ONNX Runtime)
```

## Версии

| Версия | Главное |
|---|---|
| v1.25.0-rc.1 | P0-харденинг: RoundTable append, STT persistence, backup backward-compat, safe reset/delete, semver updater, NSC, unit-тесты ядра |
| v1.24 | Удаление файлов, factory reset, auto STT, продолжение круглого стола, edit постов, лёгкий voice engine |
| v1.23 | Папки+поиск в drawer, SD hires+пресеты, репосты, виджет |
| v1.22 | Barge-in, sys bars, avatar import, md quotes/headers, feed search, hero gradient |
| v1.21 | Regenerate, rename, timestamps, API timeout, storage, feed refresh, hf log |
| v1.20 | System bars, persona avatar import, md quotes/headers, feed search |
| v1.19 | Comments, md lists/tables, notes search, VAD tuning, TAESD, autopost, pins, timings |
| v1.18 | Лёгкий APK (SD engine on-demand), in-app updates, SD controls+img2img, whisper timestamps, chat export/search, auto-backup |
| v1.17 | Диагностика, шифрование бэкапа, шаринг персон, ctx 8192, «Думаю/Печатает» |
| v1.16 | TAESD, автопостинг, пины, замеры whisper/SD |
| v1.15 | Шаринг в приложение, SD 768/img2img, таймкоды, экспорт чата, автобэкап |
| v1.14 | Круглый стол персон |
| v1.13 | **Vulkan-бэкенд** (Adreno), GPU-слои |
| v1.12 | Редактор персон, markdown, онбординг, фолбэк движков |
| v1.11 | Свои среды инструментов, чаты персон, аватары, лента с тегами, mp3/m4a |
| v1.10 | Провайдеры (LM Studio/Ollama/облака), SSE-стрим |
| v1.9 | Vision (Qwen2-VL), sherpa TTS + hands-free, RAG, шифрование ключей |
| v1.0–v1.8 | Хаб, чат-стрим, whisper, SD, агент, темы, релизная подпись |

## Лицензия

MIT — см. [LICENSE](LICENSE). Модели качаются отдельно под своими лицензиями.
NSFW-функции — строго 18+, ответственность пользователя.
