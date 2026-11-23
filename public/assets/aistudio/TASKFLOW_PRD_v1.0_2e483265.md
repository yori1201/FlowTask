# TASKFLOW

## Product Requirements Document (PRD)

  Metadata           Detail
  ------------------ --------------------------------------
  Nama produk        TASKFLOW
  Versi dokumen      1.0
  Status             Product baseline / MVP
  Jenis produk       Personal Productivity Workspace
  Platform           Android Native + Web/PWA
  Model penggunaan   Personal, multi-device
  Pendekatan         Offline-first, cloud synchronization
  Bahasa             Indonesia dan Inggris
  AI                 Ditunda ke future update

------------------------------------------------------------------------

## 1. Ringkasan Produk

TASKFLOW adalah aplikasi produktivitas pribadi untuk mengelola
pekerjaan, catatan, jadwal, proyek, dan pengingat dalam satu tempat.

Aplikasi dirancang agar pengguna tetap dapat bekerja tanpa koneksi
internet. Data disimpan terlebih dahulu di perangkat, kemudian
disinkronkan ke cloud ketika koneksi tersedia.

Platform utama adalah aplikasi Android native. Web/PWA menjadi
pendamping untuk akses melalui laptop. Keduanya menggunakan akun yang
sama.

### Prinsip produk

1.  Data pengguna tidak boleh hilang karena koneksi internet terputus.
2.  Fitur inti harus dapat digunakan secara offline setelah setup awal.
3.  Sinkronisasi harus transparan dan dapat diperiksa pengguna.
4.  Konflik data tidak boleh diselesaikan dengan menimpa data secara
    diam-diam.
5.  Antarmuka harus sederhana, cepat, dan mudah digunakan.
6.  AI tidak termasuk MVP dan akan ditambahkan pada pembaruan
    berikutnya.

## 2. Tujuan Produk dan Indikator Keberhasilan

### Tujuan

-   Mengelola tugas dan pekerjaan pribadi.
-   Menyimpan serta mengorganisasi catatan.
-   Mengelola jadwal dan deadline.
-   Mengakses data melalui Android dan laptop.
-   Tetap produktif tanpa internet.
-   Menjaga konsistensi data lintas perangkat.
-   Menyediakan mekanisme backup dan pemulihan.

### Indikator keberhasilan

-   Pengguna dapat membuat, mengedit, dan menyelesaikan task secara
    offline.
-   Pengguna dapat membuat dan mengedit notes secara offline.
-   Data lokal tetap tersedia setelah aplikasi ditutup dan dibuka
    kembali.
-   Perubahan dapat disinkronkan ke perangkat lain.
-   Retry sinkronisasi tidak menghasilkan duplikasi.
-   Konflik perubahan terdeteksi dan dapat diselesaikan.
-   Backup dapat diekspor, divalidasi, dan dipulihkan.

## 3. Target Pengguna dan Skenario

Target utama adalah individu yang membutuhkan sistem pengelolaan
aktivitas pribadi, bukan aplikasi kolaborasi tim.

Skenario utama:

1.  Membuat daftar pekerjaan harian melalui HP.
2.  Menyimpan ide dan informasi melalui Notes.
3.  Menghubungkan catatan dengan task atau project.
4.  Menentukan deadline dan pengingat.
5.  Melihat agenda melalui kalender.
6.  Melanjutkan pekerjaan melalui laptop.
7.  Mengakses data ketika tidak memiliki internet.
8.  Memulihkan data ketika berganti perangkat.

## 4. Ruang Lingkup dan Prioritas

Prioritas fitur:

-   **P0:** wajib untuk MVP.
-   **P1:** pengembangan setelah MVP.
-   **P2:** opsional atau berdasarkan evaluasi penggunaan.

### 4.1 MVP --- P0

-   Registrasi dan login email.
-   Dashboard.
-   Task Management.
-   Notes Management.
-   Calendar.
-   Projects dan Categories.
-   Database lokal dan offline-first.
-   Cloud synchronization.
-   Notifications dan Reminders.
-   Backup dan Restore.
-   Web/PWA untuk laptop.
-   Security dan recovery baseline.

### 4.2 Future Updates --- P1/P2

-   Integrasi AI melalui chat MUSE dan Meta AI.
-   AI Task Assistant.
-   AI Notes Assistant.
-   AI Daily Brief.
-   Productivity Insights lanjutan.
-   Android widgets.
-   Fitur tambahan berdasarkan evaluasi penggunaan nyata.

### 4.3 Di luar MVP

-   Kolaborasi multi-user.
-   Pembagian tugas tim.
-   Integrasi AI aktif.
-   Otomatisasi yang mengubah data tanpa persetujuan pengguna.
-   Monetisasi dan subscription.

