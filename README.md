# TaskFlow

**Personal Task & Notes Management** — offline-first personal productivity
application untuk mengelola tugas, catatan, jadwal, dan proyek dalam satu
tempat. Dibangun Android-native (Kotlin + Jetpack Compose) dengan
sinkronisasi cloud Firestore. (Nama awal proyek: FlowTask — repo ini.)

> PRD lengkap: `public/assets/aistudio/TASKFLOW_PRD_v1.0_2e483265.md`

## Unduh & Pasang (APK)

Cara tercepat memakai TaskFlow — tanpa build apa pun:

1. Buka halaman **[Releases](../../releases/latest)** repo ini.
2. Unduh aset `TaskFlow-*-release.apk` (± 3,6 MB).
3. Buka file itu di HP Android → izinkan "instal aplikasi tidak dikenal"
   bila diminta → **Install**.
4. Saat pertama dibuka, pilih **"Lanjutkan tanpa akun"** — semua fitur
   (tugas, catatan, kalender, pengingat, backup) langsung berfungsi tanpa
   internet.

APK ditandatangani keystore rilis TaskFlow (skema v2). Pembaruan hanya bisa
dipasang di atas instalasi bertanda tangan sama — jangan uninstall dulu.

---

## Product Overview (PRD FlowTask)

### Product Goals

FlowTask aims to help users:

- Capture tasks and ideas quickly.
- Organize tasks, notes, and personal projects.
- Track deadlines, schedules, and progress.
- Access saved information offline.
- Use the application across mobile and desktop devices.
- Get reminders for important activities.
- Interact with connected AI assistants through chat, with user approval before actions are applied.

### Core Features

**Tasks**
- Create, edit, complete, and delete tasks.
- Set due dates and priorities.
- Organize tasks by status and category.
- Support checklists and subtasks where appropriate.
- Review pending, upcoming, and completed tasks.

**Notes**
- Create, edit, delete, and organize notes.
- Categorize and search saved notes.
- Keep notes available offline.
- Make capturing information quick and straightforward.

**Projects**
- Group related tasks under personal projects.
- Set project deadlines.
- Track project status and progress.

**Calendar and Reminders**
- View dated tasks and scheduled activities.
- Receive reminders for deadlines and upcoming events.
- Support local notifications where the platform allows.

**Productivity Overview**
- Show pending and completed tasks.
- Summarize daily and weekly activity.
- Display project progress without unnecessary metrics.

### Offline-First Requirements

Offline access is a core product requirement, not an optional enhancement.

Users should be able to:

- Open FlowTask without an internet connection.
- Read previously saved tasks and notes.
- Create and edit tasks and notes offline.
- Retain local changes after closing and reopening the application.
- Synchronize pending changes when connectivity returns.

Synchronization must be designed to prevent silent data loss. Failed sync
attempts and conflicting updates must be handled explicitly, with a
recoverable path for the user.

### Supported Devices

- **Android:** primary mobile experience.
- **Laptop/Desktop:** web or installable PWA experience, subject to the final technical implementation.

The interface and data model should provide a consistent experience across
supported devices.

### AI Integration

AI is planned as a chat-based capability. Users communicate with connected
assistants through the FlowTask chat interface rather than relying on
separate manual workflows.

**Planned AI Connections**

| AI component | Intended role |
|---|---|
| MUSE AI | Server-side AI connection |
| Meta AI | Consumer-side AI connection |
| AI routing | Supports both connections |
| Chat interface | Primary user interaction |
| Action approval | Required before applying changes |

**Planned Use Cases**

- Create or update tasks through chat.
- Capture and organize notes.
- Summarize notes and task lists.
- Help plan schedules and priorities.
- Review project progress.
- Find information stored in FlowTask, subject to granted access.

**Approval and Data Safety**

User approval is required before an AI-requested action changes FlowTask
data. AI must not independently delete, modify, or execute consequential
operations without explicit authorization. The application should show the
proposed action clearly before the user approves it. AI integration is a
planned update and must not block delivery of the core application. Each
connection, authentication method, and supported capability must be verified
before being represented as operational.

### Product Principles

