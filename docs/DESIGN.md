# The Elizabeth App: Design

**Status:** Phase 1 in progress (see the README for what is built) · **Date:** 26 September 2026

An Android tablet app that lets someone who is losing her speech communicate quickly by
selecting words and phrases, or by typing. Her messages are spoken aloud in a voice she
chooses, ideally her own. It is designed for a user who:

- has full mental capacity and knows exactly what she wants to say,
- can touch the screen but inaccurately, and whose motor control will keep declining,
- lives in the UK/EU, so GDPR, Privacy by Design, product safety rules and the AI Act apply.

This document says what the app would do and how. It is not legal advice. The regulatory
sections list the questions to settle before the app is given to anyone outside the family.

---

## 1. Design principles

1. **Speed of communication comes first.** Common messages should take one press, and any
   sentence should need as few presses as possible.
2. **A wrong press costs one press to fix.** Accidental presses are filtered out, and there
   is always a large Undo button.
3. **Adapt as her abilities change.** Touch handling, button size and selection method can
   all be changed, and there is a planned route from touch, to dwell, to switch scanning.
4. **Nothing leaves the tablet.** The app has no internet permission at all, so anyone can
   check that it cannot send data anywhere.
5. **All AI runs on the tablet and is open source.** Speech synthesis, voice cloning and
   word prediction run locally on open models. No US cloud AI is used.
6. **She stays in control of her data.** She can see, export and erase everything the app
   has learned. Learning can be switched off.
7. **Fail safe.** If speech fails, her message is shown in large text. The emergency phrase
   and attention call always work.

---

## 2. What she sees

The main screen is a fixed landscape layout. It never scrolls, because scrolling and
swiping are hard with limited motor control. Where there is more content, large
**Next page** / **Back** buttons move between pages instead.

```
┌──────────────────────────────────────────────────────────────────────────┐
│  MESSAGE:  Could I have a cup of tea please_                             │
│  [ SPEAK ]      [ UNDO ]   [ ⌫ word ]   [ CLEAR ]   [ 🔔 ATTENTION ]    │
├──────────────────────────────────────────────────────────────────────────┤
│  PREDICTIONS:  [ please ]  [ now ]  [ with ]  [ and ]  [ thank you ]     │
├───────────┬──────────────────────────────────────────────────────────────┤
│  YES      │   Category tabs: [Needs] [Feelings] [People] [Chat] [ABC]    │
│  NO       │  ┌──────────┬──────────┬──────────┬──────────┐               │
│  WAIT     │  │ I need   │ Drink    │ Toilet   │ Pain     │               │
│  HELP     │  │ help     │ please   │ please   │          │               │
│  THANK    │  ├──────────┼──────────┼──────────┼──────────┤               │
│  YOU      │  │ Move me  │ Too hot  │ Too cold │ Tired    │               │
│           │  ├──────────┼──────────┼──────────┼──────────┤               │
│           │  │ ◀ Back   │ Recent   │ Favourite│ Next ▶   │               │
└───────────┴──┴──────────┴──────────┴──────────┴──────────┘───────────────┘
```

- **Quick replies (left column):** Yes, No, Wait, Help and Thank you are always visible
  and are spoken with one press, without going through the message bar.
- **Message bar:** very large text. Pressing a phrase or word adds it here, and SPEAK says
  it. There is an optional mode where a phrase is spoken as soon as it is pressed.
- **Phrase pages:** phrases grouped by topic (needs, feelings, people, small talk, medical).
  Family and carers can edit these from Settings.
- **ABC page:** a large keyboard, alphabetical or QWERTY, with 4 to 6 rows of big keys and
  the predictions row above it.
- **Recent and Favourites:** everything she has said recently is one press away, because
  people repeat themselves.
- **Attention:** plays a chime or bell sound to call someone. It does not need speech to
  be working, and can be set to play repeatedly until it is dismissed.
- **Listener view:** optionally shows the message in large text at the top of the screen,
  upside down, for someone sitting opposite her.
- **Settings:** opened by holding the gear icon for 3 seconds, optionally with a carer PIN,
  so it cannot be opened by accident.

The settings, voice and privacy screens are used by carers. They use normal Android
controls and follow WCAG 2.2 AA.

---

## 3. Touch handling for inaccurate and declining motor control

This is the core of the app. Buttons do not respond to taps in the usual Android way.
Instead, **one touch layer covers the whole screen** and decides which button she meant.

### 3.1 How a touch becomes a press