## 5. Navigasi dan UX

### 5.1 Navigasi Android

Navigasi utama:

1.  Home
2.  Tasks
3.  Notes
4.  Calendar
5.  Projects
6.  More

Menu More:

-   Notifications.
-   Productivity Insights.
-   Sync & Backup.
-   Settings.
-   Account.

### 5.2 Navigasi Web/PWA

Sidebar:

-   Dashboard.
-   My Tasks.
-   Notes.
-   Calendar.
-   Projects.
-   Productivity.
-   Notifications.
-   Sync & Backup.
-   Settings.

### 5.3 Prinsip desain

-   Clean, minimal, dan mudah dipahami.
-   Aksi utama mudah dijangkau dengan satu tangan di Android.
-   Tampilan konsisten pada Android dan laptop.
-   Mendukung Light Mode dan Dark Mode.
-   Ukuran teks dapat disesuaikan.
-   Status sinkronisasi terlihat jelas.
-   Hindari animasi berat.
-   Form input singkat dan hanya meminta informasi yang diperlukan.
-   Desain responsif untuk berbagai ukuran layar.

## 6. Functional Requirements

### 6.1 Authentication & Account

  ID       Kebutuhan                          Prioritas
  -------- ---------------------------------- -----------
  FR-001   Registrasi email dan password      P0
  FR-002   Login dan logout                   P0
  FR-003   Verifikasi email                   P0
  FR-004   Reset password                     P0
  FR-005   Session persistence yang aman      P0
  FR-006   Pengelolaan profil dasar           P1
  FR-007   Melihat perangkat yang terhubung   P1
  FR-008   Logout dari perangkat lain         P1

Ketentuan:

-   Satu akun dapat digunakan di beberapa perangkat milik pengguna.
-   Setiap akun hanya dapat mengakses datanya sendiri.
-   Pengguna yang sudah pernah login dan memiliki data lokal dapat
    menggunakan fitur inti secara offline.
-   Instalasi baru dan login pertama memerlukan internet.
-   Session kedaluwarsa harus ditangani tanpa menghapus data lokal.

### 6.2 Dashboard

  ID       Kebutuhan                           Prioritas
  -------- ----------------------------------- -----------
  FR-009   Menampilkan task hari ini           P0
  FR-010   Menampilkan task terlambat          P0
  FR-011   Menampilkan task prioritas tinggi   P0
  FR-012   Menampilkan task mendatang          P0
  FR-013   Ringkasan progres                   P1
  FR-014   Catatan terakhir diperbarui         P1
  FR-015   Shortcut membuat task dan note      P0
  FR-016   Status sinkronisasi                 P0

Dashboard hanya boleh menampilkan data aktual, bukan angka atau grafik
simulasi.

### 6.3 Task Management

#### Data task

Setiap task memiliki:

-   ID unik.
-   Judul.
-   Deskripsi.
-   Status.
-   Prioritas.
-   Tanggal dibuat dan diperbarui.
-   Tanggal mulai (opsional).
-   Deadline.
-   Waktu pengingat.
-   Kategori.
-   Project (opsional).
-   Tags.
-   Recurrence rule.
-   Subtasks.
-   Revision/version.
-   Status penghapusan.

#### Status

-   To Do.
-   In Progress.
-   Completed.

#### Prioritas

-   Low.
-   Medium.
-   High.
-   Urgent.

#### Functional requirements

  ID       Kebutuhan                                Prioritas
  -------- ---------------------------------------- -----------
  FR-017   Membuat task                             P0
  FR-018   Mengedit task                            P0
  FR-019   Menghapus task                           P0
  FR-020   Menyelesaikan dan membuka kembali task   P0
  FR-021   Menentukan prioritas                     P0
  FR-022   Menentukan deadline                      P0
  FR-023   Membuat subtasks/checklist               P0
  FR-024   Menentukan kategori dan project          P0
  FR-025   Menambahkan tags                         P1
  FR-026   Membuat task berulang                    P1
  FR-027   Menduplikasi task                        P1
  FR-028   Mengarsipkan task                        P1
  FR-029   Mencari dan memfilter task               P0
  FR-030   Melihat riwayat perubahan task           P1

#### Recurring tasks

Mendukung harian, mingguan, bulanan, tahunan, dan custom interval.

Ketika satu instance selesai, sistem membuat instance berikutnya sesuai
aturan pengulangan. Riwayat penyelesaian sebelumnya harus dipertahankan.

### 6.4 Notes Management

Notes adalah modul mandiri, bukan sekadar kolom deskripsi task.

