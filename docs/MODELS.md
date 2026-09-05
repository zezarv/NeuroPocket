# Каталог моделей

Точные URL и файлы — источник правды в `app/.../data/ModelCatalog.kt`
(все ссылки проверены HTTP 200 на момент добавления).
Все модели качаются внутрь приложения (папка `models/`), интернет после не нужен.

## Текст (GGUF, llama.cpp)

| Модель | Файл | RAM ~ |
|---|---|---|
| Qwen2 0.5B Q4 | `qwen2-0_5b-instruct-q4_k_m.gguf` | 0.8 ГБ |
| Llama 3.2 1B Q4 | `Llama-3.2-1B-Instruct-Q4_K_M.gguf` | 1.5 ГБ |
| Qwen2.5 1.5B / Coder 1.5B Q4 | `qwen2.5-*.gguf` | 2.2 ГБ |
| DeepSeek-R1-Distill 1.5B Q4 | `DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf` | 2.2 ГБ |
| Gemma 2 2B Q4 | `gemma-2-2b-it-Q4_K_M.gguf` | 2.6 ГБ |
| Qwen3 1.7B Q8 | `Qwen3-1.7B-Q8_0.gguf` | 3.0 ГБ |
| Llama 3.2 3B Q4 ⭐ старт | `Llama-3.2-3B-Instruct-Q4_K_M.gguf` | 3.5 ГБ |
| Qwen2.5 3B / Phi-3.5-mini | `*.gguf` | ~4 ГБ |
| Mistral 7B / Qwen2.5 7B Q4 | `*.gguf` | ~6–6.5 ГБ |
| Dolphin 8B uncensored [18+] | `dolphin-2.9.4-llama3-8b-q4_k_m.gguf` | 7 ГБ |
| Dolphin Mistral 7B uncensored [18+] | `dolphin-2.1-mistral-7b.Q4_K_M.gguf` | 6 ГБ |

## Зрение

| Модель | Файл |
|---|---|
| Qwen2-VL 2B текст | `Qwen2-VL-2B-Instruct-Q4_K_M.gguf` |
| mmproj Qwen2-VL 2B | `mmproj-Qwen2-VL-2B-Instruct-Q8_0.gguf` |

Порядок: сначала vision-GGUF «В RAM», потом mmproj «Зрение в RAM».

## Речь и вектора

| Модель | Файл |
|---|---|
| Whisper tiny/base/small/large-turbo | `ggml-*.bin` (75 МБ – 830 МБ) |
| Голоса Piper RU: Денис, Ирина, Дмитрий | `vits-piper-ru_RU-*.tar.bz2` (распаковка авто) |
| Silero VAD | `silero_vad.onnx` (2 МБ) |
| E5-small multilingual (RAG) | `multilingual-e5-small-q8_0.gguf` (~120 МБ) |

## Картинки (SD 1.5-совместимые)

| Модель | Файл | RAM ~ |
|---|---|---|
| SD 1.5 pruned emaonly | `v1-5-pruned-emaonly.safetensors` (4.2 ГБ) | 5 ГБ |
| Realistic Vision 5.1 fp16 | `Realistic_Vision_V5.1_fp16-no-ema.safetensors` (2 ГБ) | 4 ГБ |
| ToonYou Beta6 (anime) [18+] | `toonyou_beta6.safetensors` (2 ГБ) | 4 ГБ |
| Dreamlike Anime 1.0 [18+] | `dreamlike-anime-1.0.safetensors` (4 ГБ) | 5 ГБ |
| TAESD decoder | `taesd-diffusion.safetensors` (10 МБ) | — |

Генерация 512px на CPU — минуты; LCM 4–8 шагов; есть img2img.
