# Susun Kata

Game susun kata (word connect) berbahasa Indonesia untuk Android. Geser jari melewati huruf di roda untuk
menemukan kata-kata yang tersembunyi di kisi, kumpulkan koin, pakai petunjuk, dan selesaikan 100 tingkat.

- **Pengembang:** Hanz Dev Official
- **Kontak:** hanzzcodee@gmail.com
- **Dukung:** https://saweria.co/hanzreally
- **Bahasa pemrograman:** Kotlin (tanpa library pihak ketiga; hanya Android SDK)
- **Android:** 7.0 (API 24) ke atas, target SDK 34

## Fitur
- 100 tingkat, tingkat 1 mirip contoh (huruf L-A-U-T), makin lama makin banyak huruf (4 sampai 7) dan kata
- Roda huruf dengan geser jari, mundur untuk membatalkan huruf, tombol acak
- Kata bonus (kata di kamus yang tidak ada di kisi) memberi koin
- Petunjuk: buka 1 huruf (25 koin), pilih kotak sendiri (50 koin), buka 5 huruf (100 koin), hadiah harian +50 koin
- 6 latar pemandangan Indonesia (sawah terasering, pantai, gunung, hutan, danau senja, malam)
- Efek suara + musik latar, getar, opsi "Kurangi efek gerak"
- Progres tersimpan otomatis (termasuk di tengah tingkat)
- Layar Tentang & Dukung (email dan Saweria)

## Build RELEASE di GitHub (manual, hanya APK)
Build tidak jalan otomatis. Setiap kali kamu mau membuat APK:
1. Buat repositori di GitHub dan unggah seluruh isi folder ini.
2. Buka tab **Actions > Build Release APK > Run workflow**.
3. Setelah selesai, unduh `SusunKata-v1.0.0-release.apk` dari bagian **Artifacts**.
   Centang opsi `buat_release` bila APK ingin dilampirkan ke halaman Releases (tag `v1.0.0`).

Yang dibangun hanya APK versi release (`assembleRelease`, minify dan shrink resources aktif,
`debuggable = false`). Tidak ada build debug dan tidak ada AAB. Versi aplikasi: **1.0.0** (versionCode 1).

Keystore: tanpa pengaturan tambahan, workflow membuat keystore sementara agar APK release bisa dipasang.
APK dari dua build berbeda tidak bisa saling menimpa. Agar update bisa dipasang di atas versi lama, pakai
keystore milikmu sendiri:
```
keytool -genkeypair -v -keystore release.jks -alias susunkata -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks
```
Di GitHub: **Settings > Secrets and variables > Actions**, tambahkan `KEYSTORE_BASE64` (hasil base64),
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Simpan `release.jks` dengan aman, jangan di-commit.

## Build lokal
Butuh JDK 17 dan Android SDK 34, lalu: `gradle assembleRelease` (Gradle 8.9).
APK ada di `app/build/outputs/apk/release/`. Untuk menandatangani lokal, buat `keystore.properties`
berisi `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

## Menambah kata dan tingkat
- Tambah kata di `tools/kata_sumber.txt` (3 sampai 7 huruf, satu spasi atau baris baru sebagai pemisah).
- Jalankan `python3 tools/buat_level.py`. Skrip ini membuat `assets/kata.txt` dan `assets/levels.json`, lalu
  memverifikasi setiap tingkat: semua kata bisa dibentuk dari huruf roda, tidak ada kata liar terbentuk di
  kisi, dan semua kata saling tersambung. Skrip gagal bila ada tingkat yang tidak valid.
- Aset gambar dan suara dibuat ulang dengan `python3 tools/buat_aset.py` (butuh Pillow, numpy, ffmpeg).

## Desain
Arah desain, palet, dan alasan tiap keputusan ada di [DESIGN.md](DESIGN.md). Kontras warna dicek dengan
`python3 tools/cek_kontras.py`. Papan ketik didukung: ketik huruf roda lalu Enter, Tab atau panah untuk
berpindah fokus, Esc untuk menutup.

## Aset dan lisensi
Semua aset dibuat prosedural lewat skrip di `tools/` (latar, ikon, efek suara, musik), tanpa gambar atau
audio dari pihak ketiga dan tanpa generator gambar AI. Font Poppins (SIL OFL 1.1) disertakan di `assets/fonts/`.
