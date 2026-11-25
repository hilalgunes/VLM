# VLM karşılaştırması — 27 Ekim 2026

Samsung Galaxy Note10+ (SM-N975F / Exynos 9825), Android API 31, 8 CPU çekirdeği. Aynı 512×512 PNG dosyaları, aynı sorular, bağımsız bağlamlar, 4096 bağlam sınırı, en çok 64 çıktı tokenı, greedy örnekleme ve 4 CPU iş parçacığı kullanıldı. Bir ısınma isteği sonuçlardan çıkarıldı. Her görev üç kez tekrarlandı. Süreler motor bellekteyken ölçüldü; soğuk yükleme süresi bu tablonun dışında.

| Model | Tam doğru | İlk yanıt medyanı | Tam yanıt medyanı | Tepe RSS | CPU medyanı (8 çekirdek toplamı) |
|---|---:|---:|---:|---:|---:|
| SmolVLM2-500M-Q8_0 | 3/9 | 12.58 sn | 12.87 sn | 0.76 GiB | %49.5 |
| gemma-3n-E2B | 9/9 | 8.55 sn | 8.58 sn | 2.72 GiB | %33.7 |
| Qwen2.5-VL-3B-Q4_K_M | 9/9 | 125.62 sn | 127.08 sn | 2.95 GiB | %49.5 |

## Görev bazında sonuçlar

| Model | Görev | Doğru | Tam yanıt medyanı |
|---|---|---:|---:|
| SmolVLM2-500M-Q8_0 | count_en | 0/3 | 12.87 sn |
| SmolVLM2-500M-Q8_0 | ocr_en | 3/3 | 13.06 sn |
| SmolVLM2-500M-Q8_0 | count_tr | 0/3 | 12.70 sn |
| gemma-3n-E2B | count_en | 3/3 | 8.53 sn |
| gemma-3n-E2B | ocr_en | 3/3 | 9.06 sn |
| gemma-3n-E2B | count_tr | 3/3 | 8.56 sn |
| Qwen2.5-VL-3B-Q4_K_M | count_en | 3/3 | 125.76 sn |
| Qwen2.5-VL-3B-Q4_K_M | ocr_en | 3/3 | 127.35 sn |
| Qwen2.5-VL-3B-Q4_K_M | count_tr | 3/3 | 127.18 sn |

## Görevler ve gerçek yanıtlar

Her model için İngilizce sayma, İngilizce OCR ve Türkçe sayma çalıştırıldı. Sayma görselinde iki mavi daire ve bir kırmızı kare var; doğru sayı **2**. OCR görselinde doğru kod **4827**. “Tam doğru” puanı yalnızca baştaki/sondaki boşluklar kaldırıldıktan sonra beklenen kısa yanıtla birebir eşleşir. Ek açıklama içindeki tek doğru sayı ayrıca `numeric_correct` alanında tutulur.

| Model | Görev | Tekrar | Beklenen | Ham yanıt |
|---|---|---:|---|---|
| SmolVLM2-500M-Q8_0 | count_en | 1 | 2 |  3 |
| SmolVLM2-500M-Q8_0 | ocr_en | 1 | 4827 |  4827 |
| SmolVLM2-500M-Q8_0 | count_tr | 1 | 2 |  3 |
| SmolVLM2-500M-Q8_0 | count_en | 2 | 2 |  3 |
| SmolVLM2-500M-Q8_0 | ocr_en | 2 | 4827 |  4827 |
| SmolVLM2-500M-Q8_0 | count_tr | 2 | 2 |  3 |
| SmolVLM2-500M-Q8_0 | count_en | 3 | 2 |  3 |
| SmolVLM2-500M-Q8_0 | ocr_en | 3 | 4827 |  4827 |
| SmolVLM2-500M-Q8_0 | count_tr | 3 | 2 |  3 |
| gemma-3n-E2B | count_en | 1 | 2 | 2 |
| gemma-3n-E2B | ocr_en | 1 | 4827 | 4827 |
| gemma-3n-E2B | count_tr | 1 | 2 | 2 |
| gemma-3n-E2B | count_en | 2 | 2 | 2 |
| gemma-3n-E2B | ocr_en | 2 | 4827 | 4827 |
| gemma-3n-E2B | count_tr | 2 | 2 | 2 |
| gemma-3n-E2B | count_en | 3 | 2 | 2 |
| gemma-3n-E2B | ocr_en | 3 | 4827 | 4827 |
| gemma-3n-E2B | count_tr | 3 | 2 | 2 |
| Qwen2.5-VL-3B-Q4_K_M | count_en | 1 | 2 | 2 |
| Qwen2.5-VL-3B-Q4_K_M | ocr_en | 1 | 4827 | 4827 |
| Qwen2.5-VL-3B-Q4_K_M | count_tr | 1 | 2 | 2 |
| Qwen2.5-VL-3B-Q4_K_M | count_en | 2 | 2 | 2 |
| Qwen2.5-VL-3B-Q4_K_M | ocr_en | 2 | 4827 | 4827 |
| Qwen2.5-VL-3B-Q4_K_M | count_tr | 2 | 2 | 2 |
| Qwen2.5-VL-3B-Q4_K_M | count_en | 3 | 2 | 2 |
| Qwen2.5-VL-3B-Q4_K_M | ocr_en | 3 | 4827 | 4827 |
| Qwen2.5-VL-3B-Q4_K_M | count_tr | 3 | 2 | 2 |

