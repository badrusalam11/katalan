# Headless Mode yang Diblokir WAF (`--insecure` dan `--user-agent`)

Beberapa website memakai WAF (Web Application Firewall) atau layanan anti-bot yang memblokir browser headless. Test yang lulus di mode headed jadi gagal di mode `--headless`. Biasanya halaman yang muncul adalah halaman blokir, bukan halaman aplikasi:

```
Access denied
This request was blocked by our security service
```

Di log, gejalanya adalah element pertama di halaman (misalnya field login) tidak ditemukan:

```
WebElementNotFoundException: Web element with id: 'null' located by '//input[@id='username']' not found
```

Cek screenshot error di folder report. Kalau isinya halaman "Access denied", berarti penyebabnya WAF, bukan locator atau script.

## Kenapa headless diblokir

Chrome headless mengumumkan dirinya sebagai headless lewat dua tempat:

| Sinyal | Mode headed | Mode headless |
|--------|-------------|---------------|
| Header `User-Agent` / `navigator.userAgent` | `... Chrome/156.0.0.0 Safari/537.36` | `... HeadlessChrome/156.0.0.0 Safari/537.36` |
| Header `sec-ch-ua` (client hints) | `"Google Chrome";v="156", "Chromium";v="156", ...` | `"HeadlessChrome";v="156", ...` |

WAF cukup membaca salah satunya untuk memblokir request.

## Solusi: `--insecure`

```bash
java -jar katalan-runner.jar run \
  -p . \
  -ts "Test Suites/MySuite" \
  --headless \
  --insecure
```

`--insecure` hanya berlaku bersama `--headless` di Chrome. Tanpa `--headless`, flag ini tidak berpengaruh.

### Apa yang dilakukan `--insecure`

1. Chrome dibuka seperti biasa. Katalan lalu membaca User-Agent asli browser lewat CDP `Browser.getVersion`.
2. Teks `HeadlessChrome` diganti jadi `Chrome`. Bagian lain tidak diubah, jadi versi Chrome, OS, dan arsitektur tetap asli dan selalu cocok dengan Chrome yang terpasang. Tidak ada versi yang di-hardcode.
3. Client hints (`sec-ch-ua`) disusun ulang supaya sama dengan Chrome biasa:
   - `brands`: `Google Chrome`, `Chromium`, `Not?A_Brand`
   - `fullVersionList`: versi lengkap Chrome
   - `platform`: diambil dari OS mesin (`Windows`, `macOS`, atau `Linux`)
   - `architecture`: `x86` atau `arm`
   - `mobile`: `false`
4. Keduanya dipasang lewat CDP `Network.setUserAgentOverride`, sebelum test membuka URL apa pun.
5. User-Agent hasil langkah 2 disimpan selama run berjalan. Browser berikutnya dalam run yang sama, misalnya test case berikutnya yang membuka browser baru, langsung mendapat UA ini saat launch.

Kalau berhasil, log menampilkan:

```
INFO  c.k.core.driver.WebDriverFactory - --insecure: headless User-Agent overridden: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36
```

Kalau override gagal, test tetap jalan dengan UA headless dan log menampilkan:

```
WARN  c.k.core.driver.WebDriverFactory - --insecure: failed to override headless User-Agent: <pesan error>
```

### Batasan

- Hanya untuk Chrome lokal. Firefox, Edge, Safari, dan `--remote-url` (Selenium Grid) tidak terpengaruh.
- Di browser pertama dalam sebuah run, override lewat CDP hanya berlaku di tab utama. Tab atau window baru yang dibuka di browser itu masih memakai UA headless. Mulai browser kedua, UA sudah dipasang saat launch sehingga berlaku di semua tab, tapi client hints tetap hanya dioverride di tab utama.
- `--insecure` hanya menghilangkan penanda "headless" di User-Agent dan client hints. WAF yang memakai deteksi lain, seperti fingerprinting JavaScript, rate limit, atau reputasi IP, masih bisa memblokir. Untuk kasus seperti itu, minta tim security atau infra untuk whitelist IP mesin test.

## Alternatif: `--user-agent "<string>"`

```bash
java -jar katalan-runner.jar run \
  -p . \
  -ts "Test Suites/MySuite" \
  --headless \
  --user-agent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"
```

String yang diberikan dipakai apa adanya sebagai argumen Chrome `--user-agent=...` saat launch.

## Perbandingan

| | `--insecure` | `--user-agent "<string>"` |
|---|---|---|
| Mode | Hanya `--headless` | Headed dan headless |
| Isi User-Agent | Otomatis: UA asli dengan `HeadlessChrome` diganti `Chrome` | Persis string yang kamu tulis |
| Versi Chrome | Selalu sesuai Chrome terpasang | Harus kamu update manual saat Chrome update |
| Client hints `sec-ch-ua` | Ikut dioverride (brand `HeadlessChrome` hilang) | Tidak diubah, jadi headless tetap terdeteksi |
| Berlaku di semua tab | Mulai browser kedua (lihat Batasan) | Ya, sejak launch |
| Cocok untuk | WAF yang memblokir headless | Kebutuhan UA khusus, misalnya testing UA tertentu |

Kalau `--user-agent` dan `--insecure` dipakai bersamaan, `--user-agent` yang menang dan override otomatis dari `--insecure` tidak dijalankan.

## Catatan soal nama

`--insecure` tidak berhubungan dengan sertifikat TLS/SSL. Error sertifikat sudah selalu diabaikan oleh katalan, dengan atau tanpa flag ini (`--ignore-certificate-errors` dan `acceptInsecureCerts`).

Pakai `--insecure` hanya di environment yang memang boleh kamu test secara otomatis. Untuk production, sebaiknya konfirmasi dulu ke tim security.
