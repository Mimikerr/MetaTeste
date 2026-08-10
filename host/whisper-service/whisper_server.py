import numpy as np
from fastapi import FastAPI, HTTPException, UploadFile
from faster_whisper import WhisperModel

app = FastAPI()

# Carregado uma única vez no startup do processo, não a cada request.
# device="cpu": a GPU desta máquina é AMD (RX 6600), e o CTranslate2 (usado pelo
# faster-whisper) só acelera por GPU em placas NVIDIA/CUDA. int8 é o compute_type
# recomendado para CPU — bom equilíbrio entre velocidade e precisão.
model = WhisperModel("small", device="cpu", compute_type="int8")

# O host sempre envia PCM16LE mono cru (sem cabeçalho WAV) a 16kHz — é o único
# sample rate que o app Quest emite hoje (VoiceAudio.sampleRateHz default = 16000).
SAMPLE_RATE_HZ = 16000


@app.post("/transcribe")
async def transcribe(file: UploadFile) -> dict[str, str]:
    pcm16le = await file.read()
    if not pcm16le:
        raise HTTPException(status_code=400, detail="áudio vazio")

    try:
        audio = np.frombuffer(pcm16le, dtype=np.int16).astype(np.float32) / 32768.0
        segments, _ = model.transcribe(audio, language="pt")
        text = "".join(segment.text for segment in segments).strip()
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"falha na transcrição: {exc}") from exc

    return {"text": text}
