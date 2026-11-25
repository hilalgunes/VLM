// Real multimodal benchmark. All prompts carry a bitmap through libmtmd.
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include "nlohmann/json.hpp"
#include <atomic>
#include <chrono>
#include <fstream>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <thread>
#include <cstdlib>
#include <set>
#include <sys/resource.h>
#include <unistd.h>
using json = nlohmann::ordered_json;
using Clock = std::chrono::steady_clock;
double ms(Clock::time_point start) { return std::chrono::duration<double,std::milli>(Clock::now()-start).count(); }
double cpu_ms() { rusage r{};getrusage(RUSAGE_SELF,&r);return (r.ru_utime.tv_sec+r.ru_stime.tv_sec)*1000.0+(r.ru_utime.tv_usec+r.ru_stime.tv_usec)/1000.0; }
long rss() { std::ifstream f("/proc/self/status");std::string key;while(f>>key){if(key=="VmRSS:"){long n;f>>n;return n;} std::string rest;std::getline(f,rest);}return 0; }
void phase(const std::string& root,const std::string& value) { std::ofstream(root+"/native-phase.txt")<<value; }
int main(int argc,char**argv) {
 try {
    if(argc!=7)throw std::runtime_error("usage: vlm-bench model mmproj fixture-dir model-id repeats output-jsonl");
    const std::string root=argv[3], id=argv[4];const int repeats=std::stoi(argv[5]);
    if(repeats<1||repeats>10)throw std::runtime_error("invalid repeats");
    std::ofstream(root+"/native-pid.txt")<<getpid();
    std::ifstream cf(root+"/cases.json");const json cases=json::parse(cf);
    const bool resume=std::getenv("VLM_BENCH_RESUME")!=nullptr;
    std::set<std::pair<std::string,int>> completed;
    if(resume){
        std::ifstream previous(argv[6]);std::string line;
        while(std::getline(previous,line)){
            const auto row=json::parse(line);
            if(row["model"]!=id)throw std::runtime_error("resume model mismatch");
            if(row["error"].is_null())completed.emplace(row["case"].get<std::string>(),row["repeat"].get<int>());
        }
    }
    std::ofstream out(argv[6],resume?std::ios::app:std::ios::trunc);
    if(!out)throw std::runtime_error("cannot create output");
    llama_backend_init();
    const auto start=Clock::now();
    auto mp=llama_model_default_params();mp.n_gpu_layers=0;
    std::unique_ptr<llama_model,decltype(&llama_model_free)> model(llama_model_load_from_file(argv[1],mp),llama_model_free);
    if(!model)throw std::runtime_error("model load failed");
    auto cp=llama_context_default_params();cp.n_ctx=4096;cp.n_batch=512;cp.n_ubatch=512;cp.n_threads=4;cp.n_threads_batch=4;
    std::unique_ptr<llama_context,decltype(&llama_free)> ctx(llama_init_from_model(model.get(),cp),llama_free);
    if(!ctx)throw std::runtime_error("context initialization failed");
    auto vp=mtmd_context_params_default();vp.use_gpu=false;vp.n_threads=4;vp.warmup=false;vp.image_max_tokens=1024;
    std::unique_ptr<mtmd_context,decltype(&mtmd_free)> vision(mtmd_init_from_file(argv[2],model.get(),vp),mtmd_free);
    if(!vision||!mtmd_support_vision(vision.get()))throw std::runtime_error("vision initialization failed");
    const auto load_ms=ms(start);
    const auto vocab=llama_model_get_vocab(model.get());
    // Repeat zero warms both decoder and vision encoder; not included in reported runs.
    for(int repeat=0;repeat<=repeats;repeat++)for(const auto& test:cases) {
        if(repeat==0&&test["id"]!="count_en")continue;
        if(repeat>0&&completed.count({test["id"].get<std::string>(),repeat}))continue;
        phase(root,repeat==0?"warmup":test["id"].get<std::string>()+":"+std::to_string(repeat));
        llama_memory_clear(llama_get_memory(ctx.get()),true);
        std::atomic<bool> running(true);std::atomic<long> peak(rss());
        std::thread sample([&]{while(running){long val=rss(),old=peak;while(val>old&&!peak.compare_exchange_weak(old,val)){}std::this_thread::sleep_for(std::chrono::milliseconds(200));}});
        const auto started=Clock::now();const double cpu_start=cpu_ms();
        std::string answer,error;double first=-1;int generated=0;bool truncated=true;
        try {
            const auto image_path=root+"/"+test["image"].get<std::string>();
            const auto bitmap=mtmd_helper_bitmap_init_from_file(vision.get(),image_path.c_str(),false,mtmd_helper_init_opt_default());
            std::unique_ptr<mtmd_bitmap,decltype(&mtmd_bitmap_free)> image(bitmap.bitmap,mtmd_bitmap_free);
            if(!image)throw std::runtime_error("image decoding failed");
            const std::string content=std::string(mtmd_default_marker())+"\n"+test["prompt"].get<std::string>();
            const llama_chat_message message={"user",content.c_str()};
            const auto tmpl=llama_model_chat_template(model.get(),nullptr);
            std::vector<char> buf(32768);
            const int size=llama_chat_apply_template(tmpl,&message,1,true,buf.data(),buf.size());
            if(size<0||size>=int(buf.size()))throw std::runtime_error("unsupported chat template");
            const std::string prompt(buf.data(),size);
            mtmd_input_text input{prompt.c_str(),prompt.size(),true,true};
            const mtmd_bitmap* images[]={image.get()};
            std::unique_ptr<mtmd_input_chunks,decltype(&mtmd_input_chunks_free)> chunks(mtmd_input_chunks_init(),mtmd_input_chunks_free);
            if(mtmd_tokenize(vision.get(),chunks.get(),&input,images,1))throw std::runtime_error("multimodal tokenize failed");
            llama_pos past=0;
            if(mtmd_helper_eval_chunks(vision.get(),ctx.get(),chunks.get(),0,0,512,true,&past))throw std::runtime_error("image/prompt evaluation failed");
            std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> sampler(llama_sampler_init_greedy(),llama_sampler_free);
            for(int n=0;n<(repeat==0?16:64);n++){
                auto token=llama_sampler_sample(sampler.get(),ctx.get(),-1);
                if(llama_vocab_is_eog(vocab,token)){truncated=false;break;}
                if(first<0)first=ms(started);
                std::vector<char> piece(256);int len=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false);
                if(len<0){piece.resize(-len);len=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false);}
                if(len>0)answer.append(piece.data(),len);
                generated++;
                auto batch=llama_batch_get_one(&token,1);
                if(llama_decode(ctx.get(),batch))throw std::runtime_error("token decode failed");
            }
        } catch(const std::exception& e){error=e.what();}
        const double total=ms(started), cpu=cpu_ms()-cpu_start;
        running=false;sample.join();
        if(repeat>0){
            json result={{"model",id},{"runtime","llama.cpp de5122d CPU / 4 threads"},{"case",test["id"]},{"repeat",repeat},
                {"load_ms",load_ms},{"first_token_ms",first<0?json(nullptr):json(first)},{"total_ms",total},{"cpu_ms",cpu},
                {"peak_rss_kib",peak.load()},{"generated_tokens",generated},{"truncated",truncated},{"answer",answer},{"error",error.empty()?json(nullptr):json(error)}};
            out<<result.dump(-1,' ',false,json::error_handler_t::replace)<<std::endl;
            std::cout<<"RESULT "<<result.dump(-1,' ',false,json::error_handler_t::replace)<<std::endl;
        }
        phase(root,"cooldown");std::this_thread::sleep_for(std::chrono::seconds(3));
    }
    phase(root,"complete");
    return 0;
 } catch(const std::exception&e){std::cerr<<"BENCHMARK ERROR: "<<e.what()<<std::endl;return 1;}
}