#### Format editor

-   Heading H1, H2, H3.
-   Paragraph.
-   Bold, italic, underline.
-   Bullet list.
-   Numbered list.
-   Checklist.
-   Blockquote.
-   Hyperlink.
-   Divider.
-   Code block.

#### Functional requirements

  ID       Kebutuhan                           Prioritas
  -------- ----------------------------------- -----------
  FR-031   Membuat catatan                     P0
  FR-032   Mengedit catatan                    P0
  FR-033   Auto-save                           P0
  FR-034   Menghapus dan memulihkan catatan    P0
  FR-035   Pin dan favorite                    P0
  FR-036   Mengarsipkan catatan                P0
  FR-037   Kategori dan tags                   P0
  FR-038   Pencarian isi catatan               P0
  FR-039   Waktu terakhir diedit               P0
  FR-040   Jumlah kata                         P1
  FR-041   Menghubungkan note dengan task      P0
  FR-042   Menghubungkan note dengan project   P0
  FR-043   Membuat task dari teks note         P1

#### Auto-save

-   Perubahan disimpan ke database lokal secara otomatis.
-   Gunakan debounce untuk mengurangi penulisan berulang.
-   Draft lokal harus aman jika aplikasi ditutup mendadak.
-   Status penyimpanan harus jelas.
-   Penyimpanan lokal tidak boleh menunggu respons server.

#### Relasi Notes dan Tasks

Satu note dapat terhubung dengan beberapa task dan satu task dapat
memiliki beberapa note. Menghapus salah satu objek tidak otomatis
menghapus objek lainnya.

### 6.5 Calendar

  ID       Kebutuhan                               Prioritas
  -------- --------------------------------------- -----------
  FR-044   Tampilan bulanan                        P0
  FR-045   Tampilan mingguan                       P0
  FR-046   Tampilan harian                         P0
  FR-047   Menampilkan task berdasarkan deadline   P0
  FR-048   Membuka detail task dari kalender       P0
  FR-049   Mengubah tanggal task dari kalender     P1
  FR-050   Menampilkan task berulang               P1
  FR-051   Menampilkan agenda hari ini             P0

Ketentuan:

-   Gunakan zona waktu perangkat atau zona waktu akun yang ditentukan
    pengguna.
-   Perubahan zona waktu tidak boleh menggeser deadline secara tidak
    sengaja.
-   Task tanpa deadline tidak dipaksakan masuk kalender.

### 6.6 Projects & Categories

Contoh project/category: Personal, Work, Learning, Finance, Health, Side
Projects.

  ID       Kebutuhan                            Prioritas
  -------- ------------------------------------ -----------
  FR-052   Membuat project                      P0
  FR-053   Mengedit dan menghapus project       P0
  FR-054   Membuat kategori                     P0
  FR-055   Menghubungkan task dengan project    P0
  FR-056   Menghubungkan notes dengan project   P0
  FR-057   Melihat progres project              P1
  FR-058   Mengarsipkan project                 P1

Penghapusan project tidak boleh otomatis menghapus task dan notes
terkait. Sistem harus meminta keputusan pengguna mengenai relasi
tersebut.

### 6.7 Notifications & Reminder Center

Notifikasi harus tetap dapat bekerja secara offline selama pengingat
sudah tersimpan dan dijadwalkan secara lokal.

  ID       Kebutuhan                      Prioritas
  -------- ------------------------------ -----------
  FR-059   Pengingat task                 P0
  FR-060   Pengingat deadline             P0
  FR-061   Notifikasi task terlambat      P1
  FR-062   Snooze notification            P0
  FR-063   Mengatur waktu pengingat       P0
  FR-064   Quiet hours                    P1
  FR-065   Mengatur kategori notifikasi   P1
  FR-066   Daily summary                  P1
  FR-067   Riwayat notifikasi             P1

Jenis notifikasi:

-   Task reminder.
-   Upcoming deadline.
-   Overdue task.
-   Daily summary.
-   Weekly summary.
-   Sync failure.
-   Backup reminder.

Ketentuan teknis:

-   Android local notification menjadi mekanisme utama.
-   WorkManager untuk pekerjaan fleksibel terjadwal.
-   AlarmManager hanya jika diperlukan untuk pengingat tepat waktu dan
    sesuai kebijakan Android.
-   Tangani izin notifikasi Android 13+ dan pembatasan exact alarm pada
    versi relevan.
-   Jadwalkan ulang pengingat setelah restart perangkat, perubahan zona
    waktu, atau perubahan data.

### 6.8 Offline-First & Cloud Synchronization