1. **Simple:** prioritize clear workflows over feature quantity.
2. **Offline-first:** essential personal information remains accessible without internet.
3. **Reliable:** protect user data and make failures recoverable.
4. **Accessible:** optimize for mobile use while supporting desktop.
5. **Consistent:** keep behavior and information coherent across devices.
6. **Private:** give users control over data access and AI actions.
7. **Maintainable:** use modular architecture without unnecessary complexity.

### Development Roadmap

The roadmap describes intended implementation order. It does not imply that
listed features are already implemented.

| Phase | Scope |
|---|---|
| 1. Core foundation | App structure, navigation, local storage, task management, notes |
| 2. Productivity | Projects, calendar, reminders, productivity overview |
| 3. Data reliability | Offline behavior, synchronization, conflict handling, backup and recovery |
| 4. AI integration | Chat interface, MUSE connection, Meta workflow, routing, approval flow |
| 5. Quality and release | Security review, accessibility, performance, testing, and release preparation |

> Status implementasi v1.2.0: fase 1-3 sudah terwujud (mode lokal,
> sinkronisasi Firestore, konflik revision-safe, backup/restore, pengingat,
> CI). Fase 4-5 menyusul.

### Technical Direction

The final technology stack has not yet been specified in the product
requirements. Technical choices should be evaluated against these criteria:

- Reliable local persistence.
- Offline-capable application behavior.
- Secure and recoverable synchronization.
- Android and desktop accessibility.
- Maintainable, modular code.
- Practical integration points for future AI connections.

Do not assume a framework, database, backend, hosting provider, or
synchronization protocol until it is explicitly selected in the technical
design.

> Catatan implementasi (v1.2.0): stack untuk aplikasi Android sudah dipilih
> dan berjalan — lihat bagian **Stack** di bawah. Kriteria evaluasi di atas
> tetap menjadi acuan untuk keputusan teknis berikutnya (termasuk klien
> laptop/desktop di fase lanjut).

### Development Guidelines

- Preserve existing working behavior.
- Follow the approved product requirements and scope.
- Do not add unapproved features or perform unnecessary refactoring.
- Treat data integrity as a release-critical requirement.
- Test offline use, reconnection, and synchronization failures.
- Provide clear error states and recovery paths.
- Never silently discard user data.
- Keep documentation aligned with the actual implementation.
- Require explicit user approval for destructive or sensitive actions.

### Product Philosophy

**Capture. Organize. Execute. Remember.**

FlowTask is intended to be a dependable personal workspace: simple to use,
available when needed, and built around the user's control of their
information.

### License

No license has been specified yet. Until a license is added, all rights are
reserved by the repository owner.

---

## Stack (implementasi v1.2)

- Kotlin 2.4, Jetpack Compose (BOM 2026.09), Material 3
- MVVM + StateFlow, Room (draft/log/konflik lokal + mode lokal penuh), DataStore (preferensi)
- Firebase Auth (Google Sign-In) + Cloud Firestore (dengan security rules ketat)
- Min SDK 26 (Android 8.0), Target SDK 36

## Setup

1. **Kebutuhan**: JDK 21, Android SDK (API 36), Gradle 9.6.1 (lihat
   `gradle/wrapper/gradle-wrapper.properties`).
2. **google-services.json**: file ini sengaja tidak di-commit. Ambil dari
   Firebase Console (Project settings → Your apps → Android) dan letakkan di
   `app/`. Tanpa file ini, build tetap berjalan dengan peringatan
   (`MissingGoogleServicesStrategy.WARN`), tetapi Firebase tidak akan aktif saat runtime.
   Daftarkan dulu aplikasi Android `com.aistudio.taskflow.kqzpmv` di project
   Firebase yang sama dengan database di `firebase_applet_config.xml`,
   aktifkan metode masuk Google, dan daftarkan sidik jari SHA-1 keystore
   yang dipakai menandatangani APK (tanpa SHA-1, login Google ditolak).
   Untuk build CI/release, file yang sama dimasukkan sebagai secret
   `GOOGLE_SERVICES_JSON` (lihat bagian CI & Rilis).
3. **Firestore database ID**: dikonfigurasi lewat
   `app/src/main/res/values/firebase_applet_config.xml` (hasil injeksi AI Studio).
   Sesuaikan bila Anda membuat project Firebase sendiri.

## Build & Test

