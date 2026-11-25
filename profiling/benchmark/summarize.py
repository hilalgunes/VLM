"""Create reviewable CSV/JSON/Markdown from actual completed benchmark runs."""
import argparse,csv,hashlib,json,re,statistics
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('results',type=Path);a=p.parse_args()
cases=json.loads((Path(__file__).parent/'fixtures/cases.json').read_text())
expected={c['id']:c['expected'] for c in cases}
allrows=[];summary=[]
for name in ['smol','gemma','qwen']:
 file=a.results/(name+'.jsonl')
 if not file.exists():continue
 rows=[json.loads(l) for l in file.read_text().splitlines() if l.startswith('{')]
 for row in rows:
  answer=row['answer'].strip()
  row['exact_correct']=answer==expected[row['case']]
  numbers=re.findall(r'(?<!\d)\d+(?!\d)',answer)
  row['numeric_correct']=numbers==[expected[row['case']]]
  row['cpu_percent_8_cores']=row['cpu_ms']/row['total_ms']*100/8
  row['first_token_s']=row['first_token_ms']/1000 if row.get('first_token_ms') is not None else None
  row['total_s']=row['total_ms']/1000
  row['peak_rss_gib']=row['peak_rss_kib']/1048576
  allrows.append(row)
 good=[r for r in rows if not r.get('error')]
 if not good:continue
 samples=list(csv.DictReader((a.results/(name+'-samples.csv')).open()))
 # If a request was interrupted then resumed, use the last sampled segment for that key.
 segments={};previous_phase=None
 completed_keys={f"{r['case']}:{r['repeat']}" for r in good}
 for sample in samples:
  phase=sample['phase']
  if phase in completed_keys:
   if phase!=previous_phase:segments[phase]=[]
   segments[phase].append(sample)
  previous_phase=phase
 active=[sample for group in segments.values() for sample in group]
 gpu=[float(s['device_gpu_percent']) for s in active if s['device_gpu_percent'].isdigit()]
 temp=[float(s['battery_temperature_deci_c'])/10 for s in samples if s['battery_temperature_deci_c'].isdigit()]
 summary.append({'key':name,'model':good[0]['model'],'runtime':good[0]['runtime'],'runs':len(rows),
    'errors':sum(bool(r.get('error')) for r in rows),'exact_correct':sum(r['exact_correct'] for r in good),
    'numeric_correct':sum(r['numeric_correct'] for r in good),
    'first_token_s_median':statistics.median(r['first_token_s'] for r in good if r['first_token_s'] is not None),
    'total_s_median':statistics.median(r['total_s'] for r in good),
    'peak_rss_gib':max(r['peak_rss_gib'] for r in good),
    'cpu_percent_8_cores_median':statistics.median(r['cpu_percent_8_cores'] for r in good),
    'device_gpu_mean_percent':statistics.mean(gpu) if gpu else None,'device_gpu_peak_percent':max(gpu) if gpu else None,
    'battery_temp_c_min':min(temp) if temp else None,'battery_temp_c_max':max(temp) if temp else None})
