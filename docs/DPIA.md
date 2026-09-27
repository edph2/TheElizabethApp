# Data Protection Impact Assessment (DPIA)

**System:** The Elizabeth App (an offline communication aid for an Android tablet)
**Version assessed:** 0.1.0 · **Date:** 27 September 2026 · **Status:** draft for review

This follows the structure of the UK ICO's DPIA template. It records how the app was
designed to protect the personal data it handles. It is not legal advice. Review it with
Elizabeth, her family, and anyone who will be responsible for the tablet. Review it again
before the app is used by anyone else, or if what it does changes.

## 1. Why a DPIA

The app processes data about someone's health and her voice. It uses AI (speech synthesis,
voice cloning and word prediction), and the person it serves is vulnerable. A DPIA is not
strictly required while the app is used privately within a household (see §4), but it is
good practice and Article 25 (data protection by design) applies in spirit throughout.

## 2. Nature of the processing

| Data | Source | Stored where | How long |
|---|---|---|---|
| Messages she speaks (history) | Typed or chosen by her | Tablet, encrypted by the app | Rolling window, 200 by default; can be switched off or erased |
| Learned words (word counts) | Her messages | Tablet, encrypted | Until erased; one word or all |
| Phrases and categories | Family and carers | Tablet, encrypted | Until edited or erased |
| Recorded phrases (message banking) | Her voice | Tablet, encrypted | Until removed |
| Voice banking recordings | Her voice (or a donor's) | Tablet, encrypted | Until consent is withdrawn or erased |
| Consent records | Recorded before voice banking | Tablet, encrypted, and inside exports and voice packages | Kept with the recordings and voices |
| Installed voice models | Imported by a carer | Tablet, app-private storage (Android's file encryption) | Until removed; removed automatically if consent is withdrawn |
| Touch counts (optional) | How touches go: counts only | Tablet, encrypted | Until reset; off by default |
| Settings, carer PIN hash, PIN lock state | Carers | Tablet, encrypted | Until changed |
| Privacy log | App events (no content) | Tablet, encrypted, hash-chained | For the life of the app data |

**No data leaves the tablet on its own.** The app has no internet permission, so Android
does not let it open network connections. This is checked in every CI build, and on a
real Android system by an instrumented test. Data leaves the tablet only when a carer
chooses to:

- export a backup (passphrase-encrypted) to a place the carer chooses, such as a USB stick
  or a cloud storage app like Google Drive (see §7a), or
- export voice banking recordings for training (passphrase-encrypted), or
- print the paper board.

## 3. Scope

The data subjects are:
- **Elizabeth** (the user).
- **A voice donor,** if one is used.
- **People named in messages or phrases** (family, carers).

Special category data is involved. Her messages will often reveal health information
(Article 9). Her voice recordings and voice model are closely linked to her identity. They
are treated with the highest level of protection, whether or not they count as "biometric
data" in law. The volume is small: one person, one tablet.

## 4. Context and roles

- **Private use:** Elizabeth, helped by her family, using her own tablet is most likely
  covered by the household exemption (UK GDPR / GDPR Article 2(2)(c)).
- **The developer never receives any data,** and so is not a controller or processor for
  it.
- **Paid carers** (agency or NHS) who use the tablet at work may bring their employer's
  data protection obligations with them. Their organisation should review this DPIA.
- **Training a voice model** happens on a computer chosen by the family. Whoever runs it
  controls that copy of the recordings for as long as they hold it. The training guide
  requires deleting them afterwards.
- **If the app is distributed more widely,** the distributor must review this assessment
  again, along with the medical device and GPL questions in DESIGN.md §7 and
  THIRD_PARTY_NOTICES.md.

## 5. Purposes

The purposes are:
- To let Elizabeth communicate.
- To make communicating faster (prediction, history).
- To let her speak in her own voice (message and voice banking, voice models).
- To adapt the app to her changing motor control (optional touch counts).

No other purpose: no analytics, advertising, profiling, research or product improvement.

## 6. Consultation

Still to do: with Elizabeth herself, her family, and her carers. She has full mental
capacity, and the app is designed so that she can see and control what it keeps.

## 7. Necessity and proportionality

**Lawful basis, if the household exemption does not apply:** explicit consent (Article
9(2)(a)) from Elizabeth for her own data, and from any voice donor for theirs.

**Data minimisation:**
- History and learning can each be switched off.
- Touch statistics are counts only, and off by default.
- The privacy log never records message content.
- Nothing is collected for any other purpose.

**Her rights:**

| Right | How it is met |
|---|---|
| Access | "What the app knows" screen, plus a full export in open formats |
| Rectification | Phrases and settings can be edited |
| Erasure | Erase history, learned words, single words, voice recordings, or everything (including the encryption key) |
| Restriction / objection | Learning, history and touch counting can be switched off |
| Portability | The export is JSON, text and WAV files, and can be opened without the app using a documented script |

**Accuracy:** word prediction only offers suggestions, and she always chooses what is said.

## 7a. Cloud storage of backups (e.g. Google Drive)

Her voice recordings are irreplaceable, and losing the tablet would lose them, so the family
may store backups in a cloud service.

- **Safeguards:** the backup is encrypted on the tablet with AES-256-GCM, using a key
  derived from a passphrase that only the family holds. The cloud provider receives only
  ciphertext. It can see the file name (which includes "elizabeth-backup" and a date), the
  size and the timestamps.
- **Remaining risks:**
  - a weak passphrase;
  - metadata;
  - the provider's jurisdiction. For Google this is the US, although the content is
    unreadable to Google.
- **Decision:** this is Elizabeth's choice. Record it in §10. If a non-US provider is
  preferred, a USB stick or an EU-hosted cloud storage app gives the same protection.
- **The app is unchanged by this:** it still has no internet permission. Uploading is done
  by the separate storage app, only when a carer chooses that destination.

## 8. Risks

| # | Risk | Likelihood | Severity | Measures | Residual |
|---|---|---|---|---|---|
| R1 | Data sent off the tablet without her knowledge | Remote | Severe | No internet permission (checked in CI and on-device); no analytics or crash reporting; backup disabled; offline voices only | Low |
| R2 | Tablet lost or stolen, data read | Possible | Significant | Android screen lock (recommended); Android file encryption; app data encrypted with a Keystore key; release builds not debuggable | Low |
| R3 | Someone nearby changes settings or reads history | Possible | Moderate | Settings need a 2-second hold and optional PIN; PIN lockout after 5 wrong attempts; history can be switched off | Low |
| R4 | Her cloned voice misused (e.g. a deepfake) | Remote | Severe | Synthesised speech is never saved to a file; voice packages are encrypted and must carry a consent record; withdrawing consent removes voices made from the recordings; training data deleted after use | Low |
| R5 | Recordings exposed during training | Possible | Significant | Encrypted export; checksums and consent checked before training; guidance: trusted computer, disk encryption, no cloud sync, delete afterwards | Medium: depends on the people training |
| R6 | Export file obtained by someone else | Possible | Significant | AES-256-GCM with a passphrase-derived key; tampering and truncation detected | Low if the passphrase is strong |
| R7 | Data lost (tablet broken or stolen) and she loses her phrases and irreplaceable voice recordings | Possible | Severe | Encrypted backup to a USB stick or cloud storage (§7a); reminders when recordings are not backed up or the last backup is over 7 days old; installed voices can be included; "Check a backup" verifies a copy without changing anything; paper board | Low if reminders are acted on and two copies are kept |
| R11 | Encrypted backup in cloud storage is obtained by others | Possible | Significant | Encryption before upload; strong passphrase kept separately; metadata limited to file name, size and date | Low |
| R8 | Donor's voice used without valid consent | Remote | Significant | Consent statement recorded before voice banking; a voice without consent is refused on import | Low |
| R9 | Malicious voice package or library | Remote | Significant | Checksums verified; unsafe file names refused; the speech library pinned by SHA-256; SBOM published | Low |
| R10 | Speech failure leaves her unable to communicate | Possible | Severe (safety) | Large-text fallback, attention chime, the Android voice as back-up, home-screen mode to return after a crash, paper board | Low |

## 9. Outcome

With these measures, no high residual risk remains apart from R5, which depends on how
the family carries out training.

**Actions:**
- Consult Elizabeth.
- Set a screen lock on the tablet.
- Use signed release builds only.
- Agree who may train the voice, and on which computer.
- Export regularly and keep the passphrase safe.

## 10. Sign-off

| Role | Name | Date | Decision |
|---|---|---|---|
| Elizabeth | | | |
| Family member responsible for the tablet | | | |
| Person who will train the voice (if any) | | | |
| Where backups are kept (e.g. Google Drive, USB stick) | | | |
