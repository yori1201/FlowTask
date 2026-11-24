# TaskFlow — R8/ProGuard rules (B-01: minify release aktif)

# Simpan nomor baris untuk crash report yang bisa dibaca, sembunyikan nama file sumber.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Model Firestore dipetakan lewat refleksi (toObject) — anotasi @Keep sudah
# melindunginya, tetapi jaga konstruktor tanpa-argumen secara eksplisit agar
# deserialisasi tidak pecah oleh R8.
-keep class com.example.model.** {
    public <init>();
    <fields>;
}

# Firestore menggunakan generik saat decode koleksi.
-keepattributes Signature,InnerClasses,EnclosingMethod

# Firebase Auth / Firestore membawa consumer rules sendiri; tampilkan peringatan
# dependensi opsional yang tidak dipakai alih-alih gagal build.
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