**Prinsip:** pengguna tidak boleh kehilangan kemampuan bekerja hanya
karena internet terputus.

#### Offline behavior

Saat offline, pengguna tetap dapat:

-   Membuat, mengedit, menghapus, dan menyelesaikan task.
-   Membuat dan mengedit notes.
-   Mencari data lokal.
-   Mengelola project dan kategori.
-   Melihat kalender.
-   Menerima notifikasi lokal yang telah dijadwalkan.

#### Mekanisme sync

1.  Pengguna melakukan perubahan.
2.  Perubahan langsung disimpan ke database lokal.
3.  Outbox queue mencatat perubahan yang belum tersinkronisasi.
4.  Ketika online, Sync Engine mengirim perubahan ke server.
5.  Server memvalidasi dan menyimpan perubahan.
6.  Client mengambil pembaruan cloud.
7.  Client menandai perubahan sebagai synced hanya setelah konfirmasi
    server.

  ID       Kebutuhan                                Prioritas
  -------- ---------------------------------------- -----------
  FR-068   Menyimpan perubahan lokal sebelum sync   P0
  FR-069   Outbox queue                             P0
  FR-070   Sync otomatis saat online                P0
  FR-071   Sync manual                              P0
  FR-072   Incremental sync                         P0
  FR-073   Retry saat sync gagal                    P0
  FR-074   Idempotency untuk mencegah duplikasi     P0
  FR-075   Deteksi konflik perubahan                P0
  FR-076   Penyelesaian konflik oleh pengguna       P0
  FR-077   Soft delete dan tombstone                P0
  FR-078   Status sync per perangkat                P0
  FR-079   Riwayat sync dan error                   P1

#### Conflict handling

Contoh: HP mengubah judul task ketika laptop offline, sementara laptop
mengubah judul task yang sama.

Aturan:

1.  Perubahan pada field berbeda dapat digabungkan jika aman.
2.  Perubahan pada field yang sama harus dideteksi sebagai konflik.
3.  Pengguna dapat memilih versi HP, versi laptop, atau menggabungkan
    secara manual.
4.  Salinan perubahan yang belum dipilih harus dipertahankan.
5.  Keputusan konflik dicatat.

Status sinkronisasi:

-   Local Only.
-   Pending Sync.
-   Syncing.
-   Synced.
-   Sync Failed.
-   Conflict Detected.

Status Synced hanya boleh ditampilkan setelah server mengonfirmasi
perubahan tersimpan.

### 6.9 Backup & Restore

Backup berbeda dari sinkronisasi. Sinkronisasi menyamakan data
antarperangkat, sedangkan backup menyediakan salinan untuk pemulihan.

  ID       Kebutuhan                       Prioritas
  -------- ------------------------------- -----------
  FR-080   Backup cloud                    P0
  FR-081   Export data ke file             P0
  FR-082   Import dan restore              P0
  FR-083   Backup lokal                    P0
  FR-084   Waktu backup terakhir           P0
  FR-085   Validasi integritas backup      P0
  FR-086   Memilih data untuk dipulihkan   P1
  FR-087   Riwayat backup                  P1

Format export:

-   JSON untuk data terstruktur.
-   File tambahan untuk lampiran jika fitur lampiran ditambahkan.
-   Versi format backup untuk mendukung migrasi versi lama.

Restore harus menyediakan preview dan konfirmasi. Jangan menimpa data
aktif tanpa persetujuan pengguna.

### 6.10 Productivity Insights

  ID       Kebutuhan                          Prioritas
  -------- ---------------------------------- -----------
  FR-088   Jumlah task selesai                P1
  FR-089   Jumlah task tertunda               P1
  FR-090   Tren penyelesaian task             P1
  FR-091   Ringkasan produktivitas mingguan   P1
  FR-092   Progres project                    P1

Analitik harus dihitung dari data aktual. Jangan membuat skor
produktivitas tanpa definisi dan dasar perhitungan yang jelas.

### 6.11 AI Integration --- Future Update

AI tidak termasuk MVP.

Arah pengembangan berikutnya:

-   MUSE AI digunakan melalui percakapan/chat dengan layanan MUSE yang
    digunakan pengguna.
-   Meta AI digunakan melalui aplikasi atau website Meta AI.
-   Pengguna dapat memilih AI secara manual.
-   Rekomendasi AI dapat dikembangkan kemudian.
-   Hasil percakapan dapat dimasukkan kembali ke TASKFLOW melalui
    mekanisme yang didukung.
-   Setiap perubahan data yang berasal dari AI harus mendapatkan
    persetujuan pengguna.

