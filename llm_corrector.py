"""
LLM Corrector - Uses OpenAI GPT to fix transcription errors with window context
"""
from openai import OpenAI
from config import OPENAI_API_KEY, GPT_MODEL, GPT_TEMPERATURE, GPT_MAX_TOKENS, SYSTEM_PROMPT


class LLMCorrector:
    """Corrects transcription using OpenAI GPT with active window context"""

    def __init__(self):
        self.client = OpenAI(api_key=OPENAI_API_KEY)
        self.model = GPT_MODEL

    def correct(self, raw_text: str, window_title: str) -> str:
        """
        Send raw transcription + window context to GPT for correction.
        
        Args:
            raw_text: Raw transcription from Whisper
            window_title: Title of the active window (provides context)
            
        Returns:
            Corrected text
        """
        if not raw_text.strip():
            return ""

        print(f"  🧠 Correcting with {self.model} (context: {window_title})...")

        system_msg = SYSTEM_PROMPT.format(window_title=window_title)

        response = self.client.chat.completions.create(
            model=self.model,
            temperature=GPT_TEMPERATURE,
            max_tokens=GPT_MAX_TOKENS,
            messages=[
                {"role": "system", "content": system_msg},
                {"role": "user", "content": raw_text},
            ],
        )

        corrected = response.choices[0].message.content.strip()
        print(f"  ✨ Corrected: {corrected}")
        return corrected
