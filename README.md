# VLM Android

Kotlin / Jetpack Compose Android uygulaması. Gemma 3n E2B ve E4B **görsel + metin** modellerini indirir; seçilen fotoğrafı ve soruyu LiteRT-LM motoruna birlikte göndererek cihazda yanıt üretir. Sunucu veya bulut çıkarımı kullanılmaz.

## Çalıştırma

- Android Studio ile açın, ARM64 Android 8.0+ fiziksel cihaza kurun.
- Hugging Face üzerinde seçtiğiniz modelin Gemma lisansını kabul edin; o hesaba ait okuma tokenını uygulamaya girin.
- Modeli seçip indirin (E2B yaklaşık 3,66 GB; E4B yaklaşık 4,92 GB). İndirme Android DownloadManager ile arka planda sürer ve uygulama yeniden açıldığında takip edilir. Hücresel veri kullanılabilir.
- Fotoğraf seçin, soru yazın ve yanıt düğmesine basın. Sonraki sorular da seçili fotoğrafla ayrı istek olarak işlenir; önceki soru ve yanıtlar bağlama eklenmez.

E2B için 8 GB+, E4B için 12 GB+ RAM önerilir; gerçek gereksinim cihaza ve çalışma zamanına bağlıdır. Dil motoru CPU, görsel kodlayıcı GPU kullanır. İlk yükleme zaman alabilir. GPU/OpenCL desteği gerekir; her cihazda çalışacağı garanti edilmez. Büyük model dosyaları APK'ya dahil değildir. Token uygulama tercihlerine kaydedilmez; indirme isteğinin yetkilendirme başlığında Android DownloadManager'a iletilir.

## Doğrulama

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Gerçek model indirme ve görselli çıkarım testi, lisans erişimi olan hesap ve uyumlu fiziksel cihaz gerektirir.

## Kaynaklar

- [LiteRT-LM Android API ve çok modlu giriş](https://developers.google.com/edge/litert-lm/android)
- [Gemma 3n E2B dosyaları ve lisansı](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/tree/main)
- [Gemma 3n E4B dosyaları ve lisansı](https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/tree/main)

## Çok modelli karşılaştırma

Gemma, Qwen ve SmolVLM için gerçek cihaz deneyleri, tekrarlanabilir araçlar ve ham sonuçlar [profiling/benchmark](profiling/benchmark/README.md) klasöründedir. Qwen/SmolVLM bu karşılaştırmada ayrı Android native motoruyla çalışır; normal uygulama model listesi Gemma E2B/E4B olmaya devam eder.