| Mechanism | What it does | Why |
|---|---|---|
| **Snap to nearest** | A touch in the gap between buttons goes to the nearest button within a set radius (e.g. 24 dp). | Near misses still count. |
| **Select on release, slide to correct** (default) | Whichever button is under the finger when it lifts is pressed. She can land badly and slide to the right button. Highlighting shows which button will be pressed. | Aiming is replaced by adjusting. |
| **Slip grace** | Slipping onto a neighbouring button for less than about 150 ms is ignored. | Stops tremor at the moment of lifting from pressing the wrong button. |
| **Minimum hold** | The finger must rest for at least N ms, adjustable from 0 to 2000. | Filters out brushes and knuckle bumps. |
| **Repeat guard** | For about 450 ms after a press, further presses are ignored. | Stops a bounce or tremor pressing a button twice. |
| **One finger only** | Only the first finger down is tracked, and other contacts are ignored. | Palm and wrist rejection. |
| **Dwell mode** | Resting on a button for a set time presses it, with no lift needed. A filling ring shows the progress. | For when lifting accurately gets hard. |
| **First-contact mode** | Presses as soon as the finger lands. | Fastest, while her accuracy is still good. |
| **No gestures** | No swipes, long-presses, pinches or scrolling are needed anywhere. | These actions deteriorate first. |

This logic would live in a pure-Kotlin module with no Android code, so it can be
unit-tested thoroughly with recorded touch sequences.

### 3.2 Layout adapts too

- The grid size can be set from 2×2 up to 5×6. Fewer, larger buttons are easier to hit
  but need more pages.
- Spacing between buttons and high-contrast colour schemes are adjustable. Very
  important buttons, such as Speak and Undo, are placed at the screen edges, which are
  the easiest places to hit.
- The layout stays the same from day to day, so she can use motor memory. Buttons never
  move on their own. Predictions only appear in the prediction row.

### 3.3 Planned progression as her abilities decline

1. **Touch** with the filters above. Settings get stricter over time.
2. **Physical keyguard:** a laser-cut acrylic overlay with a hole for each button. This is
   standard practice in AAC. The app would export a printable or cuttable keyguard
   template that matches its current grid.
3. **Dwell** selection.
4. **Switch scanning:** one or two large buttons (USB or Bluetooth, which appear to
   Android as a keyboard) step through the rows and then the buttons. The app would
   include its own scanning mode tuned for AAC, and would also work with Android's
   built-in Switch Access.
5. **Eye gaze** is poorly supported on Android tablets. If she reaches this stage, a
   dedicated AAC device would be needed. The phrase data and her voice model would be
   exportable in open formats so they can be carried over.

### 3.4 Help with adapting

With her consent, the app would keep simple on-device statistics: how often she presses
Undo straight after a press, and how often touches land in gaps. It would then **suggest**
settings changes, such as "increase slip grace", for a carer to approve. It
never changes settings silently, because that would break her motor memory.

---

## 4. Speaking faster: prediction

| Feature | How it works |
|---|---|
| **Next-word prediction** | An on-device trigram model starts with a British English vocabulary list and learns from the messages she actually speaks. Her own words, such as family names, soon rank highest. |
| **Word completion** | After one or two letters, the prediction row offers matching words. |
| **Phrase prediction** | Recent and frequent whole messages are offered, based on time of day and what she has already typed. |
| **Optional on-device phrase expansion** (later phase, off by default) | A small open-weights language model runs locally through llama.cpp, for example a Mistral model (French company, Apache-2.0). It turns "tea hot" into "Could I have a hot cup of tea, please?" as a *suggestion* that she accepts or ignores. It needs a tablet with about 8 GB of RAM. |

The word model is deliberately simple. It is transparent, it can be exported as a
readable text file, a single word can be removed ("stop suggesting this name"), and it can
be erased completely. Learning can be paused, for example during a private conversation.

---

## 5. Voice

### 5.1 Speech engines

The app would talk to speech engines through one internal interface, with two
implementations:

1. **Embedded sherpa-onnx** (Apache-2.0, from the k2-fsa project) runs **Piper/VITS voice
   models** (ONNX files) directly in the app. This is the main route. It is fully offline,
   open source, and can load a custom model of her own voice.
2. **Android system text-to-speech**, as a fallback. It is restricted to voices that do
   not need a network connection (`Voice.isNetworkConnectionRequired == false`, with
   network synthesis disabled).

Rate, pitch and volume are adjustable. Voices are tested on the tablet itself, because
listeners need to understand her in noisy rooms.

### 5.2 Choosing a voice

- **Stock open voices:** Piper's British English voices. Each voice's licence and the
  dataset it was trained on would be checked before it is bundled.
