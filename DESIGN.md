# DESIGN.md: Susun Kata

Arah desain berasal dari permintaan pemilik: game susun kata Android seperti tangkapan layar contoh
(kotak kata di atas pemandangan, roda huruf di bawah), dengan aset yang bagus. Meniru susunan layar contoh
diminta langsung oleh pemilik. Tampilan, palet, dan bentuknya dibuat sendiri.

Design Read: game puzzle kata untuk pemain kasual berbahasa Indonesia di ponsel, bahasa visual "ubin huruf
bertepi tebal di atas papan gelap, latar pemandangan Indonesia", dial ENERGY 2 / RHYTHM 2 / MOTION 2.

## Motif identitas
Ubin huruf bertepi tebal: persegi bersudut kecil dengan tepi bawah rata berwarna lebih gelap (bukan bayangan
buram). Dipakai berulang: kisi kata, pratinjau kata, judul menu, layar menang, ikon aplikasi.

## Palet (2 warna inti + 1 aksen, sisanya netral)
| Peran | Warna | Alasan satu baris |
|---|---|---|
| Hijau daun `#1F7A3A` | ubin kata yang sudah terbuka, tombol utama | warna daun sawah di latar; putih di atasnya 5.38:1 |
| Tinta `#14211A` | papan di belakang kisi, panel, teks di atas krem | hijau kehitaman agar menyatu dengan latar, bukan hitam murni |
| Krem `#F5F0E1` | ubin tertutup, cakram roda, kartu | warna kertas ubin; tinta di atasnya 14.6:1 |
| Emas `#F2B705` (aksen) | hanya koin, kata bonus, petunjuk aktif, hadiah siap | satu warna khusus untuk "hadiah", tidak dipakai dekorasi |

Tidak ada merah: kata salah memakai ubin tinta yang bergetar, reset progres memakai tombol krem dengan
dialog konfirmasi. Angka kontras: `python3 tools/cek_kontras.py`.

## Keputusan dan alasannya
- Papan tinta di belakang kisi: ubin hijau di atas foto sawah hijau hanya 1.7:1, di atas tinta 3.09:1.
- Cakram roda krem buram penuh dengan cincin tinta: huruf hitam tetap terbaca di atas foto apa pun.
- Tidak ada gradasi pada tombol, ubin, atau penutup. Teks yang berada di atas foto (menu, tingkat, tentang)
  memakai penutup tinta rata 62%; di layar bermain semua teks berada di panel tinta atau kartu krem.
  Kontras terburuk dihitung dari piksel foto yang paling terang (`tools/cek_kontras.py`).
- Tidak ada bayangan buram. Elevasi hanya lewat tepi tebal ubin; kartu krem terpisah dari latar oleh lapisan gelap rata.
- Sudut: ubin dan tombol berlabel 14u, kartu 22u, tombol ikon bulat. Bentuk menunjukkan jenis kontrol:
  persegi bersudut untuk kata dan aksi berlabel, bulat untuk aksi ikon, cakram untuk roda.
- Huruf: Poppins Bold untuk ubin dan judul (goresan rata dan huruf kapital A, U, T, L terbaca jelas pada ukuran
  besar), Poppins Medium untuk teks. Disertakan dari berkas, bukan font bawaan perangkat.
- Ikon aplikasi: ubin KATA 2x2 krem dengan satu ubin emas, latar hijau daun rata. Ikon di dalam game digambar sendiri di kode, masing-masing mewakili fungsinya: roda gigi (pengaturan), panah kiri
  (kembali), target (buka kotak pilihan), satu bohlam (buka 1 huruf), tiga bohlam (buka 5 huruf),
  hadiah (hadiah harian), panah silang (acak), gembok (tingkat terkunci), centang (tingkat selesai).
  Tidak ada bintang atau ikon "ajaib".
- Gerak (tiap gerak punya tujuan): huruf membesar saat dipilih (konfirmasi sentuhan), ubin terbuka memantul
  berurutan (menunjukkan kata mana yang terbuka), getar saat kata salah (kesalahan), konfeti saat tingkat selesai
  (hadiah), ubin judul jatuh sekali saat menu dibuka (memperkenalkan motif). Semua dimatikan oleh
  "Kurangi efek gerak" atau skala animasi sistem 0.
- Satu titik fokus per layar: menu = tombol Main; bermain = roda huruf; menang = tombol tingkat berikutnya.
- Latar pemandangan (sawah terasering, pantai, gunung, hutan, danau senja, malam) dibuat prosedural oleh
  `tools/buat_aset.py`. Alasan: konteks Indonesia, dan tiap tiga tingkat latar berganti sebagai penanda kemajuan.
- Tema tunggal gelap-di-atas-foto: tidak ada tombol tema karena warna datang dari pemandangan.

## Teks
Bahasa Indonesia biasa, kalimat perintah pendek. Tidak ada angka klaim; angka hanya data permainan.
Tidak ada tanda pisah panjang. Tombol menyebut aksinya ("Tingkat berikutnya", "Reset progres").
