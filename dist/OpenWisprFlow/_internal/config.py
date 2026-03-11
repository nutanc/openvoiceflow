"""
Configuration for Open Wispr Flow (Cloud Edition)
"""
import os
import json

# ── API Configuration ──────────────────────────────────────────────
OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "")

# ── Whisper ASR Settings ───────────────────────────────────────────
WHISPER_MODEL = "whisper-1"  # OpenAI Whisper API model
WHISPER_LANGUAGE = "en"      # Language hint (None for auto-detect)

# ── GPT Settings ───────────────────────────────────────────────────
GPT_MODEL = "gpt-4o-mini"   # Fast & cheap, good for corrections
GPT_TEMPERATURE = 0.3       # Low temperature for faithful corrections
GPT_MAX_TOKENS = 1024

# ── Audio Settings ─────────────────────────────────────────────────
SAMPLE_RATE = 16000          # 16kHz mono for Whisper
CHANNELS = 1
DTYPE = "int16"              # 16-bit PCM

# ── Hotkey Settings ────────────────────────────────────────────────
# Push-to-talk hotkey (hold to record, release to process)
HOTKEY_COMBINATION = "<ctrl>+<shift>+space"

# ── Recording Mode ─────────────────────────────────────────────────
# "push_to_talk" = hold key to record, release to stop
# "toggle"       = press once to start, press again to stop
RECORD_MODE = "push_to_talk"

# ── System Prompt for LLM Correction ──────────────────────────────
SYSTEM_PROMPT = """You are a transcription correction assistant. 
The user dictated text using voice-to-text while working in the application: "{window_title}".

Your job:
1. Fix any speech recognition errors, mishearings, or garbled words
2. Add proper punctuation and capitalization
3. Fix grammar issues introduced by speech recognition
4. Keep the original meaning and intent exactly as spoken
5. Remove filler words like "um", "uh", "like", "you know" if present
6. If the context suggests technical terms (e.g., coding in VS Code), 
   use the correct technical spelling

IMPORTANT: Output ONLY the corrected text. No explanations, no quotes, 
no prefixes like "Here's the corrected text:". Just the clean text."""

# ── Config File Path ───────────────────────────────────────────────
CONFIG_DIR = os.path.join(os.path.expanduser("~"), ".open_wispr_flow")
CONFIG_FILE = os.path.join(CONFIG_DIR, "config.json")


def load_config():
    """Load config from file, falling back to defaults"""
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE, "r") as f:
            saved = json.load(f)
            # Override module-level vars
            g = globals()
            for key, value in saved.items():
                if key in g:
                    g[key] = value
    return {
        "OPENAI_API_KEY": OPENAI_API_KEY,
        "WHISPER_MODEL": WHISPER_MODEL,
        "GPT_MODEL": GPT_MODEL,
        "HOTKEY_COMBINATION": HOTKEY_COMBINATION,
        "RECORD_MODE": RECORD_MODE,
        "SAMPLE_RATE": SAMPLE_RATE,
    }


def save_config(overrides: dict):
    """Save config overrides to file"""
    os.makedirs(CONFIG_DIR, exist_ok=True)
    existing = {}
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE, "r") as f:
            existing = json.load(f)
    existing.update(overrides)
    with open(CONFIG_FILE, "w") as f:
        json.dump(existing, f, indent=2)
