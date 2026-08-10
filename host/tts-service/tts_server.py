import io

import edge_tts
from fastapi import FastAPI, HTTPException, Response
from pydantic import BaseModel
from pydub import AudioSegment

app = FastAPI()

DEFAULT_VOICE = "pt-BR-FranciscaNeural"

# Mono PCM16LE, mesmo formato cru que o whisper-service espera no outro sentido —
# o app Quest toca isso direto num AudioTrack, sem decoder de MP3/Opus.
SAMPLE_RATE_HZ = 24000


class SynthesizeRequest(BaseModel):
    text: str
    voice: str = DEFAULT_VOICE
    sample_rate_hz: int = SAMPLE_RATE_HZ


@app.post("/synthesize")
async def synthesize(request: SynthesizeRequest) -> Response:
    text = request.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="texto vazio")

    try:
        mp3_bytes = bytearray()
        communicate = edge_tts.Communicate(text, voice=request.voice)
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                mp3_bytes.extend(chunk["data"])

        if not mp3_bytes:
            raise HTTPException(status_code=502, detail="edge-tts não retornou áudio")

        # edge-tts só fala MP3/Opus; decodifica e reamostra para PCM16LE mono aqui,
        # via ffmpeg (pydub), para o app não precisar de um decoder de áudio comprimido.
        audio = AudioSegment.from_file(io.BytesIO(bytes(mp3_bytes)), format="mp3")
        audio = audio.set_channels(1).set_frame_rate(request.sample_rate_hz).set_sample_width(2)
        pcm16le = audio.raw_data
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"falha na síntese: {exc}") from exc

    return Response(content=pcm16le, media_type="application/octet-stream")
