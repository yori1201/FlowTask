# TASKFLOW

Aplikasi produktivitas pribadi untuk mengelola tugas, catatan, jadwal, proyek,
dan pengingat dalam satu tempat. Dibangun Android-native (Kotlin + Jetpack
Compose) dengan pendekatan offline-first dan sinkronisasi cloud Firestore.

> PRD: lihat `public/assets/aistudio/TASKFLOW_PRD_v1.0_2e483265.md`

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

## Stack

- Kotlin 2.4, Jetpack Compose (BOM 2026.09), Material 3
- MVVM + StateFlow, Room (draft/log/konflik lokal), DataStore (preferensi)
- Firebase Auth (Google Sign-In) + Cloud Firestore (dengan security rules ketat)
- Min SDK 26 (Android 8.0), Target SDK 36

## Setup

1. **Kebutuhan**: JDK 21, Android SDK (API 36), Gradle 9.3.1 (lihat
   `gradle/wrapper/gradle-wrapper.properties`).
2. **google-services.json**: file ini sengaja tidak di-commit. Ambil dari
   Firebase Console (Project settings → Your apps → Android) dan letakkan di
   `app/`. Tanpa file ini, build tetap berjalan dengan peringatan
   (`MissingGoogleServicesStrategy.WARN`), tetapi Firebase tidak akan aktif saat runtime.
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
│   ├── local/          # Room (sync_logs, sync_conflicts, note_drafts) + DataStore
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
  Aktivasi (sekali saja): tambahkan dua *repo secrets*
  **Settings → Secrets and variables → Actions**:
  1. `KEYSTORE_BASE64` — isi `my-upload-key.jks` dalam base64
     (Linux/macOS: `base64 -w 0 my-upload-key.jks`)
  2. `KEYSTORE_PASSWORD` — password keystore (store & key sama)

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
