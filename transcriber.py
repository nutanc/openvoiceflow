"""
Transcriber - Uses OpenAI Whisper API for speech-to-text
"""
import io
from openai import OpenAI
from config import OPENAI_API_KEY, WHISPER_MODEL, WHISPER_LANGUAGE


class Transcriber:
    """Transcribes audio using OpenAI Whisper API"""

    def __init__(self):
        self.client = OpenAI(api_key=OPENAI_API_KEY)
        self.model = WHISPER_MODEL
        self.language = WHISPER_LANGUAGE

    def transcribe(self, wav_bytes: bytes) -> str:
        """
        Transcribe WAV audio bytes to text using OpenAI Whisper API.
        
        Args:
            wav_bytes: Raw WAV file bytes
            
        Returns:
            Transcribed text string
        """
        if not wav_bytes:
            return ""

        print("  🔄 Transcribing with OpenAI Whisper...")

        # Wrap bytes in a file-like object with a .name attribute
        audio_file = io.BytesIO(wav_bytes)
        audio_file.name = "recording.wav"

        kwargs = {
            "model": self.model,
            "file": audio_file,
            "response_format": "text",
        }
        if self.language:
            kwargs["language"] = self.language

        transcription = self.client.audio.transcriptions.create(**kwargs)

        text = transcription.strip() if isinstance(transcription, str) else transcription.text.strip()
        print(f"  📝 Raw transcription: {text}")
        return text
