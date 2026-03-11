"""
Audio Recorder - Records from microphone and saves as WAV for OpenAI Whisper API
"""
import io
import wave
import threading
import numpy as np
import sounddevice as sd
from config import SAMPLE_RATE, CHANNELS


class AudioRecorder:
    """Records audio from the default microphone"""

    def __init__(self, sample_rate: int = SAMPLE_RATE, channels: int = CHANNELS):
        self.sample_rate = sample_rate
        self.channels = channels
        self.is_recording = False
        self._audio_chunks: list[np.ndarray] = []
        self._stream = None
        self._lock = threading.Lock()

    def _audio_callback(self, indata, frames, time_info, status):
        if status:
            print(f"  ⚠ Audio status: {status}")
        if self.is_recording:
            self._audio_chunks.append(indata.copy())

    def start(self):
        """Start recording audio"""
        with self._lock:
            if self.is_recording:
                return
            self._audio_chunks = []
            self.is_recording = True
            self._stream = sd.InputStream(
                samplerate=self.sample_rate,
                channels=self.channels,
                dtype="int16",
                callback=self._audio_callback,
            )
            self._stream.start()
            print("  🎙️  Recording... (speak now)")

    def stop(self) -> bytes:
        """Stop recording and return WAV bytes suitable for OpenAI API"""
        with self._lock:
            if not self.is_recording:
                return b""
            self.is_recording = False
            if self._stream:
                self._stream.stop()
                self._stream.close()
                self._stream = None
            print("  ✅ Recording stopped")

        if not self._audio_chunks:
            return b""

        audio_data = np.concatenate(self._audio_chunks, axis=0)

        # Convert to WAV bytes in-memory
        wav_buffer = io.BytesIO()
        with wave.open(wav_buffer, "wb") as wf:
            wf.setnchannels(self.channels)
            wf.setsampwidth(2)  # 16-bit = 2 bytes
            wf.setframerate(self.sample_rate)
            wf.writeframes(audio_data.tobytes())

        wav_buffer.seek(0)
        return wav_buffer.read()

    def record_for_duration(self, duration: float) -> bytes:
        """Record for a fixed duration (seconds) and return WAV bytes"""
        self.start()
        sd.sleep(int(duration * 1000))
        return self.stop()