(a.results/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
if allrows:
 keys=['model','runtime','case','repeat','answer','exact_correct','numeric_correct','first_token_s','total_s','peak_rss_gib','cpu_percent_8_cores','error']
 with (a.results/'runs.csv').open('w') as f:
  w=csv.DictWriter(f,fieldnames=keys,extrasaction='ignore');w.writeheader();w.writerows(allrows)
with (a.results/'summary.csv').open('w') as f:
 if summary:
  w=csv.DictWriter(f,fieldnames=list(summary[0]));w.writeheader();w.writerows(summary)
report=['# VLM karşılaştırması — 27 Ekim 2026','',
'Samsung Galaxy Note10+ (SM-N975F / Exynos 9825), Android API 31, 8 CPU çekirdeği. Aynı 512×512 PNG dosyaları, aynı sorular, bağımsız bağlamlar, 4096 bağlam sınırı, en çok 64 çıktı tokenı, greedy örnekleme ve 4 CPU iş parçacığı kullanıldı. Bir ısınma isteği sonuçlardan çıkarıldı. Her görev üç kez tekrarlandı. Süreler motor bellekteyken ölçüldü; soğuk yükleme süresi bu tablonun dışında.','',
'| Model | Tam doğru | İlk yanıt medyanı | Tam yanıt medyanı | Tepe RSS | CPU medyanı (8 çekirdek toplamı) |',
'|---|---:|---:|---:|---:|---:|']
for s in summary:
 report.append(f"| {s['model']} | {s['exact_correct']}/{s['runs']} | {s['first_token_s_median']:.2f} sn | {s['total_s_median']:.2f} sn | {s['peak_rss_gib']:.2f} GiB | %{s['cpu_percent_8_cores_median']:.1f} |")
case_rows=[]
for s in summary:
 for case in cases:
  group=[r for r in allrows if r['model']==s['model'] and r['case']==case['id']]
  if group:
   case_rows.append({'model':s['model'],'case':case['id'],'runs':len(group),'exact_correct':sum(r['exact_correct'] for r in group),
       'total_s_median':statistics.median(r['total_s'] for r in group),'answers':json.dumps([r['answer'].strip() for r in group],ensure_ascii=False)})
with (a.results/'cases.csv').open('w') as f:
 if case_rows:
  w=csv.DictWriter(f,fieldnames=list(case_rows[0]));w.writeheader();w.writerows(case_rows)
report+=['','## Görev bazında sonuçlar','','| Model | Görev | Doğru | Tam yanıt medyanı |','|---|---|---:|---:|']
for r in case_rows:
 report.append(f"| {r['model']} | {r['case']} | {r['exact_correct']}/{r['runs']} | {r['total_s_median']:.2f} sn |")
report+=['','## Görevler ve gerçek yanıtlar','',
'Her model için İngilizce sayma, İngilizce OCR ve Türkçe sayma çalıştırıldı. Sayma görselinde iki mavi daire ve bir kırmızı kare var; doğru sayı **2**. OCR görselinde doğru kod **4827**. “Tam doğru” puanı yalnızca baştaki/sondaki boşluklar kaldırıldıktan sonra beklenen kısa yanıtla birebir eşleşir. Ek açıklama içindeki tek doğru sayı ayrıca `numeric_correct` alanında tutulur.','',
'| Model | Görev | Tekrar | Beklenen | Ham yanıt |','|---|---|---:|---|---|']
for r in allrows:
 ans=r['answer'].replace('|','\\|').replace('\n','<br>')
 report.append(f"| {r['model']} | {r['case']} | {r['repeat']} | {expected[r['case']]} | {ans} |")
report+=['','## Yorumlama sınırları','',
'- Bu, iki sentetik görselle yapılan üç görevlik pilot testtir. Üç tekrar üç farklı örnek değildir; buradaki doğruluk genel VLM başarımı olarak yorumlanamaz.',
'- Gemma: LiteRT-LM 0.18.1, CPU dil modeli + GPU görsel kodlayıcı. SmolVLM ve Qwen: sabitlenmiş llama.cpp, CPU dil modeli + CPU görsel kodlayıcı. Dolayısıyla tablo, model ve motor birleşimlerini karşılaştırır; yalnız model mimarisini izole etmez.',
'- llama.cpp taşınabilir ARM64 CPU derlemesi kullanıldı (GGML_NATIVE=OFF, GPU offload yok); cihaza özel Vulkan/OpenCL/KleidiAI optimizasyonu yapılmadı. Bu süreler optimize edilmiş bütün dağıtımların ulaşabileceği en iyi hız değildir.',
'- Aynı 512×512 görüntü, her modelin kendi görsel ön işlemcisinden geçti. Native günlüklerinde SmolVLM görüntü başına 64, Qwen 324 görsel token üretti; aynı dosyayı kullanmak aynı görsel hesaplama bütçesi anlamına gelmez.',
'- Gemma int4, Qwen Q4_K_M, SmolVLM Q8_0; görsel kodlayıcıların GGUF nicemlemesi Q8_0. Sayısal formatlar ve görsel tokenizasyonları eşit değil.',
'- Tepe RSS, yaklaşık 200 ms aralıkla /proc/self/status üzerinden örneklenen süreç yerleşik belleğidir. Paylaşılan sayfaları içerir ve PSS değildir. GPU sürücüsünün ayırdığı her bellek sayfasını kapsadığı varsayılmaz; özellikle Gemma GPU belleği nedeniyle RAM değerleri tek başına toplam cihaz maliyetini göstermez. Gemma için istek sonrası PSS de ham JSON içinde kayıtlıdır.',
'- CPU, her isteğin süreç CPU zamanı / geçen süre / 8 ile hesaplandı; çekirdeklerin farklı hızlarını ağırlıklandırmaz. GPU sayacı bütün cihazı ölçer, uygulamaya özel değildir. Yaklaşık saniyelik örnekleme kısa GPU tepelerini kaçırabilir.',
'- Modeller sırayla SmolVLM → Gemma → Qwen çalıştırıldı. Isı/önbellek/sıra etkileri bütünüyle kontrol edilmedi. Batarya sıcaklığı kaydedildi; bu değer işlemci sıcaklığı değildir.',
'- İlk yanıt, ilk boş olmayan çıktı parçasına kadarki süredir; LiteRT bir parçada birden fazla token döndürebilir. Motorlar farklı tokenlaştırıcı kullandığından token/sn üzerinden doğrudan hız sıralaması yapılmadı.',
'- Her isteğin arasında 3 saniye bekleme var. Model yüklemesi ve dosya indirme/aktarma zamanları yanıt sürelerine dahil değil. `load_ms` her motorun initialize işlemini ölçer ve motorlar arasında eşdeğer soğuk başlatma garantisi taşımaz.',
'- `*-status.json` dosyaları beklenen ve tamamlanan ölçüm sayısını, hata durumlarını gösterir. Test sırasında hata veren bir çalışma başarılı sonuç olarak sunulmaz.','',
'## Deney devam ettirme notu','',
'Qwen’in ilk oturumu 20 dakikalık araç süre sınırında son isteğin ortasında durdu. Sekiz tamamlanmış istek korundu. Eksik dokuzuncu istek, aynı motor ayarlarıyla yeniden yükleme ve sonuçlardan çıkarılan bir ısınma isteğinden sonra tamamlandı. Harici örnekleme CSV’sinde iki oturum ardışık eklenmiştir; elapsed_s aradaki yeniden kurulum boşluğunu yansıtmaz. Her isteğin kendi süre/CPU/RSS ölçümü kesintisizdir.' if (a.results/'resume-note.json').exists() else 'Bu deney için devam ettirme notu bulunmuyor.',
'',
'## Dosyalar','',
'- `runs.csv`: tüm sorular, ham yanıtlar ve istek ölçümleri.',
'- `summary.csv` / `summary.json`: model bazında özet.',
'- `*-samples.csv`: zaman damgalı süreç/GPU/ısı örnekleri.',
'- `*.jsonl`: ham sonuçlar. `*-runner.log`: çalışma zamanı günlükleri.',
'- `../../model-manifest.json`: GGUF kaynağı, sabit revizyon, boyut ve SHA-256. Test görselleri `../../fixtures` klasöründedir.']
(a.results/'REPORT.md').write_text('\n'.join(report)+'\n')
print(json.dumps(summary,ensure_ascii=False,indent=2))
