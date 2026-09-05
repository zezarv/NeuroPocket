package com.neuropocket.app.data

object ModelCatalog {
    // Диапазон от самых маленьких до относительно больших.
    // URL — прямые ссылки на HuggingFace (GGUF, Q4_K_M где возможно).
    // NSFW-модели помечены nsfw=true — без цензуры, ответственность на пользователе.
    val models: List<AiModelInfo> = listOf(
        AiModelInfo(
            id = "qwen2-0_5b-q4",
            name = "Qwen2 0.5B Q4 (ультра-мал.)",
            sizeLabel = "0.5B",
            ramNeedGb = 0.8,
            fileName = "qwen2-0_5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2-0.5B-Instruct-GGUF/resolve/main/qwen2-0_5b-instruct-q4_k_m.gguf",
            descRu = "Самая быстрая для слабых тел. Тест, черновики, соцлента."
        ),
        AiModelInfo(
            id = "llama32-1b-q4",
            name = "Llama 3.2 1B Q4",
            sizeLabel = "1B",
            ramNeedGb = 1.5,
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            descRu = "Оптимально для телефона. Чат, перевод, улучшение."
        ),
        AiModelInfo(
            id = "qwen25-1_5b-q4",
            name = "Qwen2.5 1.5B Q4",
            sizeLabel = "1.5B",
            ramNeedGb = 2.2,
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            descRu = "Баланс скорость/качество. Русский средний."
        ),
        AiModelInfo(
            id = "qwen25-coder-1_5b-q4",
            name = "Qwen2.5-Coder 1.5B Q4",
            sizeLabel = "1.5B-code",
            ramNeedGb = 2.2,
            fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            descRu = "Заточена под код: вайбкод, разбор ошибок. Чат слабее обычной."
        ),
        AiModelInfo(
            id = "gemma2-2b-q4",
            name = "Gemma 2 2B Q4",
            sizeLabel = "2B",
            ramNeedGb = 2.6,
            fileName = "gemma-2-2b-it-Q4_K_M.gguf",
            url = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            descRu = "Аккуратная малая модель Google. Английский лучше русского."
        ),
        AiModelInfo(
            id = "r1-distill-1_5b-q4",
            name = "DeepSeek-R1-Distill 1.5B Q4",
            sizeLabel = "1.5B-R1",
            ramNeedGb = 2.2,
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            url = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            descRu = "Рассуждает по шагам (думает вслух). Математика, логика. Медленнее обычных."
        ),
        AiModelInfo(
            id = "qwen3-1_7b-q8",
            name = "Qwen3 1.7B Q8",
            sizeLabel = "1.7B",
            ramNeedGb = 3.0,
            fileName = "Qwen3-1.7B-Q8_0.gguf",
            url = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q8_0.gguf",
            descRu = "Новейший Qwen, гибрид думающий/обычный. Q8 — качество выше Q4."
        ),
        AiModelInfo(
            id = "qwen2vl-2b-q4",
            name = "Qwen2-VL 2B Q4 (видит фото!)",
            sizeLabel = "2B-vision",
            ramNeedGb = 2.6,
            fileName = "Qwen2-VL-2B-Instruct-Q4_K_M.gguf",
            url = "https://huggingface.co/ggml-org/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-Q4_K_M.gguf",
            kind = "vision-text",
            descRu = "Текстовая часть vision-модели. Загрузи в RAM, затем mmproj ниже — и спрашивай про фото."
        ),
        AiModelInfo(
            id = "llama32-3b-q4",
            name = "Llama 3.2 3B Q4",
            sizeLabel = "3B",
            ramNeedGb = 3.5,
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            descRu = "Рекомендуемая основная для S24 Ultra. Агент, вайбкод-заготовки."
        ),
        AiModelInfo(
            id = "qwen25-3b-q4",
            name = "Qwen2.5 3B Q4",
            sizeLabel = "3B",
            ramNeedGb = 3.8,
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            descRu = "Хороший русский, инструкции."
        ),
        AiModelInfo(
            id = "phi35-mini-q4",
            name = "Phi-3.5 Mini 3.8B Q4",
            sizeLabel = "3.8B",
            ramNeedGb = 4.2,
            fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            url = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            descRu = "Сильна в рассуждениях на телефоне."
        ),
        AiModelInfo(
            id = "mistral7b-q4",
            name = "Mistral 7B v0.3 Q4",
            sizeLabel = "7B",
            ramNeedGb = 6.0,
            fileName = "mistral-7b-instruct-v0.3-Q4_K_M.gguf",
            url = "https://huggingface.co/bartowski/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf",
            descRu = "Тяжёлая, только с запасом ОЗУ. Лучше качество."
        ),
        AiModelInfo(
            id = "qwen25-7b-q4",
            name = "Qwen2.5 7B Q4",
            sizeLabel = "7B",
            ramNeedGb = 6.5,
            fileName = "qwen2.5-7b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf",
            descRu = "Максимум для 9-10 ГБ свободно. Медленнее, но умнее."
        ),
        // NSFW / uncensored — без встроенных ограничений, только 18+
        AiModelInfo(
            id = "nsfw-dolphin-27b-unc",
            name = "Dolphin 2.9 8B Uncensored Q4 [18+]",
            sizeLabel = "8B-unc",
            ramNeedGb = 7.0,
            fileName = "dolphin-2.9.4-llama3-8b-q4_k_m.gguf",
            url = "https://huggingface.co/cognitivecomputations/dolphin-2.9.4-llama3-8b-gguf/resolve/main/dolphin-2.9.4-llama3-8b-q4_k_m.gguf",
            nsfw = true,
            descRu = "Без цензуры (uncensored). Ролевые, персоны 18+. Только для взрослых, локально."
        ),
        AiModelInfo(
            id = "nsfw-dolphin-mistral-7b",
            name = "Dolphin Mistral 7B Uncensored Q4 [18+]",
            sizeLabel = "7B-unc",
            ramNeedGb = 6.0,
            fileName = "dolphin-2.1-mistral-7b.Q4_K_M.gguf",
            url = "https://huggingface.co/TheBloke/dolphin-2.1-mistral-7B-GGUF/resolve/main/dolphin-2.1-mistral-7b.Q4_K_M.gguf",
            nsfw = true,
            descRu = "Проверенная uncensored 7B. Легче 8B-версии, тянет средний контекст."
        )
    )

