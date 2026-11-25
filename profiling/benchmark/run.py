"""Run the three real VLMs sequentially on an attached ARM64 device.
Prerequisites: see README.md. Uses adb via subprocess with argument lists, no local shell.
"""
import argparse,csv,json,shlex,subprocess,time
from pathlib import Path
HERE=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
p.add_argument('--adb',default='adb');p.add_argument('--out',type=Path,required=True)
p.add_argument('--only',nargs='+',default=['smol','gemma','qwen'],choices=['smol','gemma','qwen'])
p.add_argument('--repeats',type=int,default=3)
p.add_argument('--timeout',type=int,default=1800)
p.add_argument('--resume',action='store_true',help='Resume native runs only; a fresh warm-up is excluded')
a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
assert 1<=a.repeats<=10
NATIVE='/data/local/tmp/vlm-benchmark';APP='/sdcard/Android/data/com.hilal.vlm/files/benchmark'
def adb(*args,check=True):return subprocess.run([a.adb,*args],capture_output=True,text=True,check=check,timeout=30).stdout
def shell(command,check=True):return adb('shell',command,check=check)
def words(*args):return shlex.join(str(x) for x in args)
meta={'date':'2026-10-27','llama_commit':'de5122d879d00d2895c36be7e0cee94105aeeabd','repeats':a.repeats,
      'device':shell('getprop ro.product.model; getprop ro.board.platform; getprop ro.build.version.sdk; getconf CLK_TCK; cat /proc/meminfo | head -3'),
      'gpu_counter_scope':'whole_device','protocol':'loaded-engine, one excluded warmup, independent conversations, greedy, max64, context4096, 4 CPU threads, 3s between requests',
      'vision_backends':{'gemma':'GPU','smol':'CPU','qwen':'CPU'}}
(a.out/('metadata-resume.json' if a.resume else 'metadata.json')).write_text(json.dumps(meta,ensure_ascii=False,indent=2))
for name in a.only:
    shell('am force-stop com.hilal.vlm')
    if name=='gemma':
        root=APP;phase=root+'/phase.txt';pidcmd='pidof com.hilal.vlm';result=root+'/gemma-E2B.jsonl'
        shell('echo loading > '+phase)
        command=words('am','instrument','-w','-r','-e','class','com.hilal.vlm.ModelBenchmarkTest','-e','benchmark','true','-e','model','E2B','-e','repeats',a.repeats,'com.hilal.vlm.test/androidx.test.runner.AndroidJUnitRunner')
    else:
        root=NATIVE;phase=root+'/native-phase.txt';pidcmd='cat '+root+'/native-pid.txt';result=root+'/'+name+'.jsonl'
        shell('echo loading > '+phase+'; echo 0 > '+root+'/native-pid.txt')
        model,proj,modelid=(('SmolVLM2-500M-Video-Instruct-Q8_0.gguf','mmproj-SmolVLM2-500M-Video-Instruct-Q8_0.gguf','SmolVLM2-500M-Q8_0') if name=='smol' else ('Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf','mmproj-Qwen2.5-VL-3B-Instruct-Q8_0.gguf','Qwen2.5-VL-3B-Q4_K_M'))
        command=words(root+'/vlm-bench',root+'/models/'+model,root+'/models/'+proj,root,modelid,a.repeats,result)
    if a.resume and name!='gemma': command='VLM_BENCH_RESUME=1 '+command
    print('START',name,flush=True)
    started=time.monotonic();lastphase=None;timed_out=False
    sample_path=a.out/(name+'-samples.csv')
    append=a.resume and name!='gemma' and sample_path.exists()
    old_samples=list(csv.DictReader(sample_path.open())) if append else []
    offset=float(old_samples[-1]['elapsed_s'])+1 if old_samples else 0
    with (a.out/(name+'-runner.log')).open('a' if append else 'w') as log,sample_path.open('a' if append else 'w') as samples:
        writer=csv.writer(samples)
        if not append: writer.writerow(['elapsed_s','phase','pid','cpu_ticks','rss_kib','device_gpu_percent','gpu_clock_raw','battery_temperature_deci_c'])
        job=subprocess.Popen([a.adb,'shell',command],stdout=log,stderr=subprocess.STDOUT)
        try:
            while job.poll() is None:
                # All values are device counters; no personal files or credentials are read.
                cmd='cat '+phase+'; echo; task_pid='+ '"$('+pidcmd+')"'+ '; echo PID:$task_pid; if [ -n "$task_pid" ] && [ "$task_pid" != 0 ]; then cat /proc/$task_pid/stat; grep VmRSS /proc/$task_pid/status; fi; echo GPU; cat /sys/class/misc/mali0/device/utilization; cat /sys/class/misc/mali0/device/clock; cat /sys/class/power_supply/battery/temp'
                raw=shell(cmd,check=False);lines=raw.splitlines();phase_now=lines[0] if lines else 'unknown'
                if phase_now!=lastphase:print(name,phase_now,flush=True);lastphase=phase_now
                pid=next((l[4:] for l in lines if l.startswith('PID:')),'')
                stat=next((l for l in lines if ') ' in l and l.split(' ',1)[0].isdigit()),'')
                ticks=''
                if stat:
                    f=stat.split(') ',1)[1].split();ticks=int(f[11])+int(f[12])
                rss=next((l.split()[1] for l in lines if l.startswith('VmRSS:')),'')
                gpu=clock=temp=''
                if 'GPU' in lines:
                    vals=lines[lines.index('GPU')+1:];gpu=vals[0] if len(vals)>0 else '';clock=vals[1] if len(vals)>1 else '';temp=vals[2] if len(vals)>2 else ''
                writer.writerow([round(offset+time.monotonic()-started,3),phase_now,pid,ticks,rss,gpu,clock,temp]);samples.flush()
                if time.monotonic()-started>a.timeout:
                    timed_out=True
                    break
                time.sleep(1)
        finally:
            if job.poll() is None:
                job.terminate()
                if name=='gemma':shell('am force-stop com.hilal.vlm',check=False)
                else:shell('kill "$(cat '+root+'/native-pid.txt)"',check=False)
        job.wait(timeout=30)
        raw=shell('cat '+result,check=False)
        (a.out/(name+'.jsonl')).write_text(raw)
        rows=[json.loads(l) for l in raw.splitlines() if l.startswith('{')]
        errors=[r for r in rows if r.get('error')]
        status={'model':name,'exit_code':job.returncode,'timed_out':timed_out,'rows':len(rows),'expected_rows':3*a.repeats,'errors':errors,'wall_seconds':round(time.monotonic()-started,2)}
        (a.out/(name+'-status.json')).write_text(json.dumps(status,ensure_ascii=False,indent=2))
        print('DONE',json.dumps(status),flush=True)
        if job.returncode or len(rows)!=3*a.repeats or errors:
            print('Inspect runner log; this model did not complete the benchmark successfully.',flush=True)
    time.sleep(5)