## Yorumlama sınırları

- Bu, iki sentetik görselle yapılan üç görevlik pilot testtir. Üç tekrar üç farklı örnek değildir; buradaki doğruluk genel VLM başarımı olarak yorumlanamaz.
- Gemma: LiteRT-LM 0.18.1, CPU dil modeli + GPU görsel kodlayıcı. SmolVLM ve Qwen: sabitlenmiş llama.cpp, CPU dil modeli + CPU görsel kodlayıcı. Dolayısıyla tablo, model ve motor birleşimlerini karşılaştırır; yalnız model mimarisini izole etmez.
- llama.cpp taşınabilir ARM64 CPU derlemesi kullanıldı (GGML_NATIVE=OFF, GPU offload yok); cihaza özel Vulkan/OpenCL/KleidiAI optimizasyonu yapılmadı. Bu süreler optimize edilmiş bütün dağıtımların ulaşabileceği en iyi hız değildir.
- Aynı 512×512 görüntü, her modelin kendi görsel ön işlemcisinden geçti. Native günlüklerinde SmolVLM görüntü başına 64, Qwen 324 görsel token üretti; aynı dosyayı kullanmak aynı görsel hesaplama bütçesi anlamına gelmez.
- Gemma int4, Qwen Q4_K_M, SmolVLM Q8_0; görsel kodlayıcıların GGUF nicemlemesi Q8_0. Sayısal formatlar ve görsel tokenizasyonları eşit değil.
- Tepe RSS, yaklaşık 200 ms aralıkla /proc/self/status üzerinden örneklenen süreç yerleşik belleğidir. Paylaşılan sayfaları içerir ve PSS değildir. GPU sürücüsünün ayırdığı her bellek sayfasını kapsadığı varsayılmaz; özellikle Gemma GPU belleği nedeniyle RAM değerleri tek başına toplam cihaz maliyetini göstermez. Gemma için istek sonrası PSS de ham JSON içinde kayıtlıdır.
- CPU, her isteğin süreç CPU zamanı / geçen süre / 8 ile hesaplandı; çekirdeklerin farklı hızlarını ağırlıklandırmaz. GPU sayacı bütün cihazı ölçer, uygulamaya özel değildir. Yaklaşık saniyelik örnekleme kısa GPU tepelerini kaçırabilir.
- Modeller sırayla SmolVLM → Gemma → Qwen çalıştırıldı. Isı/önbellek/sıra etkileri bütünüyle kontrol edilmedi. Batarya sıcaklığı kaydedildi; bu değer işlemci sıcaklığı değildir.
- İlk yanıt, ilk boş olmayan çıktı parçasına kadarki süredir; LiteRT bir parçada birden fazla token döndürebilir. Motorlar farklı tokenlaştırıcı kullandığından token/sn üzerinden doğrudan hız sıralaması yapılmadı.
- Her isteğin arasında 3 saniye bekleme var. Model yüklemesi ve dosya indirme/aktarma zamanları yanıt sürelerine dahil değil. `load_ms` her motorun initialize işlemini ölçer ve motorlar arasında eşdeğer soğuk başlatma garantisi taşımaz.
- `*-status.json` dosyaları beklenen ve tamamlanan ölçüm sayısını, hata durumlarını gösterir. Test sırasında hata veren bir çalışma başarılı sonuç olarak sunulmaz.

## Deney devam ettirme notu

Qwen’in ilk oturumu 20 dakikalık araç süre sınırında son isteğin ortasında durdu. Sekiz tamamlanmış istek korundu. Eksik dokuzuncu istek, aynı motor ayarlarıyla yeniden yükleme ve sonuçlardan çıkarılan bir ısınma isteğinden sonra tamamlandı. Harici örnekleme CSV’sinde iki oturum ardışık eklenmiştir; elapsed_s aradaki yeniden kurulum boşluğunu yansıtmaz. Her isteğin kendi süre/CPU/RSS ölçümü kesintisizdir.

## Dosyalar

- `runs.csv`: tüm sorular, ham yanıtlar ve istek ölçümleri.
- `summary.csv` / `summary.json`: model bazında özet.
- `*-samples.csv`: zaman damgalı süreç/GPU/ısı örnekleri.
- `*.jsonl`: ham sonuçlar. `*-runner.log`: çalışma zamanı günlükleri.
- `../../model-manifest.json`: GGUF kaynağı, sabit revizyon, boyut ve SHA-256. Test görselleri `../../fixtures` klasöründedir.