**Batasan:** jangan menganggap kedua layanan sudah memiliki API atau
koneksi otomatis. Mekanisme integrasi harus diverifikasi sebelum
implementasi. AI harus menjadi modul terpisah dan tidak boleh menjadi
ketergantungan fitur inti.

## 7. Technical Architecture

### 7.1 Arsitektur tingkat tinggi

``` text
TASKFLOW
|
+-- Android Native
|   +-- Kotlin
|   +-- Jetpack Compose
|   +-- Room / SQLite
|   +-- WorkManager
|   +-- DataStore
|
+-- Web / PWA
|   +-- Next.js
|   +-- TypeScript
|   +-- IndexedDB
|   +-- Service Worker
|
+-- Local Data & Sync Layer
|   +-- Repository
|   +-- Outbox Queue
|   +-- Incremental Sync
|   +-- Conflict Resolver
|
+-- Backend Services
|   +-- Authentication
|   +-- Sync API
|   +-- Validation
|   +-- Backup
|
+-- Supabase
    +-- Auth
    +-- PostgreSQL
    +-- Row Level Security
    +-- Storage (jika diperlukan)
```

### 7.2 Android stack

  -----------------------------------------------------------------------
  Komponen                Teknologi               Fungsi
  ----------------------- ----------------------- -----------------------
  Programming             Kotlin                  Bahasa utama

  UI                      Jetpack Compose         Native interface

  Architecture            MVVM + Repository       Pemisahan tanggung
                                                  jawab

  Local database          Room / SQLite           Penyimpanan offline

  Preferences             DataStore               Pengaturan

  Background tasks        WorkManager             Sync dan pekerjaan
                                                  fleksibel

  Exact reminders         AlarmManager            Pengingat tepat waktu
                                                  bila diperlukan

  Navigation              Navigation Compose      Navigasi aplikasi
  -----------------------------------------------------------------------

Minimum Android yang direkomendasikan: API 26 (Android 8.0), dengan
pengujian pada versi lebih baru dan variasi vendor.

### 7.3 Web/PWA stack

  Komponen     Teknologi        Fungsi
  ------------ ---------------- ----------------------------------
  Framework    Next.js          Aplikasi web
  Language     TypeScript       Type safety
  UI           React            Antarmuka
  Local data   IndexedDB        Penyimpanan offline
  PWA          Service Worker   Cache aplikasi dan offline shell
  Hosting      Vercel           Deployment web

Service Worker saja tidak cukup untuk offline-first. Data aplikasi harus
dikelola melalui IndexedDB.

### 7.4 Backend

-   Supabase Auth untuk autentikasi.
-   PostgreSQL untuk cloud database.
-   Row Level Security untuk isolasi data.
-   API/RPC khusus untuk sinkronisasi dan validasi.
-   Supabase Storage hanya jika kebutuhan lampiran ditambahkan.
-   Vercel untuk hosting web.
-   GitHub untuk source control.

**Catatan penting:** Supabase tidak otomatis menyinkronkan Room di
Android atau IndexedDB di browser. Sync Engine, queue, retry,
idempotency, dan conflict resolution harus dikembangkan secara khusus.

## 8. Database Design

### 8.1 Entitas utama

  Tabel             Fungsi
  ----------------- --------------------------
  profiles          Profil pengguna
  user_settings     Pengaturan aplikasi
  devices           Perangkat yang terhubung
  projects          Data proyek
  categories        Kategori
  tasks             Data pekerjaan
  subtasks          Checklist tugas
  notes             Data catatan
  tags              Label
  task_tags         Relasi task dan tag
  note_tags         Relasi note dan tag
  note_task_links   Relasi note dan task

### 8.2 Entitas operasional

  Tabel                  Fungsi
  ---------------------- ------------------------------------
  sync_changes           Catatan perubahan sync
  sync_devices           Cursor/status sync perangkat
  sync_conflicts         Konflik yang menunggu penyelesaian
  task_history           Riwayat perubahan task
  reminders              Jadwal pengingat
  notification_rules     Preferensi notifikasi
  notification_history   Riwayat notifikasi
  backups                Metadata backup
  backup_jobs            Status proses backup/restore

### 8.3 Field standar

Setiap objek data utama minimal memiliki:

-   `id`: UUID.
-   `user_id`: pemilik data.
-   `created_at`.
-   `updated_at`.
-   `revision`: nomor revisi.
-   `deleted_at`: soft delete.

### 8.4 Aturan database