    val whisperModels: List<AiModelInfo> = listOf(
        AiModelInfo(
            id = "whisper-tiny",
            name = "Whisper tiny (75 МБ)",
            sizeLabel = "tiny",
            ramNeedGb = 0.5,
            fileName = "ggml-tiny.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            kind = "whisper",
            descRu = "Быстрая черновая транскрибация. WAV 16 кГц моно."
        ),
        AiModelInfo(
            id = "whisper-base",
            name = "Whisper base (142 МБ)",
            sizeLabel = "base",
            ramNeedGb = 0.8,
            fileName = "ggml-base.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            kind = "whisper",
            descRu = "Баланс для русского. Рекомендуемая."
        ),
        AiModelInfo(
            id = "whisper-small",
            name = "Whisper small (466 МБ)",
            sizeLabel = "small",
            ramNeedGb = 1.5,
            fileName = "ggml-small.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
            kind = "whisper",
            descRu = "Точнее, но медленнее на телефоне."
        ),
        AiModelInfo(
            id = "whisper-large-turbo",
            name = "Whisper large-v3-turbo (830 МБ)",
            sizeLabel = "large-turbo",
            ramNeedGb = 2.5,
            fileName = "ggml-large-v3-turbo.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo.bin",
            kind = "whisper",
            descRu = "Почти как large, но в разы быстрее. Лучшее качество на S24 Ultra."
        )
    )

    // Глаза для vision-моделей (порядок в описании).
    val embedModels: List<AiModelInfo> = listOf(
        AiModelInfo(
            id = "e5-small-q8",
            name = "E5-small multilingual Q8 (120 МБ)",
            sizeLabel = "embed",
            ramNeedGb = 0.5,
            fileName = "multilingual-e5-small-q8_0.gguf",
            url = "https://huggingface.co/TwinSunsLLC/multilingual-e5-small-gguf/resolve/main/multilingual-e5-small-q8_0.gguf",
            kind = "embed",
            descRu = "Вектора для поиска по заметкам. Русский понимает. Для RAG."
        )
    )

    val mmprojModels: List<AiModelInfo> = listOf(
        AiModelInfo(
            id = "mmproj-qwen2vl-2b",
            name = "mmproj Qwen2-VL 2B Q8 (зрение)",
            sizeLabel = "mmproj",
            ramNeedGb = 0.8,
            fileName = "mmproj-Qwen2-VL-2B-Instruct-Q8_0.gguf",
            url = "https://huggingface.co/ggml-org/Qwen2-VL-2B-Instruct-GGUF/resolve/main/mmproj-Qwen2-VL-2B-Instruct-Q8_0.gguf",
            kind = "mmproj",
            descRu = "Глаза для Qwen2-VL. Порядок: 1) vision-GGUF в RAM 2) этот файл кнопкой «Зрение в RAM»."
        )
    )