- **Her own voice, from voice banking.** See 5.3.
- **A donor voice:** a family member or friend with a similar accent records the script.
  This needs their recorded, informed consent (see §7.4).

### 5.3 Cloning her voice

This depends on how much of her voice is left. **Recording should start as soon as possible,
before the app exists if necessary.** A decent USB microphone and a quiet, soft-furnished
room are enough.

**Step A. Message banking (do this first).** She records whole phrases in her own voice,
such as "I love you", her catchphrases, or family names said the way she says them.
These are played back as they are, with no AI involved. In the app, any phrase button can
be linked to a recording. This preserves her real voice and personality even if cloning
never works well, and it is often what families value most.

**Step B. Voice banking.** She reads a script in the app, one sentence at a time.

- The script is a phonetically varied set of about 300 to 1,000 sentences, written for the
  project rather than copied from copyrighted text. It is split into short sessions of
  about 10 minutes, because speaking tires her.
- The app checks each take on the tablet for clipping, background noise, volume and
  silence at the edges, and asks for a re-take if needed.
- Audio is recorded as 16-bit mono WAV at 22.05 kHz, the format Piper trains on, and is
  **encrypted on the tablet** (see §6).
- Recording from about 20 minutes of good audio is useful. One to two hours gives a
  noticeably better voice.

**Step C. Training.** Training needs a GPU, so it cannot run on the tablet.

1. The carer exports an **encrypted, passphrase-protected archive** of the recordings and
   script over USB to a **trusted local computer** (Linux with an NVIDIA GPU, owned by the
   family). A GPU server located in the EU, run under a data
   processing agreement, is a fallback.
2. On that computer, an open-source Piper training script fine-tunes an existing British
   English Piper model on her recordings. This takes a few hours of GPU time.
3. The output is a single ONNX voice model (about 20 to 60 MB) plus a config file, along
   with a manifest recording its SHA-256 hash, its source and the date of consent.
4. The model is copied back to the tablet by USB. The app checks the hash, shows who the
   voice belongs to, and then offers it as a voice option.
5. The raw recordings are deleted from the training computer. The app keeps its encrypted
   copy, so the voice can be retrained later with better tools.

The project would include the training pipeline (scripts, pinned versions and step-by-step
instructions) so that a technically capable family member can run it.

**If her voice is already affected:**

- Older recordings of her can be used, such as videos, voicemails or speeches. They would
  be cleaned up with open-source denoising on the same local computer.
- **Zero-shot cloning** models can imitate a voice from about a minute of audio. Open
  examples include F5-TTS and OpenVoice. They are too heavy to run live on a tablet, and
  some have **non-commercial** model licences, which may be acceptable for private use but
  must be checked. On the local computer, they could generate extra training material for
  a Piper voice.
- Commercial voice banking services (for example SpeakUnique, which is UK-based, or
  ModelTalker) are an alternative. However, her recordings would be processed on their
  servers. That is her choice to make, and it would need a documented legal basis.
  It is not the default.

---

## 6. Privacy and security design

### 6.1 Data held (all on the tablet)

| Data | Why | Protection | Retention |
|---|---|---|---|
| Phrases, categories, settings | Core function | Encrypted | Until edited or erased |
| Message history (last N messages) | Recent and repeat | Encrypted; history can be switched off | Rolling window (default 200 messages) |
| Learned word model | Prediction | Encrypted; readable export; single-word and full erase | Until erased |
| Voice recordings (message and voice banking) | Her voice | Encrypted; exported only when she or a carer explicitly asks | Kept until erased (valuable) |
| Voice model(s) | Speaking in her voice | Integrity-checked; consent record attached | Until removed |
| Consent records and privacy log | Accountability | Tamper-evident hash chain | Life of the app |

Her messages will contain **health data**, which is special category data under GDPR
Article 9. Her voice recordings and voice model are closely linked to her identity. All of
this is treated with the highest level of protection, whatever its strict legal
classification.

### 6.2 Controls

- **No `INTERNET` permission in the manifest.** This is the key guarantee, because it can
  be checked independently (for example with `aapt dump permissions`). Without it, the
  app cannot send data. There is no analytics, no cloud crash reporting and no
  advertising ID.
- **Minimum permissions:** only `RECORD_AUDIO`, requested when banking starts. Files are
  imported and exported through Android's system file picker, so no storage permission
  is needed.
- **Encryption at rest:** AES-256-GCM, with keys held in the **Android Keystore**
  (hardware-backed where the tablet supports it). This is in addition to Android's own
  full-disk encryption.