1.  Semua data pribadi memiliki `user_id`.
2.  Semua tabel data pengguna dilindungi RLS.
3.  Relasi menggunakan foreign key dan aturan penghapusan yang jelas.
4.  Indeks mengikuti pola pencarian dan sinkronisasi.
5.  Perubahan data meningkatkan revision.
6.  Penghapusan menghasilkan informasi yang dapat disinkronkan.
7.  Sync endpoint memvalidasi kepemilikan data.
8.  Operasi sync harus idempotent.
9.  Backup menyimpan versi format dan metadata integritas.
10. Migrasi database harus tercatat dan dapat diuji.

Schema SQL yang dibuat saat implementasi merupakan migration awal, bukan
sekadar membuat tabel tanpa constraint, index, RLS, dan pengujian.

## 9. Security & Privacy Requirements

### Authentication

-   Password tidak disimpan plaintext.
-   Gunakan mekanisme autentikasi yang aman.
-   Token Android disimpan menggunakan secure storage.
-   Session browser menggunakan mekanisme cookie/token yang aman sesuai
    arsitektur.
-   Logout menangani sesi dan cache sensitif dengan benar.

### Database

-   RLS aktif pada seluruh tabel pribadi.
-   Pengguna A tidak dapat membaca atau mengubah data pengguna B.
-   Supabase service role key tidak boleh ada di APK atau frontend.
-   Validasi dilakukan di server, bukan hanya UI.

### Network

-   Semua komunikasi menggunakan HTTPS.
-   Error tidak membocorkan secret atau data pribadi.
-   Validasi input dan pembatasan request diterapkan.
-   Operasi yang dapat diulang menggunakan idempotency key.

### Local storage

-   Data sensitif dilindungi sesuai tingkat risikonya.
-   Credential tidak disimpan di SharedPreferences biasa atau file
    konfigurasi.
-   Backup lokal dapat dilindungi dengan mekanisme yang sesuai.
-   Data lokal tidak dihapus hanya karena server sementara tidak
    tersedia.

### Privacy

-   Tidak ada pengiriman data ke AI pada MVP.
-   Hindari analytics pihak ketiga yang mengumpulkan data pribadi tanpa
    kebutuhan dan persetujuan.
-   Penghapusan akun harus menjelaskan konsekuensi terhadap cloud dan
    data lokal.

## 10. Non-Functional Requirements

  -----------------------------------------------------------------------
  ID                      Kategori                Target
  ----------------------- ----------------------- -----------------------
  NFR-001                 Performance             Target startup \<2
                                                  detik pada perangkat
                                                  uji memadai

  NFR-002                 Local save              Target \<300 ms untuk
                                                  operasi normal

  NFR-003                 Offline                 Fitur inti tersedia
                                                  offline setelah setup

  NFR-004                 Reliability             Tidak ada silent data
                                                  loss

  NFR-005                 Sync                    Incremental sync, bukan
                                                  full download berulang

  NFR-006                 Security                RLS dan validasi server
                                                  wajib

  NFR-007                 Usability               Operasi inti mudah
                                                  dilakukan melalui
                                                  Android

  NFR-008                 Compatibility           Android API 26+

  NFR-009                 Web                     PWA responsif untuk
                                                  laptop

  NFR-010                 Maintainability         Modul dan service
                                                  terpisah

  NFR-011                 Recovery                Backup dapat divalidasi
                                                  dan dipulihkan

  NFR-012                 Efficiency              Penggunaan baterai dan
                                                  data terkendali
  -----------------------------------------------------------------------

Target performa merupakan target pengujian, bukan jaminan pada seluruh
perangkat dan kondisi jaringan.

## 11. Error Handling & Risk Mitigation

  -----------------------------------------------------------------------
  Risiko                              Penanganan
  ----------------------------------- -----------------------------------
  Internet terputus                   Simpan lokal dan antrekan sync

  Server tidak tersedia               Pertahankan data lokal dan retry

  Sync terhenti                       Lanjutkan perubahan yang belum
                                      dikonfirmasi

  Data terduplikasi                   Idempotency key dan unique
                                      constraint

  Konflik dua perangkat               Simpan versi dan minta keputusan
                                      pengguna

  Aplikasi tertutup mendadak          Transaksi database dan draft
                                      persistence

  Login kedaluwarsa                   Autentikasi ulang tanpa menghapus
                                      data lokal

  Backup gagal                        Tampilkan status gagal, jangan
                                      klaim berhasil

  Restore gagal                       Validasi sebelum mengganti data
                                      aktif

  Pengingat tidak muncul              Periksa izin, jadwal, dan batasan
                                      OS

  Perangkat hilang                    Pulihkan dari cloud backup atau
                                      perangkat tersinkronisasi
  -----------------------------------------------------------------------

Setiap error harus menyediakan:

