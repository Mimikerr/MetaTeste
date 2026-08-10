# tts-service

Microserviço de síntese de voz (TTS) usado pelo módulo `:host`, rodando
[edge-tts](https://github.com/rany2/edge-tts) (vozes neurais da Microsoft, grátis,
sem API key). É um processo Python independente do build Gradle — precisa estar
rodando antes (ou junto) do `:host` para o assistente responder falando; se não
estiver, o host simplesmente não manda áudio de volta (a resposta continua
chegando em texto).

edge-tts gera MP3; o serviço decodifica e reamostra para PCM16LE mono cru (mesmo
formato que o app Quest já captura com `AudioRecord`) via `pydub`, que por sua vez
depende do `ffmpeg` estar instalado e no PATH do sistema.

## Requisitos

- Python 3.10+
- [ffmpeg](https://ffmpeg.org/download.html) instalado e no PATH (necessário para o `pydub` decodificar o MP3 do edge-tts)

## Rodando

```
cd host/tts-service
python -m venv venv
.\venv\Scripts\pip install -r requirements.txt
.\venv\Scripts\python -m uvicorn tts_server:app --host 127.0.0.1 --port 8001
```

O `:host` (Kotlin) espera o serviço em `http://127.0.0.1:8001/synthesize` por
padrão — configurável via variável de ambiente `NEXUS_TTS_URL`. A voz padrão é
`pt-BR-FranciscaNeural` — configurável via `NEXUS_TTS_VOICE` (outras opções:
`pt-BR-AntonioNeural`, ou qualquer voz listada por `edge-tts --list-voices`).

## Testando manualmente

```
curl -X POST http://127.0.0.1:8001/synthesize \
  -H "Content-Type: application/json" \
  -d "{\"text\": \"Terminal injetado com sucesso.\"}" \
  --output resposta.pcm
```

Resposta esperada: corpo binário PCM16LE mono a 24kHz (`application/octet-stream`).