- **Backups disabled** (`allowBackup=false` plus data-extraction rules), so no copy goes to
  a Google cloud backup. Carers make encrypted exports explicitly instead.
- **Voice model files are treated as untrusted input.** Only formats the app expects are
  loaded, hashes are checked against the manifest, and nothing is executed from them.
- **Settings protection:** a hold-to-open gesture and an optional carer PIN.
- **Supply chain:** few dependencies; pinned versions with Gradle dependency
  verification; a published SBOM; reproducible builds; all code open source.
  Distribution would be a signed APK installed directly, or through F-Droid, rather than
  a store that adds its own tracking libraries.
- **Tamper-evident privacy log:** privacy-relevant events (learning switched on or off,
  export, erase, voice added, consent given) are logged on the tablet. Each entry includes
  a hash of the previous one, so later changes to the log can be detected.

### 6.3 Her rights, built into the interface

- **See:** a "What the app knows" screen, in plain English.
- **Export (access and portability):** one encrypted ZIP of JSON, text and WAV files in
  open formats.
- **Erase:** delete learned words, history, recordings, or everything. Each is a separate
  option, confirmed with a hold.
- **Object or restrict:** learning and history can each be switched off.

### 6.4 Threat model (summary)

| Threat | Mitigation |
|---|---|
| Tablet lost or stolen | Device screen lock (recommended at setup), full-disk encryption, and app data encrypted with Keystore keys |
| Other apps reading the data | Android app sandbox, no exported components, no shared storage |
| Data sent over the network | No `INTERNET` permission |
| Her voice misused to make deepfakes | No export of synthesised audio; voice model marked with its owner and consent; cloning only with a consent record |
| A malicious or altered voice model | Hash-checked manifest; a strict parser |
| A compromised dependency | Minimal, pinned and verified dependencies; SBOM; reproducible builds |
| Someone in the room changing settings | Hold-to-open settings and a PIN |

---

## 7. Regulatory map (UK and EU)

*These are points to confirm with a specialist before the app is distributed to anyone
other than her.*

### 7.1 GDPR / UK GDPR and Privacy by Design (Art. 25)

- If only Elizabeth and her family use the app on her own tablet, the **household
  exemption** (Art. 2(2)(c)) probably applies to her use. Because the app processes no
  data on the developer's systems, **the developer is not a controller** of her data. This
  is the strongest position available, and it follows directly from the design choice
  of having no network access.
- If paid carers use it as part of their work, they may become
  controllers or processors of what they enter or see. Their organisation's policies
  then apply.
- A **DPIA** (Art. 35) is recommended as good practice and would be included in the repo,
  because the app handles health data and voice data.
- **Donor voices and family names:** recording someone else's voice needs their informed,
  written consent, which is stored with the voice.

### 7.2 EU AI Act

- Text-to-speech, voice cloning and n-gram prediction are **not prohibited practices** and
  are **not high-risk** under Annex III.
- **Article 50(2)** requires providers of systems that generate synthetic audio to mark
  it in a machine-readable way. This obligation applies from 2 August 2026, so it
  should be checked against the latest guidance, codes of practice and any Digital Omnibus
  changes. The design reduces the exposure:
  - Speech is played **live through the speaker only**. There is no export of synthesised
    audio files.
  - If audio export is ever added, it would include an open-source audio watermark and
    metadata marking it as synthetic.
- **Article 50(4)** deepfake disclosure applies to *deployers*, and purely personal,
  non-professional use is excluded. Even so, the app would offer an optional introduction
  phrase: "I'm using a computer voice made from my own voice."
- **Article 4 (AI literacy):** plain-English documentation for her and her carers explains
  what the voice model and prediction do and cannot do.
- The UK has no equivalent AI statute. Existing UK GDPR and product law apply.

### 7.3 Product safety and medical devices

- **Medical devices (EU MDR 2017/745; UK MDR 2002):** software whose intended purpose is to
  *compensate for a disability* can count as a medical device, probably **Class I**. A
  private project given free to one person is arguably not "placed on the market". Wider
  distribution, especially commercial, would likely need MDR/UKCA conformity: a quality
  system, risk management to ISO 14971, and software lifecycle processes to IEC 62304.
  Designing along those lines from the start keeps that route open, and it is good
  practice anyway.
- **General Product Safety Regulation (EU) 2023/988:** applies to consumer products if the
  app is supplied as one. The design supports this with its fail-safe behaviour and
  risk analysis.
- **Cyber Resilience Act:** applies to products with digital elements placed on the market.
  The no-network design and SBOM help here.