-   Penjelasan sederhana.
-   Waktu kejadian.
-   Status dampak terhadap data.
-   Langkah pemulihan.
-   Kode referensi untuk debugging jika diperlukan.

Contoh pesan:

> Sinkronisasi belum berhasil. Data Anda tetap tersimpan di perangkat
> ini. TASKFLOW akan mencoba kembali ketika koneksi tersedia.

## 12. Testing & Quality Assurance

### 12.1 Functional testing

-   Registrasi, login, logout, reset password.
-   CRUD Task.
-   CRUD Notes.
-   Subtasks.
-   Projects dan Categories.
-   Search dan filter.
-   Kalender.
-   Pengingat.
-   Backup dan Restore.

### 12.2 Offline testing

  Test                           Hasil yang diwajibkan
  ------------------------------ -----------------------
  Membuat task tanpa internet    Tersimpan lokal
  Mengedit task tanpa internet   Perubahan bertahan
  Membuat notes tanpa internet   Tersimpan lokal
  Menutup aplikasi mendadak      Data tidak hilang
  Restart perangkat              Data lokal tersedia
  Koneksi kembali                Sync berjalan
  Sync terputus di tengah        Tidak ada duplikasi
  Dua perangkat mengubah data    Konflik terdeteksi

### 12.3 Security testing

-   Pengguna tidak dapat mengakses data pengguna lain.
-   RLS bekerja pada seluruh tabel pribadi.
-   API secret tidak ditemukan di APK maupun frontend.
-   Session invalid ditolak.
-   Input berbahaya ditangani.
-   Operasi tanpa izin ditolak server.

### 12.4 Recovery testing

-   Export seluruh data.
-   Restore ke instalasi baru.
-   Memulihkan backup valid.
-   Menolak backup rusak.
-   Menguji kompatibilitas format backup lama.
-   Memastikan restore tidak menggandakan data.

### 12.5 Device testing

-   Android dengan RAM terbatas.
-   Android dengan versi OS berbeda.
-   Android dengan mode hemat baterai.
-   Android dengan jaringan tidak stabil.
-   Laptop menggunakan Chrome.
-   PWA setelah pertama kali dimuat dan kemudian offline.

## 13. Roadmap Pengembangan

### Phase 1 --- Product Foundation (P0)

-   Finalisasi UI/UX dan navigasi.
-   Setup repository GitHub.
-   Setup Android dan Web/PWA.
-   Setup Supabase.
-   Database migrations.
-   Authentication dan security baseline.

**Output:** aplikasi dasar dan akun pengguna berfungsi.

### Phase 2 --- Core Productivity (P0)

-   Dashboard.
-   Task Management.
-   Notes Management.
-   Projects dan Categories.
-   Search dan filter.

**Output:** fitur inti dapat digunakan secara online.

### Phase 3 --- Offline Engine (P0)

-   Room dan IndexedDB.
-   Outbox queue.
-   Sync API.
-   Incremental sync.
-   Retry dan idempotency.
-   Conflict detection dan resolution.

**Output:** data dapat digunakan offline dan disinkronkan.

### Phase 4 --- Calendar & Notifications (P0)

-   Calendar views.
-   Task reminders.
-   Local notifications.
-   Notification settings.

**Output:** pengelolaan jadwal dan pengingat berfungsi.

### Phase 5 --- Backup & Web Access (P0)

-   Backup cloud.
-   Export/import.
-   Restore validation.
-   PWA laptop.
-   Cross-device testing.

**Output:** data dapat dipulihkan dan digunakan lintas perangkat.

### Phase 6 --- QA & Release (P0)

-   Functional testing.
-   Offline testing.
-   Security testing.
-   Recovery testing.
-   Bug fixing.
-   Dokumentasi penggunaan dan pemulihan.
-   Release MVP.

**Output:** TASKFLOW MVP siap digunakan.

### Phase 7 --- Future Updates (P1/P2)

-   Integrasi AI melalui chat MUSE dan Meta AI.
-   Productivity Insights.
-   Advanced recurring tasks.
-   Android widgets.
-   Fitur tambahan berdasarkan kebutuhan nyata.

**Output:** peningkatan produk tanpa merusak sistem inti.

## 14. Definition of Done

MVP belum boleh dinyatakan selesai hanya karena aplikasi bisa dibuka.

