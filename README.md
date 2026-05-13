# PamAssist 🩺💊
Positive Aging Mission - Offline AI medical assistant powered by Gemma 4 + Cactus

**An offline-first AI medical assistant powered by Gemma 4 E2B + Cactus, 
running entirely on-device.**

Pam is a personal health companion that helps patients manage medications, 
analyze product labels, and get personalized medical guidance — with zero 
internet dependency after setup, and full privacy by design.

**PAM Stands for Positive Aging Mission based on the care of the elderly, 
due to the loneliness faced by some of them, putting their physical and mental integrity at risk**

---

## The Problem

Millions of people, especially elderly patients, reach a stage in their lives 
where old age sometimes prevents them from carrying out everyday activities 
that include socialization. As a result, they feel isolated and begin an irreversible 
cognitive and physical decline. Pam's mission is to prevent this.
Some elderly patients, also struggle to manage complex medication 
schedules, understand product labels, and access reliable health 
information. Existing solutions either require constant internet connectivity, 
send sensitive health data to the cloud, or lack the intelligence to provide 
truly personalized guidance.

---

## The Solution

PamAssist runs a full multimodal AI pipeline entirely on-device:

- 💬 **AI Chat with Pam** — Personalized medical Q&A powered by Gemma 4 E2B,
  specialized in care, with full awareness of the patient's medical
  history and active medications
- 📷 **Product Label Scanner** — CameraX captures the label, ML Kit OCR 
  extracts the text, and Gemma 4 analyzes it against the patient's profile 
  to flag conflicts or interactions
- 💊 **Voice-guided Medication Management** — Pam asks structured questions 
  and automatically adds medications to the database via a structured 
  `<MEDICATION_ADD>` JSON protocol
- ⏰ **Offline Medication Reminders** — AlarmManager schedules Doze-mode 
  resistant notifications with zero cloud dependency
- 🎙️ **Voice Input & Output** — Full STT (Android SpeechRecognizer, es-AR) 
  and TTS (Android TextToSpeech) integration

---

## How Gemma 4 + Cactus Power PamAssist

This app uses **Gemma 4 E2B** (quantized, ~4.7GB) running locally via 
**Cactus v1.14** — a native C++ inference engine exposed to Kotlin via JNI.

### Key AI Integration Points

**1. Dynamic System Prompt Assembly**  
Before every inference call, the app builds a personalized system prompt 
that injects the patient's medical history and full medication list directly 
into Gemma's context window. This allows the model to detect potential drug 
interactions and give genuinely personalized responses without any cloud call.

**2. Token Streaming**  
Responses stream token-by-token via Cactus callbacks, giving Pam a 
natural, real-time conversational feel even on a mid-range device.

**3. Structured Output Protocol**  
When adding medications by voice, Gemma emits structured JSON wrapped in 
`<MEDICATION_ADD>` tags. The ViewModel detects this tag, parses the JSON, 
and automatically persists the medication to Room and reschedules alarms — 
a fully agentic, offline pipeline.

**4. OCR + LLM Product Analysis**  
ML Kit extracts text from product labels (preventing OOM crashes from raw 
image ingestion). That text is then sent to Gemma with the patient's profile 
for a personalized 4-sentence safety analysis.

---

## Architecture

```
┌─────────────────────────────────────┐
│         UI (Jetpack Compose)        │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│           ViewModels                │
│  ChatViewModel · ScanViewModel      │
│  MedicationsViewModel               │
└──┬─────────────┬──────────────┬─────┘
   │             │              │
┌──▼──────┐ ┌───▼────┐ ┌───────▼──────┐
│ Cactus  │ │  Room  │ │    ML Kit    │
│ Manager │ │  DB    │ │  OCR+CameraX │
│ Gemma 4 │ │        │ │              │
└──┬──────┘ └───┬────┘ └──────────────┘
   │            │
┌──▼──────┐ ┌───▼──────────┐
│Cactus.kt│ │AlarmScheduler│
│JNI─.so  │ │(offline)     │
└─────────┘ └──────────────┘
```
---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin + Jetpack Compose |
| AI SDK | Cactus v1.14 (libcactus.so) |
| Model | Gemma 4 E2B — Cactus-Compute/gemma-4-E2B-it |
| Model Download | HTTP → HuggingFace (4.7GB ZIP, auto-extracted) |
| STT | Android SpeechRecognizer (es-AR) |
| TTS | Android TextToSpeech |
| OCR | ML Kit Text Recognition |
| Camera | CameraX |
| Database | Room (SQLite) |
| Notifications | AlarmManager (Doze-mode resistant) |
| Tested Device | Samsung Galaxy A15 (4GB RAM) |

---

## Building libcactus.so

The native Cactus library is not distributed as a prebuilt binary and must 
be compiled from source. We compiled `libcactus.so` from the 
[cactuscompute/cactus](https://github.com/cactus-compute/cactus) repository 
using Google Colab.

**The full compilation notebook is included in this repo:** 
[`build_libcactus.ipynb`](./build_libcactus.ipynb)

Once compiled, place the `.so` file at:
app/src/main/jniLibs/arm64-v8a/libcactus.so

---

## Installation

1. Clone this repository
2. Compile `libcactus.so` using the included Colab notebook and place it 
   at `app/src/main/jniLibs/arm64-v8a/`
3. Open the project in Android Studio
4. Build and run on an Android device (arm64, Android 8.0+, min 4GB RAM)
5. On first launch, the app will automatically download Gemma 4 E2B (~4.7GB) 
   from HuggingFace — a one-time setup requiring internet

After the initial model download, **the app runs fully offline.**

---