- **Accessibility:** the carer screens follow EN 301 549 and WCAG 2.2 AA. The main screen
  goes well beyond them for motor accessibility.

### 7.4 Safety requirements from the risk analysis

- The app is **not an emergency alarm**, and this is stated clearly. It complements a
  pendant or telecare alarm and does not replace it.
- If speech fails, the message appears full-screen in large text, and the attention chime
  still works.
- Warnings are shown for low battery and for the volume being turned down or muted.
- A **paper communication board** printed from the app's own phrases is kept as a backup.
- The screen is kept on while the app is in use, and the app restarts on its own if it
  crashes.

---

## 8. Architecture

```
┌──────────────────────────── Android app (Kotlin, Jetpack Compose) ───────────────────────────┐
│  UI: CommunicationScreen · Keyboard · PhrasePages · Settings · VoiceBanking · Privacy        │
│      └── TouchSurface (one full-screen touch layer; buttons only register where they are)    │
│  Speech: SpeechEngine ─┬─ SherpaOnnxEngine (Piper/VITS .onnx, on-device)                      │
│                        ├─ SystemTtsEngine (offline voices only)                               │
│                        └─ ClipPlayer (message-banked recordings)                              │
│  Audio: Recorder + on-device quality checks (WAV 22.05 kHz)                                   │
│  Storage: EncryptedStore (AES-GCM, Android Keystore) · Export/Import through the file picker │
├──────────────────────────── core (pure Kotlin, unit-tested on any JVM) ─────────────────────┤
│  touch/   TouchFilter, TargetRegistry (snap, slip grace, dwell, repeat guard)               │
│  text/    MessageEditor (insert word/phrase, backspace, undo stack)                         │
│  predict/ WordPredictor (trigram, learn, forget, export/import), PhrasePredictor            │
│  model/   PhraseBoard, Settings, VoiceProfile + VoiceConsent                                │
│  privacy/ PrivacyLog (hash chain), DataExport                                               │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
        Off-device, optional, run by a trusted person:  tools/voice-training/ (Piper fine-tuning)
```

- **Language and UI:** Kotlin and Jetpack Compose, minimum Android 8 (API 26), landscape
  tablet layout.
- **Testing:** the `core` module is unit-tested with recorded touch sequences (tremor,
  slips and palm contacts) and prediction scenarios. The UI is tested with Compose tests,
  and the app is checked in usability sessions with Elizabeth and her family.
- **CI:** GitHub Actions builds the app, runs the tests, checks that no `INTERNET`
  permission is present (the build fails if it is), checks licences and generates the
  SBOM.

### Recommended hardware

- A 10 to 11 inch Android tablet with 6 to 8 GB of RAM, and a supported update lifetime
  of at least 3 years.
- A firm mount or stand at the right angle for her arm. Positioning improves accuracy as
  much as software does.
- A small powered speaker (wired or Bluetooth) if the tablet's own speaker is too quiet.
- A keyguard (see §3.3) and, later, one or two large accessibility switches.

---

## 9. Phased delivery

| Phase | Delivers | Why this order |
|---|---|---|
| **0 (now, no app needed)** | Message banking and early voice banking with a good microphone | Her voice is time-critical. Everything else can wait. |
| **1: MVP** | Main screen, quick replies, phrase pages, keyboard, touch filtering, undo, word prediction, offline system TTS, playback of message-banked clips, encrypted storage, export and erase | Useful communication as soon as possible |
| **2: Her voice** | In-app voice banking with quality checks, the Piper training pipeline, the embedded sherpa-onnx engine, voice consent records | Speak in her own voice |
| **3: Adaptation** | Dwell refinements, switch scanning, keyguard templates, settings suggestions, listener view | Keep her communicating as her abilities decline |
| **4: Optional** | On-device phrase expansion with an open-weights LLM; regulatory file (DPIA, risk file) if the app is distributed more widely | Extra speed; wider use |

---

## 10. Open questions for the family and care team

1. What is her diagnosis and expected rate of decline? This decides how urgent voice
   banking is, and how soon dwell or switch access will be needed.
2. How much of her voice is left now? Are there older recordings of her (videos,
   voicemails)?
3. Would she prefer an alphabetical or QWERTY keyboard? Did she use a computer or
   typewriter?
4. Which tablet will she use, or is there a budget for one?
5. Is a trusted computer with an NVIDIA GPU available for training her voice? If not, would
   a GPU server located in the EU under a data processing agreement be acceptable to her?
6. Are there other languages she needs apart from English?
7. Is the app only for her, or might it be shared more widely later? This decides the
   medical device and product safety route.
