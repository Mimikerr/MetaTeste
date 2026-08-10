# whisper-service

Microserviço de transcrição de voz (STT) usado pelo módulo `:host`, rodando
[faster-whisper](https://github.com/SYSTRAN/faster-whisper) em CPU (`device="cpu"`,
`compute_type="int8"`). É um processo Python independente do build Gradle —
precisa estar rodando antes (ou junto) do `:host`.

Nota: o plano original previa `device="cuda"` numa GPU NVIDIA (GTX 1660 Super),
mas a GPU real desta máquina é AMD (RX 6600) — o CTranslate2 usado pelo
faster-whisper não acelera por GPU em placas AMD no Windows, então o serviço
roda em CPU. O modelo "small" continua bem mais preciso que o Vosk anterior,
só que sem aceleração de hardware.

## Requisitos

- Python 3.10+ (nenhuma dependência de GPU/CUDA)

## Rodando

```
cd host/whisper-service
python -m venv venv
.\venv\Scripts\pip install -r requirements.txt
.\venv\Scripts\python -m uvicorn whisper_server:app --host 127.0.0.1 --port 8000
```

O `:host` (Kotlin) espera o serviço em `http://127.0.0.1:8000/transcribe` por
padrão — configurável via variável de ambiente `NEXUS_WHISPER_URL`.

## Testando manualmente

```
curl -X POST http://127.0.0.1:8000/transcribe -F "file=@caminho/para/audio.pcm"
```

Resposta esperada: `{"text": "..."}`.