    // Проверка URL: официальный README stable-diffusion.cpp.
    // 4.2 ГБ — качай по Wi-Fi. TAESD/LoRA — импортируй свои файлы (слоты в мосте готовы).
    val voiceModels: List<AiModelInfo> = listOf(
        AiModelInfo(
            id = "tts-denis",
            name = "Голос Денис (муж., ~60 МБ)",
            sizeLabel = "tts-ru",
            ramNeedGb = 0.4,
            fileName = "vits-piper-ru_RU-denis-medium.tar.bz2",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-denis-medium.tar.bz2",
            kind = "voice",
            descRu = "Мужской русский голос (Piper). Распакуется сам при обновлении списка."
        ),
        AiModelInfo(
            id = "tts-irina",
            name = "Голос Ирина (жен., ~60 МБ)",
            sizeLabel = "tts-ru",
            ramNeedGb = 0.4,
            fileName = "vits-piper-ru_RU-irina-medium.tar.bz2",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-irina-medium.tar.bz2",
            kind = "voice",
            descRu = "Женский русский голос (Piper). Для персон — то, что надо."
        ),
        AiModelInfo(
            id = "tts-dmitri",
            name = "Голос Дмитрий (муж., ~60 МБ)",
            sizeLabel = "tts-ru",
            ramNeedGb = 0.4,
            fileName = "vits-piper-ru_RU-dmitri-medium.tar.bz2",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-dmitri-medium.tar.bz2",
            kind = "voice",
            descRu = "Второй мужской голос, тембр ниже Дениса."
        ),
        AiModelInfo(
            id = "vad-silero",
            name = "VAD Silero (2 МБ, для голосового чата)",
            sizeLabel = "vad",
            ramNeedGb = 0.2,
            fileName = "silero_vad.onnx",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            kind = "vad",
            descRu = "Детектор речи: голосовой чат сам понимает, когда ты замолчал."
        )
    )

    val taesdModels: List<AiModelInfo> = listOf(
        AiModelInfo(
            id = "taesd-sd",
            name = "TAESD tiny decoder (10 МБ)",
            sizeLabel = "taesd",
            ramNeedGb = 0.3,
            fileName = "taesd-diffusion.safetensors",
            url = "https://huggingface.co/madebyollin/taesd/resolve/main/diffusion_pytorch_model.safetensors",
            kind = "taesd",
            descRu = "Крошечный декодер: финал картинки в разы быстрее. Подхватывается сам."
        )
    )

    val sdModels: List<AiModelInfo> = listOf(
        AiModelInfo(
            id = "sd15-pruned",
            name = "SD 1.5 pruned emaonly (4.2 ГБ)",
            sizeLabel = "SD1.5",
            ramNeedGb = 5.0,
            fileName = "v1-5-pruned-emaonly.safetensors",
            url = "https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-v1-5/resolve/main/v1-5-pruned-emaonly.safetensors",
            kind = "image",
            descRu = "Эталонная модель из README sd.cpp. 512px, сэмплер LCM 4-8 шагов — минуты на S24 Ultra CPU."
        ),
        AiModelInfo(
            id = "sd-realistic-vision",
            name = "Realistic Vision 5.1 fp16 (2 ГБ)",
            sizeLabel = "SD1.5-real",
            ramNeedGb = 4.0,
            fileName = "Realistic_Vision_V5.1_fp16-no-ema.safetensors",
            url = "https://huggingface.co/SG161222/Realistic_Vision_V5.1_noVAE/resolve/main/Realistic_Vision_V5.1_fp16-no-ema.safetensors",
            kind = "image",
            descRu = "Фотореализм: портреты, кожа, свет. Формат SD1.5 — работает в нашем движке."
        ),
        AiModelInfo(
            id = "sd-toonyou",
            name = "ToonYou Beta6 (2 ГБ) [18+]",
            sizeLabel = "SD1.5-anime",
            ramNeedGb = 4.0,
            fileName = "toonyou_beta6.safetensors",
            url = "https://huggingface.co/frankjoshua/toonyou_beta6/resolve/main/toonyou_beta6.safetensors",
            kind = "image",
            nsfw = true,
            descRu = "Аниме-стиль. Без фильтра: 18+ контент по промпту. Только для взрослых."
        ),
        AiModelInfo(
            id = "sd-dreamlike-anime",
            name = "Dreamlike Anime 1.0 (4 ГБ) [18+]",
            sizeLabel = "SD1.5-anime",
            ramNeedGb = 5.0,
            fileName = "dreamlike-anime-1.0.safetensors",
            url = "https://huggingface.co/dreamlike-art/dreamlike-anime-1.0/resolve/main/dreamlike-anime-1.0.safetensors",
            kind = "image",
            nsfw = true,
            descRu = "Красивое аниме, хентай-стилистика по промпту. Без цензуры, 18+."
        )
    )
}
