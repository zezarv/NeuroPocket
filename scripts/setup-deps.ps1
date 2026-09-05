#Requires -Version 5.1
<#
  Восстановление вендорных нативных зависимостей (пины как в рабочем билде).
  Запуск из корня репозитория:  powershell -ExecutionPolicy Bypass -File scripts\setup-deps.ps1
#>
$ErrorActionPreference = "Stop"
$cpp = Join-Path $PSScriptRoot "..\app\src\main\cpp"
New-Item -ItemType Directory -Path $cpp -Force | Out-Null

function Clone-Pinned($url, $dir, $ref, $extraArgs) {
    $dest = Join-Path $cpp $dir
    if (Test-Path (Join-Path $dest ".git")) {
        Write-Host "$dir already cloned, fetching $ref..."
        git -C $dest fetch --depth 1 origin $ref
    } else {
        git clone --depth 1 $url $dest
        git -C $dest fetch --depth 1 origin $ref
    }
    git -C $dest checkout $ref
    if ($extraArgs) { Invoke-Expression $extraArgs }
}

# llama.cpp (чат/vision) — пин d230ddd
Clone-Pinned "https://github.com/ggerganov/llama.cpp.git" "llama.cpp" "d230ddd" $null

# whisper.cpp (+ собственный пребилд)
Clone-Pinned "https://github.com/ggerganov/whisper.cpp.git" "whisper.cpp" "eacbd82" $null
# собрать: cmake -S whisper.cpp -B whisper-build -DANDROID_ABI=arm64-v8a ... (см. docs/BUILD.md)

# stable-diffusion.cpp (+ ggml сабмодуль!)
Clone-Pinned "https://github.com/leejet/stable-diffusion.cpp.git" "sd.cpp" "6b3edaa" `
    'git -C (Join-Path $cpp "sd.cpp") submodule update --init --depth 1 ggml'

# sherpa-onnx: нужен только AAR (уже в app/libs/). Клон — для справки/API:
# git clone --depth 1 --branch v1.13.7 https://github.com/k2-fsa/sherpa-onnx.git

Write-Host "Done. See docs/BUILD.md for prebuilt steps."