```bash
# Build debug
gradle :app:assembleDebug

# Unit test (JVM + Robolectric)
gradle :app:testDebugUnitTest

# Uji Firestore Security Rules (36 assertion) — perlu Java & Node 20+
npm install
npm run test:rules
```

## Struktur Kode

```
app/src/main/java/com/example/
├── auth/               # Google Sign-In + layar masuk
├── backup/             # Enkode/dekode backup JSON + checksum SHA-256
├── data/
│   ├── local/          # Room (mode lokal penuh, sync_logs, sync_conflicts, note_drafts) + DataStore
│   └── remote/         # Repository Firestore, error handler, recurrence, restore planner
├── model/              # Model data (@Keep) untuk Firestore
├── notifications/      # ReminderScheduler, ReminderReceiver, BootReceiver
├── ui/                 # Layar Compose (Dashboard, Tasks, Notes, Calendar, Projects, Sync/Backup)
└── viewmodel/          # TaskFlowViewModel + kebijakan pemulihan stream
```

## Keamanan & Sinkronisasi

- `firestore.rules`: default-deny, validasi field ketat per entitas, backup
  immutable. Diuji oleh `firestore.test.js` (jalankan lewat emulator).
- Penulisan revision-safe: update tugas/catatan memakai `runTransaction`;
  operasi lain memakai `FieldValue.increment(1)` sehingga revision selalu
  monoton lintas perangkat.
- Konflik revisi dideteksi otomatis dan diselesaikan lewat UI tiga opsi
  (lokal / cloud / gabung manual) — tidak ada penimpaan senyap.
- Auto-backup Android dimatikan (`allowBackup=false`); daya tahan data dijamin
  oleh sinkronisasi Firestore + backup cloud + ekspor JSON.

## CI & Rilis

- `.github/workflows/ci.yml` menjalankan build + unit test (job `android`)
  dan uji security rules lewat emulator (job `rules`) pada setiap
  push/pull-request.
- `.github/workflows/release.yml` otomatis membangun APK release ter-sign
  dan melampirkannya ke GitHub Release setiap kali tag `v*` di-push.
  Aktivasi (sekali saja): tambahkan tiga *repo secrets*
  **Settings → Secrets and variables → Actions**:
  1. `KEYSTORE_BASE64` — isi `my-upload-key.jks` dalam base64
     (Linux/macOS: `base64 -w 0 my-upload-key.jks`)
  2. `KEYSTORE_PASSWORD` — password keystore (store & key sama)
3. `GOOGLE_SERVICES_JSON` — isi `app/google-services.json` asli dari
   Firebase Console dalam base64
   (Linux/macOS: `base64 -w 0 app/google-services.json`).
   Tanpa secret ini CI/release tetap jalan, tapi APK memakai dummy dan
   tombol "Masuk dengan Google" tidak berfungsi; mode lokal tetap jalan.

  Lalu: `git tag v1.2.1 && git push origin v1.2.1` — APK ter-publish
  otomatis di halaman Releases. Tanpa kedua secret ini job dilewati
  (skipped) tanpa memerahkan CI.

## Mode Lokal (v1.2.0)

Aplikasi kini dapat dipakai **tanpa akun Google** — pilih "Lanjutkan tanpa
akun" pada layar masuk. Seluruh data (tugas, catatan, proyek, kalender,
pengingat, backup) tersimpan di Room pada perangkat dan tidak pernah
menyentuh Firestore. Jalur cloud tetap utuh: begitu masuk dengan Google,
semua sinkronisasi lintas perangkat berjalan seperti sebelumnya.

- Data mode lokal terpisah dari data akun (pemilik `local-user`).
- Aksi "Selesai" dari notifikasi dan penjadwalan ulang pengingat setelah
  reboot berfungsi di kedua mode.
- Backup/restore dan ekspor JSON tetap tersedia di mode lokal.

## Build Release Bertanda Tangan

```bash
export KEYSTORE_PATH=$PWD/my-upload-key.jks
export STORE_PASSWORD=...   # lih. keystore pribadi Anda
export KEY_PASSWORD=...
gradle :app:assembleRelease # output: app/build/outputs/apk/release/
```

Keystore (`my-upload-key.jks`) TIDAK di-commit. Simpan baik-baik: APK
pembaruan harus ditandatangani dengan kunci yang sama.
