# Fiziksel cihaz ölçümü — 23 Ekim 2026

Cihaz: Samsung SM-N975F, universal9825, 8 CPU çekirdeği, MemTotal 11692324 KiB.
Model: gemma-3n-E2B-it-int4.litertlm. Önceden yüklenmiş motor ve mevcut fotoğrafla yeni açıklama sorusu gönderildi; soğuk başlangıç ölçülmedi.

CSV: yaklaşık saniyelik /proc/PID/stat CPU zaman farkı (CLK_TCK=100), /proc/uptime, VmRSS ve Mali sysfs utilization/clock sayaçları. CPU yüzde 100 tek çekirdektir; toplam kapasite için 8'e bölünmüştür. GPU sayacı tüm cihaz içindir, uygulamaya özel değildir. Yaklaşık saniyelik örnekleme kısa tepeleri kaçırabilir. Profil aracı ve ekran paylaşımı da çalışıyordu.

Ayrıntılı dumpsys meminfo ölçümleri: test öncesi PSS 4258324 KiB, RSS 4364002 KiB, Graphics 378055 KiB; yanıt sırasında PSS 4127144 KiB, RSS 4229762 KiB, Graphics 369859 KiB. Grafik belleği toplam PSS/RSS hesabına zaten dahildir; eklenmemelidir. Model dosyası eşlemeleri belleğin büyük kısmını oluşturur. Model bellekte tutulduğu için işlem bitince RAM'in aynı seviyede kalması tek başına sızıntı göstermez.

Bu kayıt tek istekten alınmıştır; kapsamlı cihaz kıyaslaması veya uzun süreli sızıntı testi değildir.