-   [ ] Pengguna dapat registrasi dan login.
-   [ ] Pengguna dapat membuat dan mengelola task.
-   [ ] Pengguna dapat membuat dan mengelola notes.
-   [ ] Task dan notes tetap tersimpan setelah aplikasi ditutup.
-   [ ] Fitur inti berjalan tanpa internet.
-   [ ] Perubahan offline dapat disinkronkan setelah online.
-   [ ] Sinkronisasi antarperangkat berjalan dua arah.
-   [ ] Sync terputus tidak menghasilkan duplikasi.
-   [ ] Konflik data tidak diselesaikan dengan silent overwrite.
-   [ ] Pengingat lokal berfungsi sesuai izin perangkat.
-   [ ] Kalender menampilkan data yang benar.
-   [ ] Backup dan restore berhasil diuji.
-   [ ] RLS dan isolasi data telah diuji.
-   [ ] Tidak ada secret bocor ke aplikasi client.
-   [ ] PWA dapat diakses dari laptop.
-   [ ] Seluruh pengujian P0 lulus.
-   [ ] Dokumentasi error handling dan recovery tersedia.

## 15. Dokumentasi dan Handover

  Dokumen                 Isi
  ----------------------- -----------------------------------------
  README                  Instalasi dan cara menjalankan aplikasi
  PRD                     Kebutuhan produk
  Architecture Document   Struktur dan alur sistem
  Database Schema         Tabel, relasi, index, RLS
  API Documentation       Endpoint dan kontrak data
  Sync Specification      Queue, retry, conflict resolution
  Security Document       Auth, permission, secret management
  QA Test Plan            Skenario dan hasil pengujian
  Deployment Guide        Cara deploy dan update
  Recovery Guide          Penanganan kegagalan dan pemulihan
  Changelog               Riwayat perubahan versi

Dokumentasi harus dapat dipahami pengembang lain. Recovery Guide harus
memiliki bagian yang dapat dipahami pengguna awam.

## 16. Risiko Arsitektur yang Wajib Diawasi

### 16.1 Offline-first bukan sekadar cache

Aplikasi membutuhkan database lokal, queue, mekanisme sync, dan conflict
resolver yang benar. Tanpa itu, aplikasi berisiko kehilangan perubahan.

### 16.2 Backup bukan pengganti sinkronisasi

Backup harus memiliki mekanisme pemulihan sendiri. Sinkronisasi yang
salah dapat menyebarkan penghapusan atau kerusakan data ke perangkat
lain.

### 16.3 Android dan PWA berbeda

PWA memiliki batasan browser, penyimpanan lokal, dan background
execution. Kemampuan offline harus diuji, bukan diasumsikan sama dengan
aplikasi native.

### 16.4 Notifikasi dipengaruhi sistem operasi

Izin pengguna, optimasi baterai, pembatasan background, dan versi
Android dapat memengaruhi ketepatan pengingat.

### 16.5 AI tidak boleh menjadi ketergantungan sistem inti

TASKFLOW harus tetap berfungsi penuh meskipun MUSE atau Meta AI tidak
tersedia.

## 17. Keputusan Produk Baseline

  Area                     Keputusan
  ------------------------ ---------------------------------
  Nama kerja               TASKFLOW
  Produk                   Personal Productivity Workspace
  Target                   Penggunaan pribadi
  Platform utama           Android Native
  Platform pendamping      Web/PWA laptop
  Bahasa                   Indonesia dan Inggris
  Authentication           Email dan password
  Database lokal Android   Room / SQLite
  Database lokal Web       IndexedDB
  Cloud backend            Supabase
  Web hosting              Vercel
  Source control           GitHub
  Architecture             Offline-first
  Sync                     Incremental, bidirectional
  Conflict                 Tidak boleh silent overwrite
  Backup                   Cloud + local export
  Notification             Local-first
  AI                       Future update
  Monetization             Tidak diperlukan untuk MVP

## 18. Evaluasi dan Status PRD

PRD ini menjadi baseline produk dan handover awal. Dokumen ini belum
menggantikan spesifikasi implementasi teknis.

Sebelum implementasi penuh, tiga artefak turunan wajib diselesaikan:

1.  Wireframe dan desain UI/UX.
2.  Kontrak API dan spesifikasi Sync Engine.
3.  Database migrations, RLS, serta strategi backup/restore.

### Aturan perubahan

-   Jangan melakukan redesign atau refactor besar tanpa persetujuan
    pemilik produk.
-   Jangan menambahkan fitur di luar PRD tanpa persetujuan.
-   Setiap perubahan harus dicatat di changelog.
-   Evaluasi dampak perubahan terhadap data, sinkronisasi, keamanan, dan
    kompatibilitas.
-   Jangan mengklaim fitur selesai sebelum acceptance criteria dan
    pengujian terkait lulus.

**Status akhir:** TASKFLOW PRD v1.0 --- baseline pengembangan MVP.
Integrasi AI ditunda ke pembaruan berikutnya.
