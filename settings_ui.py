"""
Settings Window - Tkinter GUI for configuring Open Wispr Flow
"""
import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext
import threading


class SettingsWindow:
    """Tkinter settings window for configuring API key, models, and system prompt"""

    def __init__(self, on_save_callback=None):
        self.on_save = on_save_callback
        self.window = None
        self._is_open = False

    def show(self, current_config: dict):
        """Open the settings window with current config values"""
        if self._is_open:
            if self.window:
                self.window.lift()
                self.window.focus_force()
            return

        self._is_open = True
        self.window = tk.Tk()
        self.window.title("Open Wispr Flow — Settings")
        self.window.geometry("700x720")
        self.window.resizable(True, True)
        self.window.protocol("WM_DELETE_WINDOW", self._on_close)

        # ── Style ──────────────────────────────────────────────
        style = ttk.Style(self.window)
        style.theme_use("clam")

        bg = "#1e1e2e"
        fg = "#cdd6f4"
        accent = "#89b4fa"
        entry_bg = "#313244"
        entry_fg = "#cdd6f4"
        btn_bg = "#89b4fa"
        btn_fg = "#1e1e2e"

        self.window.configure(bg=bg)
        style.configure("TFrame", background=bg)
        style.configure("TLabel", background=bg, foreground=fg, font=("Segoe UI", 10))
        style.configure("Header.TLabel", background=bg, foreground=accent, font=("Segoe UI", 14, "bold"))
        style.configure("Section.TLabel", background=bg, foreground=accent, font=("Segoe UI", 11, "bold"))
        style.configure("TButton", font=("Segoe UI", 10, "bold"))
        style.configure("TCombobox", font=("Segoe UI", 10))

        # Main frame with padding
        main = ttk.Frame(self.window, padding=20)
        main.pack(fill=tk.BOTH, expand=True)

        # ── Header ─────────────────────────────────────────────
        ttk.Label(main, text="⚙️  Open Wispr Flow Settings", style="Header.TLabel").pack(anchor="w", pady=(0, 15))

        # ── API Key ────────────────────────────────────────────
        ttk.Label(main, text="OpenAI API Key", style="Section.TLabel").pack(anchor="w", pady=(5, 2))
        self.api_key_var = tk.StringVar(value=current_config.get("OPENAI_API_KEY", ""))
        api_frame = ttk.Frame(main)
        api_frame.pack(fill=tk.X, pady=(0, 10))
        self.api_entry = tk.Entry(
            api_frame, textvariable=self.api_key_var, show="•",
            font=("Consolas", 11), bg=entry_bg, fg=entry_fg,
            insertbackground=fg, relief="flat", bd=5
        )
        self.api_entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
        self._show_key = False
        self.toggle_btn = tk.Button(
            api_frame, text="👁", command=self._toggle_key_visibility,
            bg=entry_bg, fg=fg, relief="flat", font=("Segoe UI", 10), width=3
        )
        self.toggle_btn.pack(side=tk.RIGHT, padx=(5, 0))

        # ── Model Settings ─────────────────────────────────────
        ttk.Label(main, text="Model Settings", style="Section.TLabel").pack(anchor="w", pady=(10, 2))

        model_frame = ttk.Frame(main)
        model_frame.pack(fill=tk.X, pady=(0, 10))

        # GPT Model
        ttk.Label(model_frame, text="GPT Model:").grid(row=0, column=0, sticky="w", padx=(0, 10), pady=3)
        self.gpt_model_var = tk.StringVar(value=current_config.get("GPT_MODEL", "gpt-4o-mini"))
        gpt_combo = ttk.Combobox(
            model_frame, textvariable=self.gpt_model_var,
            values=["gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1-nano", "gpt-3.5-turbo"],
            state="readonly", width=20
        )
        gpt_combo.grid(row=0, column=1, sticky="w", pady=3)

        # Whisper Model
        ttk.Label(model_frame, text="Whisper Model:").grid(row=1, column=0, sticky="w", padx=(0, 10), pady=3)
        self.whisper_model_var = tk.StringVar(value=current_config.get("WHISPER_MODEL", "whisper-1"))
        whisper_combo = ttk.Combobox(
            model_frame, textvariable=self.whisper_model_var,
            values=["whisper-1"],
            state="readonly", width=20
        )
        whisper_combo.grid(row=1, column=1, sticky="w", pady=3)

        # Language
        ttk.Label(model_frame, text="Language:").grid(row=2, column=0, sticky="w", padx=(0, 10), pady=3)
        self.language_var = tk.StringVar(value=current_config.get("WHISPER_LANGUAGE", "en"))
        lang_combo = ttk.Combobox(
            model_frame, textvariable=self.language_var,
            values=["en", "es", "fr", "de", "it", "pt", "ja", "zh", "ko", "hi", "ar"],
            width=20
        )
        lang_combo.grid(row=2, column=1, sticky="w", pady=3)

        # Temperature
        ttk.Label(model_frame, text="Temperature:").grid(row=3, column=0, sticky="w", padx=(0, 10), pady=3)
        self.temp_var = tk.StringVar(value=str(current_config.get("GPT_TEMPERATURE", 0.3)))
        temp_entry = tk.Entry(
            model_frame, textvariable=self.temp_var,
            font=("Consolas", 10), bg=entry_bg, fg=entry_fg,
            insertbackground=fg, relief="flat", bd=3, width=10
        )
        temp_entry.grid(row=3, column=1, sticky="w", pady=3)

        # ── Hotkey Settings ────────────────────────────────────
        ttk.Label(main, text="Hotkey & Recording", style="Section.TLabel").pack(anchor="w", pady=(10, 2))

        hotkey_frame = ttk.Frame(main)
        hotkey_frame.pack(fill=tk.X, pady=(0, 10))

        ttk.Label(hotkey_frame, text="Record Mode:").grid(row=0, column=0, sticky="w", padx=(0, 10), pady=3)
        self.record_mode_var = tk.StringVar(value=current_config.get("RECORD_MODE", "push_to_talk"))
        mode_combo = ttk.Combobox(
            hotkey_frame, textvariable=self.record_mode_var,
            values=["push_to_talk", "toggle"],
            state="readonly", width=20
        )
        mode_combo.grid(row=0, column=1, sticky="w", pady=3)

        ttk.Label(hotkey_frame, text="Hotkey:").grid(row=1, column=0, sticky="w", padx=(0, 10), pady=3)
        ttk.Label(hotkey_frame, text="Ctrl + Win", foreground="#a6adc8").grid(row=1, column=1, sticky="w", pady=3)

        # ── System Prompt ──────────────────────────────────────
        ttk.Label(main, text="System Prompt", style="Section.TLabel").pack(anchor="w", pady=(10, 2))

        self.prompt_text = scrolledtext.ScrolledText(
            main, height=10, wrap=tk.WORD,
            font=("Consolas", 9), bg=entry_bg, fg=entry_fg,
            insertbackground=fg, relief="flat", bd=5
        )
        self.prompt_text.pack(fill=tk.BOTH, expand=True, pady=(0, 15))
        self.prompt_text.insert("1.0", current_config.get("SYSTEM_PROMPT", ""))

        # ── Buttons ────────────────────────────────────────────
        btn_frame = ttk.Frame(main)
        btn_frame.pack(fill=tk.X)

        save_btn = tk.Button(
            btn_frame, text="💾  Save & Apply", command=self._save,
            bg=btn_bg, fg=btn_fg, font=("Segoe UI", 11, "bold"),
            relief="flat", bd=0, padx=20, pady=8, cursor="hand2"
        )
        save_btn.pack(side=tk.RIGHT, padx=(10, 0))

        cancel_btn = tk.Button(
            btn_frame, text="Cancel", command=self._on_close,
            bg="#45475a", fg=fg, font=("Segoe UI", 10),
            relief="flat", bd=0, padx=15, pady=8, cursor="hand2"
        )
        cancel_btn.pack(side=tk.RIGHT)

        # Center window on screen
        self.window.update_idletasks()
        w = self.window.winfo_width()
        h = self.window.winfo_height()
        x = (self.window.winfo_screenwidth() // 2) - (w // 2)
        y = (self.window.winfo_screenheight() // 2) - (h // 2)
        self.window.geometry(f"+{x}+{y}")

        self.window.mainloop()

    def _toggle_key_visibility(self):
        self._show_key = not self._show_key
        self.api_entry.config(show="" if self._show_key else "•")
        self.toggle_btn.config(text="🔒" if self._show_key else "👁")

    def _save(self):
        api_key = self.api_key_var.get().strip()
        if not api_key:
            messagebox.showwarning("Missing API Key", "Please enter your OpenAI API key.")
            return

        try:
            temp = float(self.temp_var.get())
            if not (0 <= temp <= 2):
                raise ValueError
        except ValueError:
            messagebox.showwarning("Invalid Temperature", "Temperature must be a number between 0 and 2.")
            return

        config = {
            "OPENAI_API_KEY": api_key,
            "GPT_MODEL": self.gpt_model_var.get(),
            "WHISPER_MODEL": self.whisper_model_var.get(),
            "WHISPER_LANGUAGE": self.language_var.get(),
            "GPT_TEMPERATURE": temp,
            "RECORD_MODE": self.record_mode_var.get(),
            "SYSTEM_PROMPT": self.prompt_text.get("1.0", tk.END).strip(),
        }

        if self.on_save:
            self.on_save(config)

        messagebox.showinfo("Saved", "Settings saved! Changes will apply to the next recording.")
        self._on_close()

    def _on_close(self):
        self._is_open = False
        if self.window:
            self.window.destroy()
            self.window = None
