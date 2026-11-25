# Gerçek cihazda çok modelli VLM karşılaştırması

Bu klasör, uygulamaya sahte model seçenekleri eklemeden **gerçek görsel çıkarımı** ile Gemma / Qwen / SmolVLM karşılaştırması yapar. Gemma, uygulamanın LiteRT-LM bağımlılığı üzerinden bir Android instrumentation testiyle; GGUF modelleri Android NDK ile derlenmiş `llama.cpp` + `libmtmd` üzerinden çalışır. Qwen ve SmolVLM normal uygulamanın model listesine henüz eklenmiş değildir; bu deneyin ayrı motoruyla çalışırlar.

## Kaydedilen deney

[27 Ekim 2026 sonuçları](results/2026-10-27/REPORT.md) — tamamlanan ölçümlerden üretilir.

- `fixtures/shapes.png`: iki mavi daire, bir kırmızı kare.
- `fixtures/ocr.png`: dört haneli 4827 kodu.
- `fixtures/cases.json`: değiştirilmeyen ortak sorular ve beklenen yanıtlar.
- 3 görev × 3 tekrar × 3 model; ayrıca model başına sonuçlara dahil edilmeyen bir ısınma isteği.
- Greedy örnekleme, 64 çıktı tokenı üst sınırı, 4096 bağlam sınırı, 4 CPU iş parçacığı; istekler arasında 3 saniye bekleme.

Kalite puanı bu iki sentetik görselin küçük pilot testine aittir. Özellikle aynı görevin üç tekrarı üç bağımsız kalite örneği değildir. Motor, nicemleme, GPU kullanımı ve model boyutu farklılıkları raporda açıklanır.

## Yeniden çalıştırma

Gerekenler: Python 3, Android SDK/NDK, CMake/Ninja, USB hata ayıklaması açık ARM64 Android 9+ cihaz. Gemma E2B uygulamadan önceden indirilmiş olmalı. GGUF modelleri ve görsel kodlayıcıları için toplam yaklaşık 3,32 GB indirme alanı gerekir. `model-manifest.json`, sabit Hugging Face revizyonlarını ve dosya SHA-256 değerlerini içerir.

1. `python3 make_fixtures.py` ile görselleri üretin (üçüncü taraf paket gerektirmez).
2. Resmi [llama.cpp](https://github.com/ggml-org/llama.cpp) kaynağını `de5122d879d00d2895c36be7e0cee94105aeeabd` revizyonunda alın. `build_native.sh` içindeki ortam değişkenlerini ayarlayın ve scripti çalıştırın. Derlenen çalıştırılabilir dosya `$BENCH_BUILD/vlm-bench` olur. Kaynak kod ve büyük model dosyaları projeye kopyalanmaz.
3. `model-manifest.json` içindeki dosyaları `https://huggingface.co/{repo}/resolve/{revision}/{file}` adreslerinden indirin ve SHA-256 değerlerini doğrulayın.
4. Proje kökünde `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` çalıştırın. Uygulama ve test APK'larını `adb install -r` ile kurun. Uygulama verilerini silmeyin; indirdiğiniz Gemma korunmalıdır.
5. Telefonda `/data/local/tmp/vlm-benchmark/models` ve `/sdcard/Android/data/com.hilal.vlm/files/benchmark` klasörlerini hazırlayın. GGUF dosyalarını birinci klasöre; `vlm-bench` ve fixtures içindeki üç dosyayı `/data/local/tmp/vlm-benchmark/` içine kopyalayın. Fixture dosyalarının aynısını uygulamanın benchmark klasörüne de kopyalayın. Çalıştırılabilir dosyaya `chmod 755` verin.
6. Proje kökünde aşağıdaki komutu çalıştırın. `--out` için yeni bir klasör seçin; aynı klasör seçilirse o deneyin çıktıları yeniden yazılır.

```sh
python3 profiling/benchmark/run.py --adb /path/to/adb --out profiling/benchmark/results/my-run --repeats 3
python3 profiling/benchmark/summarize.py profiling/benchmark/results/my-run
```

`--only smol`, `--only gemma`, `--only qwen` ile tek model tekrar çalıştırılabilir. Native modelin yarım kalan deneyi, telefondaki sonuç dosyası korunarak `--resume --only qwen` (veya `smol`) ile devam ettirilebilir; tamamlanan istekler atlanır, yeni motor için yine bir ısınma isteği yapılır. Varsayılan model başına süre sınırı 1800 saniyedir; `--timeout` ile değiştirilebilir. CPU yüzdesinin özet ölçeği bu 8 çekirdekli cihaz içindir; başka çekirdek sayısında özetleme ölçeğini uyarlayın. Runner modelleri sırayla çalıştırır; önceki VLM uygulama sürecini kapatır. GPU sayacı Mali cihazlarda erişilebilirse kaydedilir; başka cihazda eksik olması GPU kullanımı sıfır demek değildir. Isı, batarya sensöründen okunur; CPU sıcaklığı değildir. Ölçümler sırasında telefonu başka işler için kullanmayın.

Native test CPU üzerinde çalışır; Gemma görsel kodlayıcı için GPU kullanır. Bu nedenle sonuçlar model + çalışma motoru konfigürasyonlarını karşılaştırır.

## Kaynaklar

- [LiteRT-LM Android çok modlu API](https://developers.google.com/edge/litert-lm/android)
- [llama.cpp Android derleme](https://github.com/ggml-org/llama.cpp/blob/de5122d879d00d2895c36be7e0cee94105aeeabd/docs/android.md)
- [libmtmd API](https://github.com/ggml-org/llama.cpp/blob/de5122d879d00d2895c36be7e0cee94105aeeabd/tools/mtmd/mtmd.h)
- [SmolVLM2 500M GGUF](https://huggingface.co/ggml-org/SmolVLM2-500M-Video-Instruct-GGUF)
- [Qwen2.5-VL 3B GGUF](https://huggingface.co/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF)
