#ifdef __linux__
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#endif

#include <napi.h>
#include <mpv/client.h>
#include <mpv/render.h>
#include <mpv/render_gl.h>

#ifdef __APPLE__
#include <CoreFoundation/CoreFoundation.h>
#include <IOSurface/IOSurface.h>
#include <OpenGL/OpenGL.h>
#include <OpenGL/gl3.h>
#endif

#if defined(__APPLE__) || defined(__linux__)
#include <dlfcn.h>
#endif

#ifdef __linux__
#include <link.h>
#endif

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <d3d11.h>
#include <dxgi1_2.h>
#include <GL/gl.h>
#endif

#include <algorithm>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <mutex>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

namespace {

struct MediaNetworkProfile {
  std::string user_agent = "WizeStream Desktop/0.6";
  std::string referrer;
  std::string http_headers;
};

#ifdef __linux__
void linux_module_anchor() {}

struct LinuxMpvApi {
  void* library = nullptr;
  decltype(&::mpv_command) mpv_command_fn = nullptr;
  decltype(&::mpv_create) mpv_create_fn = nullptr;
  decltype(&::mpv_error_string) mpv_error_string_fn = nullptr;
  decltype(&::mpv_event_name) mpv_event_name_fn = nullptr;
  decltype(&::mpv_initialize) mpv_initialize_fn = nullptr;
  decltype(&::mpv_request_log_messages) mpv_request_log_messages_fn = nullptr;
  decltype(&::mpv_observe_property) mpv_observe_property_fn = nullptr;
  decltype(&::mpv_render_context_create) mpv_render_context_create_fn = nullptr;
  decltype(&::mpv_render_context_free) mpv_render_context_free_fn = nullptr;
  decltype(&::mpv_render_context_render) mpv_render_context_render_fn = nullptr;
  decltype(&::mpv_render_context_set_update_callback) mpv_render_context_set_update_callback_fn = nullptr;
  decltype(&::mpv_render_context_update) mpv_render_context_update_fn = nullptr;
  decltype(&::mpv_set_option_string) mpv_set_option_string_fn = nullptr;
  decltype(&::mpv_set_property) mpv_set_property_fn = nullptr;
  decltype(&::mpv_set_property_string) mpv_set_property_string_fn = nullptr;
  decltype(&::mpv_set_wakeup_callback) mpv_set_wakeup_callback_fn = nullptr;
  decltype(&::mpv_terminate_destroy) mpv_terminate_destroy_fn = nullptr;
  decltype(&::mpv_wait_event) mpv_wait_event_fn = nullptr;
};

LinuxMpvApi load_linux_mpv_api() {
  LinuxMpvApi api;
  Dl_info module_info{};
  if (!dladdr(reinterpret_cast<void*>(&linux_module_anchor), &module_info) || !module_info.dli_fname) {
    std::fprintf(stderr, "Could not locate the WizeStream native addon\n");
    std::abort();
  }
  std::string module_path(module_info.dli_fname);
  const size_t separator = module_path.find_last_of('/');
  const std::string directory = separator == std::string::npos ? "." : module_path.substr(0, separator);
  const std::string versioned = directory + "/libmpv.so.2";
  // Electron and libmpv both ship FFmpeg. Loading libmpv into Electron's base
  // namespace can bind one stack to symbols from the other, which is especially
  // prone to allocator corruption on arm64. A new glibc namespace keeps the
  // staged libmpv dependency closure self-contained while the addon continues to
  // use Electron's normal N-API symbols.
  api.library = dlmopen(LM_ID_NEWLM, versioned.c_str(), RTLD_NOW | RTLD_LOCAL);
  if (!api.library) {
    const std::string unversioned = directory + "/libmpv.so";
    api.library = dlmopen(LM_ID_NEWLM, unversioned.c_str(), RTLD_NOW | RTLD_LOCAL);
  }
  if (!api.library) {
    std::fprintf(stderr, "Could not load staged libmpv: %s\n", dlerror());
    std::abort();
  }
#define LOAD_MPV_SYMBOL(name) \
  api.name##_fn = reinterpret_cast<decltype(api.name##_fn)>(dlsym(api.library, #name)); \
  if (!api.name##_fn) { std::fprintf(stderr, "Missing libmpv symbol: %s\n", #name); std::abort(); }
  LOAD_MPV_SYMBOL(mpv_command)
  LOAD_MPV_SYMBOL(mpv_create)
  LOAD_MPV_SYMBOL(mpv_error_string)
  LOAD_MPV_SYMBOL(mpv_event_name)
  LOAD_MPV_SYMBOL(mpv_initialize)
  LOAD_MPV_SYMBOL(mpv_request_log_messages)
  LOAD_MPV_SYMBOL(mpv_observe_property)
  LOAD_MPV_SYMBOL(mpv_render_context_create)
  LOAD_MPV_SYMBOL(mpv_render_context_free)
  LOAD_MPV_SYMBOL(mpv_render_context_render)
  LOAD_MPV_SYMBOL(mpv_render_context_set_update_callback)
  LOAD_MPV_SYMBOL(mpv_render_context_update)
  LOAD_MPV_SYMBOL(mpv_set_option_string)
  LOAD_MPV_SYMBOL(mpv_set_property)
  LOAD_MPV_SYMBOL(mpv_set_property_string)
  LOAD_MPV_SYMBOL(mpv_set_wakeup_callback)
  LOAD_MPV_SYMBOL(mpv_terminate_destroy)
  LOAD_MPV_SYMBOL(mpv_wait_event)
#undef LOAD_MPV_SYMBOL
  return api;
}

LinuxMpvApi& linux_mpv_api() {
  static LinuxMpvApi api = load_linux_mpv_api();
  return api;
}

std::mutex linux_handle_pool_mutex;
std::vector<mpv_handle*> linux_handle_pool;

mpv_handle* acquire_linux_handle() {
  std::lock_guard<std::mutex> lock(linux_handle_pool_mutex);
  if (linux_handle_pool.empty()) return nullptr;
  mpv_handle* handle = linux_handle_pool.back();
  linux_handle_pool.pop_back();
  return handle;
}

void recycle_linux_handle(mpv_handle* handle) {
  std::lock_guard<std::mutex> lock(linux_handle_pool_mutex);
  linux_handle_pool.push_back(handle);
}

#define mpv_command linux_mpv_api().mpv_command_fn
#define mpv_create linux_mpv_api().mpv_create_fn
#define mpv_error_string linux_mpv_api().mpv_error_string_fn
#define mpv_event_name linux_mpv_api().mpv_event_name_fn
#define mpv_initialize linux_mpv_api().mpv_initialize_fn
#define mpv_request_log_messages linux_mpv_api().mpv_request_log_messages_fn
#define mpv_observe_property linux_mpv_api().mpv_observe_property_fn
#define mpv_render_context_create linux_mpv_api().mpv_render_context_create_fn
#define mpv_render_context_free linux_mpv_api().mpv_render_context_free_fn
#define mpv_render_context_render linux_mpv_api().mpv_render_context_render_fn
#define mpv_render_context_set_update_callback linux_mpv_api().mpv_render_context_set_update_callback_fn
#define mpv_render_context_update linux_mpv_api().mpv_render_context_update_fn
#define mpv_set_option_string linux_mpv_api().mpv_set_option_string_fn
#define mpv_set_property linux_mpv_api().mpv_set_property_fn
#define mpv_set_property_string linux_mpv_api().mpv_set_property_string_fn
#define mpv_set_wakeup_callback linux_mpv_api().mpv_set_wakeup_callback_fn
#define mpv_terminate_destroy linux_mpv_api().mpv_terminate_destroy_fn
#define mpv_wait_event linux_mpv_api().mpv_wait_event_fn
#endif

#ifdef _WIN32
#ifndef GL_FRAMEBUFFER
#define GL_FRAMEBUFFER 0x8D40
#endif
#ifndef GL_COLOR_ATTACHMENT0
#define GL_COLOR_ATTACHMENT0 0x8CE0
#endif
#ifndef GL_FRAMEBUFFER_COMPLETE
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#endif
#ifndef GL_RGBA8
#define GL_RGBA8 0x8058
#endif
#ifndef GL_CLAMP_TO_EDGE
#define GL_CLAMP_TO_EDGE 0x812F
#endif
#ifndef WGL_ACCESS_WRITE_DISCARD_NV
#define WGL_ACCESS_WRITE_DISCARD_NV 0x0002
#endif

using PFNGLGENFRAMEBUFFERSPROC = void (APIENTRY*)(GLsizei, GLuint*);
using PFNGLBINDFRAMEBUFFERPROC = void (APIENTRY*)(GLenum, GLuint);
using PFNGLFRAMEBUFFERTEXTURE2DPROC = void (APIENTRY*)(GLenum, GLenum, GLenum, GLuint, GLint);
using PFNGLCHECKFRAMEBUFFERSTATUSPROC = GLenum (APIENTRY*)(GLenum);
using PFNGLDELETEFRAMEBUFFERSPROC = void (APIENTRY*)(GLsizei, const GLuint*);
using PFNWGLDXOPENDEVICENVPROC = HANDLE (WINAPI*)(void*);
using PFNWGLDXCLOSEDEVICENVPROC = BOOL (WINAPI*)(HANDLE);
using PFNWGLDXREGISTEROBJECTNVPROC = HANDLE (WINAPI*)(HANDLE, void*, GLuint, GLenum, GLenum);
using PFNWGLDXUNREGISTEROBJECTNVPROC = BOOL (WINAPI*)(HANDLE, HANDLE);
using PFNWGLDXLOCKOBJECTSNVPROC = BOOL (WINAPI*)(HANDLE, GLint, HANDLE*);
using PFNWGLDXUNLOCKOBJECTSNVPROC = BOOL (WINAPI*)(HANDLE, GLint, HANDLE*);

LRESULT CALLBACK hidden_wnd_proc(HWND hwnd, UINT message, WPARAM wparam, LPARAM lparam) {
  return DefWindowProc(hwnd, message, wparam, lparam);
}
#endif

std::string mpv_error_text(int code) {
  if (code >= 0) return "ok";
  const char* text = mpv_error_string(code);
  return text ? text : "unknown mpv error";
}

void throw_mpv_error(Napi::Env env, const std::string& action, int code) {
  std::ostringstream out;
  out << action << " failed: " << mpv_error_text(code) << " (" << code << ")";
  Napi::Error::New(env, out.str()).ThrowAsJavaScriptException();
}

#ifdef _WIN32
std::string hex_u32(unsigned long value) {
  std::ostringstream out;
  out << "0x" << std::hex << value;
  return out.str();
}
#endif

using MpvWakeupCallback = void (*)(void*);

#ifdef _WIN32
using MpvSetWakeupCallbackFn = void (*)(mpv_handle*, MpvWakeupCallback, void*);
using MpvSetRenderUpdateCallbackFn = void (*)(mpv_render_context*, MpvWakeupCallback, void*);

template <typename T>
T load_mpv_proc(const char* name) {
  HMODULE mpv = GetModuleHandleA("libmpv-2.dll");
  if (!mpv) mpv = GetModuleHandleA("mpv-2.dll");
  if (!mpv) mpv = LoadLibraryA("libmpv-2.dll");
  if (!mpv) mpv = LoadLibraryA("mpv-2.dll");
  return mpv ? reinterpret_cast<T>(GetProcAddress(mpv, name)) : nullptr;
}

void set_mpv_wakeup_callback(mpv_handle* handle, MpvWakeupCallback callback, void* ctx) {
  static MpvSetWakeupCallbackFn fn = load_mpv_proc<MpvSetWakeupCallbackFn>("mpv_set_wakeup_callback");
  if (fn) fn(handle, callback, ctx);
}

void set_mpv_render_update_callback(mpv_render_context* context, MpvWakeupCallback callback, void* ctx) {
  static MpvSetRenderUpdateCallbackFn fn =
    load_mpv_proc<MpvSetRenderUpdateCallbackFn>("mpv_render_context_set_update_callback");
  if (fn) fn(context, callback, ctx);
}
#else
void set_mpv_wakeup_callback(mpv_handle* handle, MpvWakeupCallback callback, void* ctx) {
  mpv_set_wakeup_callback(handle, callback, ctx);
}

void set_mpv_render_update_callback(mpv_render_context* context, MpvWakeupCallback callback, void* ctx) {
  mpv_render_context_set_update_callback(context, callback, ctx);
}
#endif

class MpvPlayer : public Napi::ObjectWrap<MpvPlayer> {
 public:
  static Napi::Object Init(Napi::Env env, Napi::Object exports) {
    Napi::Function ctor = DefineClass(env, "MpvPlayer", {
      InstanceMethod("open", &MpvPlayer::Open),
      InstanceMethod("openMedia", &MpvPlayer::OpenMedia),
      InstanceMethod("setAudioFile", &MpvPlayer::SetAudioFile),
      InstanceMethod("setSubtitleFile", &MpvPlayer::SetSubtitleFile),
      InstanceMethod("play", &MpvPlayer::Play),
      InstanceMethod("pause", &MpvPlayer::Pause),
      InstanceMethod("stop", &MpvPlayer::Stop),
      InstanceMethod("seek", &MpvPlayer::Seek),
      InstanceMethod("setVolume", &MpvPlayer::SetVolume),
      InstanceMethod("setEqualizer", &MpvPlayer::SetEqualizer),
      InstanceMethod("setPlaybackParameters", &MpvPlayer::SetPlaybackParameters),
      InstanceMethod("setUpdateCallback", &MpvPlayer::SetUpdateCallback),
      InstanceMethod("setEventCallback", &MpvPlayer::SetEventCallback),
      InstanceMethod("renderFrame", &MpvPlayer::RenderFrame),
      InstanceMethod("renderSharedTexture", &MpvPlayer::RenderSharedTexture),
      InstanceMethod("pollEvents", &MpvPlayer::PollEvents),
      InstanceMethod("destroy", &MpvPlayer::Destroy),
    });
    exports.Set("MpvPlayer", ctor);
    return exports;
  }

  explicit MpvPlayer(const Napi::CallbackInfo& info)
      : Napi::ObjectWrap<MpvPlayer>(info) {
    Napi::Env env = info.Env();
    if (info.Length() >= 1 && info[0].IsObject()) {
      Napi::Object options = info[0].As<Napi::Object>();
      if (options.Has("mode") && options.Get("mode").IsString()) {
        mode_ = options.Get("mode").As<Napi::String>().Utf8Value();
      }
      if (options.Has("tlsCaFile") && options.Get("tlsCaFile").IsString()) {
        tls_ca_file_ = options.Get("tlsCaFile").As<Napi::String>().Utf8Value();
      }
    }

    bool reused_handle = false;
#ifdef __linux__
    handle_ = acquire_linux_handle();
    reused_handle = handle_ != nullptr;
#endif
    if (!handle_) handle_ = mpv_create();
    if (!handle_) {
      Napi::Error::New(env, "mpv_create failed").ThrowAsJavaScriptException();
      return;
    }

    if (!reused_handle) {
      set_option("terminal", "no");
      set_option("msg-level", "all=warn");
      set_option("input-default-bindings", "no");
      set_option("audio-display", "no");
      set_option("pause", "yes");
      set_option("keep-open", "yes");
      // WizeStream resolves media itself. Do not invoke an unbundled youtube-dl
      // subprocess when a resolved URL reports a network error.
      set_option("ytdl", "no");
      if (!tls_ca_file_.empty()) set_option("tls-ca-file", tls_ca_file_.c_str());
      const char* hwdec = std::getenv("MPV_HWDEC");
#ifdef _WIN32
      set_option("hwdec", hwdec && hwdec[0] ? hwdec : "no");
#else
      set_option("hwdec", hwdec && hwdec[0] ? hwdec : "auto-safe");
#endif
      const char* audio_output = std::getenv("MPV_AO");
      if (audio_output && audio_output[0]) set_option("ao", audio_output);
      set_option("sw-fast", "yes");
      set_option("vo", "libmpv");

      int ret = mpv_initialize(handle_);
      if (ret < 0) {
        throw_mpv_error(env, "mpv_initialize", ret);
        return;
      }
      mpv_request_log_messages(handle_, "warn");

      observe("time-pos", MPV_FORMAT_DOUBLE);
      observe("duration", MPV_FORMAT_DOUBLE);
      observe("pause", MPV_FORMAT_FLAG);
      observe("eof-reached", MPV_FORMAT_FLAG);
      observe("width", MPV_FORMAT_INT64);
      observe("height", MPV_FORMAT_INT64);
      observe("video-codec", MPV_FORMAT_STRING);
      observe("container-fps", MPV_FORMAT_DOUBLE);
      observe("aid", MPV_FORMAT_STRING);
      observe("sid", MPV_FORMAT_STRING);
    }
    if (reused_handle) {
      while (mpv_wait_event(handle_, 0)->event_id != MPV_EVENT_NONE) {}
      if (!tls_ca_file_.empty())
        mpv_set_property_string(handle_, "tls-ca-file", tls_ca_file_.c_str());
    }
    set_mpv_wakeup_callback(handle_, on_mpv_wakeup, this);

    int ret = 0;

    if (mode_ == "shared-texture") {
#if defined(__APPLE__)
      if (!init_gl()) {
        Napi::Error::New(env, "Failed to initialize CGL context").ThrowAsJavaScriptException();
        return;
      }
      CGLSetCurrentContext(gl_context_);
      mpv_opengl_init_params gl_init = {
        get_proc_address,
        nullptr,
      };
      const char* api_type = MPV_RENDER_API_TYPE_OPENGL;
      mpv_render_param params[] = {
        {MPV_RENDER_PARAM_API_TYPE, const_cast<char*>(api_type)},
        {MPV_RENDER_PARAM_OPENGL_INIT_PARAMS, &gl_init},
        {MPV_RENDER_PARAM_INVALID, nullptr},
      };
      ret = mpv_render_context_create(&render_context_, handle_, params);
      if (ret < 0) {
        throw_mpv_error(env, "mpv_render_context_create(opengl)", ret);
        return;
      }
      set_mpv_render_update_callback(render_context_, on_mpv_render_update, this);
#elif defined(_WIN32)
      if (!init_gl()) {
        Napi::Error::New(env, "Failed to initialize WGL/D3D11 interop context").ThrowAsJavaScriptException();
        return;
      }
      wglMakeCurrent(win_dc_, win_gl_context_);
      mpv_opengl_init_params gl_init = {
        get_proc_address,
        nullptr,
      };
      const char* api_type = MPV_RENDER_API_TYPE_OPENGL;
      mpv_render_param params[] = {
        {MPV_RENDER_PARAM_API_TYPE, const_cast<char*>(api_type)},
        {MPV_RENDER_PARAM_OPENGL_INIT_PARAMS, &gl_init},
        {MPV_RENDER_PARAM_INVALID, nullptr},
      };
      ret = mpv_render_context_create(&render_context_, handle_, params);
      if (ret < 0) {
        throw_mpv_error(env, "mpv_render_context_create(opengl)", ret);
        return;
      }
      set_mpv_render_update_callback(render_context_, on_mpv_render_update, this);
#else
      Napi::Error::New(env, "shared-texture mode is only implemented on macOS and Windows").ThrowAsJavaScriptException();
      return;
#endif
    } else {
      const char* api_type = MPV_RENDER_API_TYPE_SW;
      mpv_render_param params[] = {
        {MPV_RENDER_PARAM_API_TYPE, const_cast<char*>(api_type)},
        {MPV_RENDER_PARAM_INVALID, nullptr},
      };

      ret = mpv_render_context_create(&render_context_, handle_, params);
      if (ret < 0) {
        throw_mpv_error(env, "mpv_render_context_create", ret);
        return;
      }
      set_mpv_render_update_callback(render_context_, on_mpv_render_update, this);
    }
  }

  ~MpvPlayer() override {
    cleanup();
  }

 private:
  void set_option(const char* key, const char* value) {
    if (handle_) {
      mpv_set_option_string(handle_, key, value);
    }
  }

  void observe(const char* name, mpv_format format) {
    if (handle_) {
      mpv_observe_property(handle_, next_observer_id_++, name, format);
    }
  }

  int command(const std::vector<std::string>& args) {
    std::vector<const char*> c_args;
    c_args.reserve(args.size() + 1);
    for (const std::string& arg : args) {
      c_args.push_back(arg.c_str());
    }
    c_args.push_back(nullptr);
    return mpv_command(handle_, c_args.data());
  }

  MediaNetworkProfile network_profile(const Napi::Object& request) {
    MediaNetworkProfile profile;
    if (request.Has("userAgent") && request.Get("userAgent").IsString())
      profile.user_agent = request.Get("userAgent").As<Napi::String>().Utf8Value();
    if (request.Has("referrer") && request.Get("referrer").IsString())
      profile.referrer = request.Get("referrer").As<Napi::String>().Utf8Value();
    if (request.Has("httpHeaders") && request.Get("httpHeaders").IsArray()) {
      Napi::Array headers = request.Get("httpHeaders").As<Napi::Array>();
      for (uint32_t index = 0; index < headers.Length(); index++) {
        Napi::Value value = headers.Get(index);
        if (!value.IsString()) continue;
        if (!profile.http_headers.empty()) profile.http_headers += ",";
        profile.http_headers += value.As<Napi::String>().Utf8Value();
      }
    }
    return profile;
  }

  int apply_network_profile(const MediaNetworkProfile& profile) {
    // Keep mpv's seekable libcurl transport enabled so byte-range requests are
    // generated correctly. FFmpeg suppresses automatic Range headers for POST.
    int ret = mpv_set_property_string(handle_, "curl-enabled", "yes");
    if (ret < 0 && ret != MPV_ERROR_PROPERTY_NOT_FOUND) return ret;
    ret = mpv_set_property_string(handle_, "stream-lavf-o", "");
    if (ret < 0) return ret;
    ret = mpv_set_property_string(handle_, "user-agent", profile.user_agent.c_str());
    if (ret < 0) return ret;
    ret = mpv_set_property_string(handle_, "referrer", profile.referrer.c_str());
    if (ret < 0) return ret;
    return mpv_set_property_string(handle_, "http-header-fields", profile.http_headers.c_str());
  }

  static void call_js_no_args(Napi::Env env, Napi::Function callback) {
    callback.Call({});
  }

  static void on_mpv_render_update(void* ctx) {
    auto* self = static_cast<MpvPlayer*>(ctx);
    if (self && self->alive_ && self->update_callback_) {
      self->update_callback_.NonBlockingCall(call_js_no_args);
    }
  }

  static void on_mpv_wakeup(void* ctx) {
    auto* self = static_cast<MpvPlayer*>(ctx);
    if (self && self->alive_ && self->event_callback_) {
      self->event_callback_.NonBlockingCall(call_js_no_args);
    }
  }

  Napi::Value Open(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    if (info.Length() < 1 || !info[0].IsString()) {
      Napi::TypeError::New(env, "open(path) requires a string path").ThrowAsJavaScriptException();
      return env.Undefined();
    }
    std::string path = info[0].As<Napi::String>().Utf8Value();
    loaded_ = false;
    pending_audio_ = false;
    pending_subtitle_ = false;
    int ret = apply_network_profile(MediaNetworkProfile{});
    if (ret < 0) {
      throw_mpv_error(env, "reset media request", ret);
      return env.Undefined();
    }
    ret = command({"loadfile", path, "replace"});
    if (ret < 0) throw_mpv_error(env, "loadfile", ret);
    has_external_audio_ = false;
    has_external_subtitle_ = false;
    return env.Undefined();
  }

  Napi::Value OpenMedia(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    if (info.Length() < 1 || !info[0].IsObject()) {
      Napi::TypeError::New(env, "openMedia(request) requires an object").ThrowAsJavaScriptException();
      return env.Undefined();
    }
    Napi::Object request = info[0].As<Napi::Object>();
    if (!request.Has("source") || !request.Get("source").IsString()) {
      Napi::TypeError::New(env, "openMedia(request.source) requires a string").ThrowAsJavaScriptException();
      return env.Undefined();
    }
    const std::string source = request.Get("source").As<Napi::String>().Utf8Value();
    loaded_ = false;
    pending_audio_ = false;
    pending_subtitle_ = false;
    int ret = apply_network_profile(network_profile(request));
    if (ret < 0) {
      throw_mpv_error(env, "configure media request", ret);
      return env.Undefined();
    }
    ret = command({"loadfile", source, "replace"});
    if (ret < 0) {
      throw_mpv_error(env, "loadfile", ret);
      return env.Undefined();
    }
    has_external_audio_ = false;
    has_external_subtitle_ = false;
    if (request.Has("audio") && request.Get("audio").IsObject()) {
      if (!add_external_track(env, request.Get("audio").As<Napi::Object>(), true)) return env.Undefined();
    }
    if (request.Has("subtitle") && request.Get("subtitle").IsObject()) {
      if (!add_external_track(env, request.Get("subtitle").As<Napi::Object>(), false)) return env.Undefined();
    }
    return env.Undefined();
  }

  Napi::Value SetAudioFile(const Napi::CallbackInfo& info) {
    return set_external_track(info, true);
  }

  Napi::Value SetSubtitleFile(const Napi::CallbackInfo& info) {
    return set_external_track(info, false);
  }

  bool add_external_track(Napi::Env env, const Napi::Object& track, bool audio) {
    if (!track.Has("url") || !track.Get("url").IsString()) {
      Napi::TypeError::New(env, "track.url requires a string").ThrowAsJavaScriptException();
      return false;
    }
    const std::string url = track.Get("url").As<Napi::String>().Utf8Value();
    const std::string title = track.Has("title") && track.Get("title").IsString()
        ? track.Get("title").As<Napi::String>().Utf8Value() : "";
    const std::string language = track.Has("language") && track.Get("language").IsString()
        ? track.Get("language").As<Napi::String>().Utf8Value() : "";
    const MediaNetworkProfile profile = network_profile(track);
    if (!loaded_) {
      if (audio) {
        pending_audio_ = true;
        pending_audio_url_ = url;
        pending_audio_title_ = title;
        pending_audio_language_ = language;
        pending_audio_network_ = profile;
      } else {
        pending_subtitle_ = true;
        pending_subtitle_url_ = url;
        pending_subtitle_title_ = title;
        pending_subtitle_language_ = language;
        pending_subtitle_network_ = profile;
      }
      return true;
    }
    const int profile_ret = apply_network_profile(profile);
    if (profile_ret < 0) {
      throw_mpv_error(env, audio ? "configure audio request" : "configure subtitle request", profile_ret);
      return false;
    }
    const char* action = audio ? "audio-add" : "sub-add";
    const int ret = command({action, url, "select", title, language});
    if (ret < 0) {
      throw_mpv_error(env, action, ret);
      return false;
    }
    if (audio) has_external_audio_ = true; else has_external_subtitle_ = true;
    return true;
  }

  Napi::Value set_external_track(const Napi::CallbackInfo& info, bool audio) {
    Napi::Env env = info.Env();
    bool& active = audio ? has_external_audio_ : has_external_subtitle_;
    if (audio) pending_audio_ = false; else pending_subtitle_ = false;
    if (active) {
      const char* remove_action = audio ? "audio-remove" : "sub-remove";
      const int ret = command({remove_action});
      if (ret < 0) {
        throw_mpv_error(env, remove_action, ret);
        return env.Undefined();
      }
      active = false;
    }
    if (info.Length() == 0 || info[0].IsNull() || info[0].IsUndefined()) {
      if (!audio && handle_) mpv_set_property_string(handle_, "sid", "no");
      return env.Undefined();
    }
    if (!info[0].IsObject()) {
      Napi::TypeError::New(env, "track must be an object or null").ThrowAsJavaScriptException();
      return env.Undefined();
    }
    add_external_track(env, info[0].As<Napi::Object>(), audio);
    return env.Undefined();
  }

  Napi::Value Play(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    int flag = 0;
    int ret = mpv_set_property(handle_, "pause", MPV_FORMAT_FLAG, &flag);
    if (ret < 0) throw_mpv_error(env, "play", ret);
    return env.Undefined();
  }

  Napi::Value Pause(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    int flag = 1;
    int ret = mpv_set_property(handle_, "pause", MPV_FORMAT_FLAG, &flag);
    if (ret < 0) throw_mpv_error(env, "pause", ret);
    return env.Undefined();
  }

  Napi::Value Stop(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    int ret = command({"stop"});
    if (ret < 0) throw_mpv_error(env, "stop", ret);
    loaded_ = false;
    pending_audio_ = false;
    pending_subtitle_ = false;
    return env.Undefined();
  }

  Napi::Value Seek(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    if (info.Length() < 1 || !info[0].IsNumber()) {
      Napi::TypeError::New(env, "seek(seconds) requires a number").ThrowAsJavaScriptException();
      return env.Undefined();
    }
    double seconds = info[0].As<Napi::Number>().DoubleValue();
    const bool exact = info.Length() < 2 || info[1].IsUndefined()
        || (info[1].IsBoolean() && info[1].As<Napi::Boolean>().Value());
    if (info.Length() >= 2 && !info[1].IsUndefined() && !info[1].IsBoolean()) {
      Napi::TypeError::New(env, "seek exact flag must be a boolean").ThrowAsJavaScriptException();
      return env.Undefined();
    }
    int ret = command({"seek", std::to_string(seconds), exact ? "absolute+exact" : "absolute"});
    if (ret < 0) throw_mpv_error(env, "seek", ret);
    return env.Undefined();
  }

  Napi::Value SetVolume(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    if (info.Length() < 1 || !info[0].IsNumber()) {
      Napi::TypeError::New(env, "setVolume(value) requires a number").ThrowAsJavaScriptException();
      return env.Undefined();
    }
    double volume = std::clamp(info[0].As<Napi::Number>().DoubleValue(), 0.0, 100.0);
    int ret = mpv_set_property(handle_, "volume", MPV_FORMAT_DOUBLE, &volume);
    if (ret < 0) throw_mpv_error(env, "setVolume", ret);
    return env.Undefined();
  }

  int apply_audio_filters() {
    std::vector<std::string> filters;
    if (skip_silence_) {
      filters.push_back("lavfi=[silenceremove=stop_periods=-1:stop_duration=0.1:"
                        "stop_threshold=-30dB:stop_silence=0.02:detection=peak]");
    }
    if (equalizer_gains_.size() == 10) {
      static const int frequencies[] = {32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
      std::ostringstream entries;
      for (size_t index = 0; index < equalizer_gains_.size(); index++) {
        if (index > 0) entries << "; ";
        entries << "entry(" << frequencies[index] << "," << equalizer_gains_[index] / 2.0 << ")";
      }
      filters.push_back("lavfi=[firequalizer=gain_entry='" + entries.str() + "':scale=loglog]");
    }
    if (pitch_uses_filter_ && std::abs(playback_pitch_ - 1.0) > 0.0001) {
      std::ostringstream filter;
      filter << "rubberband=pitch-scale=" << playback_pitch_;
      filters.push_back(filter.str());
    }
    std::ostringstream chain;
    for (size_t index = 0; index < filters.size(); index++) {
      if (index > 0) chain << ",";
      chain << filters[index];
    }
    return mpv_set_property_string(handle_, "af", chain.str().c_str());
  }

  Napi::Value SetEqualizer(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    if (info.Length() < 1 || info[0].IsNull() || info[0].IsUndefined()) {
      equalizer_gains_.clear();
      int ret = apply_audio_filters();
      if (ret < 0) throw_mpv_error(env, "setEqualizer", ret);
      return env.Undefined();
    }
    if (!info[0].IsArray()) {
      Napi::TypeError::New(env, "setEqualizer(gains) requires ten half-decibel gain steps")
          .ThrowAsJavaScriptException();
      return env.Undefined();
    }
    Napi::Array gains = info[0].As<Napi::Array>();
    if (gains.Length() != 10) {
      Napi::RangeError::New(env, "setEqualizer(gains) requires exactly ten bands")
          .ThrowAsJavaScriptException();
      return env.Undefined();
    }
    std::vector<double> next_gains;
    next_gains.reserve(10);
    for (uint32_t index = 0; index < gains.Length(); index++) {
      Napi::Value value = gains.Get(index);
      if (!value.IsNumber()) {
        Napi::TypeError::New(env, "Equalizer gains must be numbers").ThrowAsJavaScriptException();
        return env.Undefined();
      }
      double gain_step = value.As<Napi::Number>().DoubleValue();
      if (!std::isfinite(gain_step) || std::floor(gain_step) != gain_step
          || gain_step < -24.0 || gain_step > 24.0) {
        Napi::RangeError::New(env, "Equalizer gains must be between -24 and 24 half-decibel steps")
            .ThrowAsJavaScriptException();
        return env.Undefined();
      }
      next_gains.push_back(gain_step);
    }
    equalizer_gains_ = std::move(next_gains);
    int ret = apply_audio_filters();
    if (ret < 0) throw_mpv_error(env, "setEqualizer", ret);
    return env.Undefined();
  }

  Napi::Value SetPlaybackParameters(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    if (info.Length() < 3 || !info[0].IsNumber() || !info[1].IsNumber() || !info[2].IsBoolean()) {
      Napi::TypeError::New(env, "setPlaybackParameters(speed, pitch, skipSilence) requires two numbers and a boolean")
          .ThrowAsJavaScriptException();
      return env.Undefined();
    }
    double speed = info[0].As<Napi::Number>().DoubleValue();
    double pitch = info[1].As<Napi::Number>().DoubleValue();
    if (!std::isfinite(speed) || speed < 0.1 || speed > 3.0
        || !std::isfinite(pitch) || pitch < 0.1 || pitch > 3.0) {
      Napi::RangeError::New(env, "Playback speed and pitch must be between 0.1 and 3")
          .ThrowAsJavaScriptException();
      return env.Undefined();
    }
    int pitch_correction = 1;
    int ret = mpv_set_property(handle_, "audio-pitch-correction", MPV_FORMAT_FLAG, &pitch_correction);
    if (ret < 0) {
      throw_mpv_error(env, "setPlaybackParameters(audio-pitch-correction)", ret);
      return env.Undefined();
    }
    ret = mpv_set_property(handle_, "speed", MPV_FORMAT_DOUBLE, &speed);
    if (ret < 0) {
      throw_mpv_error(env, "setPlaybackParameters(speed)", ret);
      return env.Undefined();
    }
    const bool previous_pitch_uses_filter = pitch_uses_filter_;
    const double previous_pitch = playback_pitch_;
    const bool previous_skip_silence = skip_silence_;
    const int pitch_ret = mpv_set_property(handle_, "pitch", MPV_FORMAT_DOUBLE, &pitch);
    pitch_uses_filter_ = pitch_ret < 0;
    playback_pitch_ = pitch;
    skip_silence_ = info[2].As<Napi::Boolean>().Value();
    const bool filters_changed = previous_pitch_uses_filter != pitch_uses_filter_
        || (pitch_uses_filter_ && std::abs(previous_pitch - playback_pitch_) > 0.0001)
        || previous_skip_silence != skip_silence_;
    ret = filters_changed ? apply_audio_filters() : 0;
    if (ret < 0) {
      throw_mpv_error(env, pitch_uses_filter_ ? "setPlaybackParameters(audio filters)"
                                             : "setPlaybackParameters(skip silence)", ret);
    }
    return env.Undefined();
  }

  Napi::Value SetUpdateCallback(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    if (update_callback_) {
      update_callback_.Release();
      update_callback_ = nullptr;
    }
    if (info.Length() >= 1 && info[0].IsFunction()) {
      update_callback_ = Napi::ThreadSafeFunction::New(
        env,
        info[0].As<Napi::Function>(),
        "mpv-render-update",
        1,
        1
      );
    }
    return env.Undefined();
  }

  Napi::Value SetEventCallback(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    if (event_callback_) {
      event_callback_.Release();
      event_callback_ = nullptr;
    }
    if (info.Length() >= 1 && info[0].IsFunction()) {
      event_callback_ = Napi::ThreadSafeFunction::New(
        env,
        info[0].As<Napi::Function>(),
        "mpv-event-wakeup",
        1,
        1
      );
    }
    return env.Undefined();
  }

  Napi::Value RenderFrame(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    if (mode_ == "shared-texture") {
      Napi::Error::New(env, "renderFrame is unavailable in shared-texture mode").ThrowAsJavaScriptException();
      return env.Null();
    }
    if (!render_context_) {
      Napi::Error::New(env, "render context is not initialized").ThrowAsJavaScriptException();
      return env.Null();
    }

    int width = 960;
    int height = 540;
    if (info.Length() >= 2 && info[0].IsNumber() && info[1].IsNumber()) {
      width = std::max(2, info[0].As<Napi::Number>().Int32Value());
      height = std::max(2, info[1].As<Napi::Number>().Int32Value());
    }
    width = std::min(width, 1920);
    height = std::min(height, 1080);

    std::vector<uint8_t> rgba(static_cast<size_t>(width) * static_cast<size_t>(height) * 4);
    int size[2] = {width, height};
    int stride = width * 4;
    char format[] = "rgba";

    mpv_render_param params[] = {
      {MPV_RENDER_PARAM_SW_SIZE, size},
      {MPV_RENDER_PARAM_SW_FORMAT, format},
      {MPV_RENDER_PARAM_SW_STRIDE, &stride},
      {MPV_RENDER_PARAM_SW_POINTER, rgba.data()},
      {MPV_RENDER_PARAM_INVALID, nullptr},
    };

    mpv_render_context_update(render_context_);
    int ret = mpv_render_context_render(render_context_, params);
    if (ret < 0) {
      throw_mpv_error(env, "mpv_render_context_render", ret);
      return env.Null();
    }

    Napi::Object frame = Napi::Object::New(env);
    frame.Set("width", width);
    frame.Set("height", height);
    frame.Set("rgba", Napi::Buffer<uint8_t>::Copy(env, rgba.data(), rgba.size()));
    return frame;
  }

  Napi::Value RenderSharedTexture(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    if (mode_ != "shared-texture") {
      Napi::Error::New(env, "renderSharedTexture is only available in shared-texture mode").ThrowAsJavaScriptException();
      return env.Null();
    }

#if !defined(__APPLE__) && !defined(_WIN32)
    Napi::Error::New(env, "renderSharedTexture is only implemented on macOS and Windows").ThrowAsJavaScriptException();
    return env.Null();
#elif defined(__APPLE__)
    int width = 960;
    int height = 540;
    if (info.Length() >= 2 && info[0].IsNumber() && info[1].IsNumber()) {
      width = std::max(2, info[0].As<Napi::Number>().Int32Value());
      height = std::max(2, info[1].As<Napi::Number>().Int32Value());
    }
    width = std::min(width, 3840);
    height = std::min(height, 2160);

    if (!ensure_iosurface_target(width, height)) {
      Napi::Error::New(env, "Failed to create IOSurface render target").ThrowAsJavaScriptException();
      return env.Null();
    }

    CGLSetCurrentContext(gl_context_);
    mpv_opengl_fbo fbo = {
      static_cast<int>(fbo_),
      width,
      height,
      GL_RGBA8,
    };
    int flip_y = 1;
    mpv_render_param params[] = {
      {MPV_RENDER_PARAM_OPENGL_FBO, &fbo},
      {MPV_RENDER_PARAM_FLIP_Y, &flip_y},
      {MPV_RENDER_PARAM_INVALID, nullptr},
    };

    mpv_render_context_update(render_context_);
    int ret = mpv_render_context_render(render_context_, params);
    glFlush();
    if (ret < 0) {
      throw_mpv_error(env, "mpv_render_context_render(opengl)", ret);
      return env.Null();
    }

    Napi::Object handle = Napi::Object::New(env);
    uintptr_t surface_ptr = reinterpret_cast<uintptr_t>(surface_);
    handle.Set("ioSurface", Napi::Buffer<uint8_t>::Copy(
      env,
      reinterpret_cast<uint8_t*>(&surface_ptr),
      sizeof(surface_ptr)
    ));

    Napi::Object size = Napi::Object::New(env);
    size.Set("width", width);
    size.Set("height", height);

    Napi::Object rect = Napi::Object::New(env);
    rect.Set("x", 0);
    rect.Set("y", 0);
    rect.Set("width", width);
    rect.Set("height", height);

    Napi::Object colorSpace = Napi::Object::New(env);
    colorSpace.Set("primaries", "bt709");
    colorSpace.Set("transfer", "srgb");
    colorSpace.Set("matrix", "rgb");
    colorSpace.Set("range", "full");

    Napi::Object textureInfo = Napi::Object::New(env);
    textureInfo.Set("id", std::to_string(reinterpret_cast<uintptr_t>(surface_)));
    textureInfo.Set("pixelFormat", "bgra");
    textureInfo.Set("codedSize", size);
    textureInfo.Set("visibleRect", rect);
    textureInfo.Set("contentRect", rect);
    textureInfo.Set("timestamp", static_cast<double>(timestamp_us_++));
    textureInfo.Set("colorSpace", colorSpace);
    textureInfo.Set("handle", handle);

    return textureInfo;
#elif defined(_WIN32)
    int width = 960;
    int height = 540;
    if (info.Length() >= 2 && info[0].IsNumber() && info[1].IsNumber()) {
      width = std::max(2, info[0].As<Napi::Number>().Int32Value());
      height = std::max(2, info[1].As<Napi::Number>().Int32Value());
    }
    width = std::min(width, 3840);
    height = std::min(height, 2160);

    if (!ensure_dx_shared_target(width, height)) {
      Napi::Error::New(env, dx_error_.empty() ? "Failed to create WGL/D3D11 shared texture target" : dx_error_).ThrowAsJavaScriptException();
      return env.Null();
    }

    wglMakeCurrent(win_dc_, win_gl_context_);
    if (!wglDXLockObjectsNV_(dx_interop_device_, 1, &dx_interop_object_)) {
      Napi::Error::New(env, "wglDXLockObjectsNV failed").ThrowAsJavaScriptException();
      return env.Null();
    }

    glBindFramebuffer_(GL_FRAMEBUFFER, win_gl_fbo_);
    mpv_opengl_fbo fbo = {
      static_cast<int>(win_gl_fbo_),
      width,
      height,
      GL_RGBA8,
    };
    int flip_y = 1;
    mpv_render_param params[] = {
      {MPV_RENDER_PARAM_OPENGL_FBO, &fbo},
      {MPV_RENDER_PARAM_FLIP_Y, &flip_y},
      {MPV_RENDER_PARAM_INVALID, nullptr},
    };

    mpv_render_context_update(render_context_);
    int ret = mpv_render_context_render(render_context_, params);
    glFlush();
    BOOL unlocked = wglDXUnlockObjectsNV_(dx_interop_device_, 1, &dx_interop_object_);
    if (ret < 0) {
      throw_mpv_error(env, "mpv_render_context_render(opengl)", ret);
      return env.Null();
    }
    if (!unlocked) {
      Napi::Error::New(env, "wglDXUnlockObjectsNV failed").ThrowAsJavaScriptException();
      return env.Null();
    }

    HRESULT copy_hr = dx_keyed_mutex_->AcquireSync(0, 1000);
    if (FAILED(copy_hr)) {
      Napi::Error::New(env, "IDXGIKeyedMutex::AcquireSync failed: " + hex_u32(static_cast<unsigned long>(copy_hr))).ThrowAsJavaScriptException();
      return env.Null();
    }
    d3d_context_->CopyResource(d3d_export_texture_, d3d_interop_texture_);
    d3d_context_->Flush();
    copy_hr = dx_keyed_mutex_->ReleaseSync(0);
    if (FAILED(copy_hr)) {
      Napi::Error::New(env, "IDXGIKeyedMutex::ReleaseSync failed: " + hex_u32(static_cast<unsigned long>(copy_hr))).ThrowAsJavaScriptException();
      return env.Null();
    }

    Napi::Object handle = Napi::Object::New(env);
    uintptr_t nt_handle = reinterpret_cast<uintptr_t>(dx_shared_handle_);
    handle.Set("ntHandle", Napi::Buffer<uint8_t>::Copy(
      env,
      reinterpret_cast<uint8_t*>(&nt_handle),
      sizeof(nt_handle)
    ));

    Napi::Object size = Napi::Object::New(env);
    size.Set("width", width);
    size.Set("height", height);

    Napi::Object rect = Napi::Object::New(env);
    rect.Set("x", 0);
    rect.Set("y", 0);
    rect.Set("width", width);
    rect.Set("height", height);

    Napi::Object colorSpace = Napi::Object::New(env);
    colorSpace.Set("primaries", "bt709");
    colorSpace.Set("transfer", "srgb");
    colorSpace.Set("matrix", "rgb");
    colorSpace.Set("range", "full");

    Napi::Object textureInfo = Napi::Object::New(env);
    textureInfo.Set("id", std::to_string(reinterpret_cast<uintptr_t>(dx_shared_handle_)));
    textureInfo.Set("pixelFormat", "bgra");
    textureInfo.Set("codedSize", size);
    textureInfo.Set("visibleRect", rect);
    textureInfo.Set("contentRect", rect);
    textureInfo.Set("timestamp", static_cast<double>(timestamp_us_++));
    textureInfo.Set("colorSpace", colorSpace);
    textureInfo.Set("handle", handle);

    return textureInfo;
#endif
  }

  Napi::Value PollEvents(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    Napi::Array events = Napi::Array::New(env);
    uint32_t index = 0;

    while (handle_) {
      mpv_event* event = mpv_wait_event(handle_, 0);
      if (!event || event->event_id == MPV_EVENT_NONE) break;

      Napi::Object item = Napi::Object::New(env);
      item.Set("id", static_cast<int>(event->event_id));
      item.Set("type", mpv_event_name(event->event_id));

      if (event->error < 0) {
        item.Set("error", mpv_error_text(event->error));
      }

      if (event->event_id == MPV_EVENT_LOG_MESSAGE && event->data) {
        mpv_event_log_message* message = static_cast<mpv_event_log_message*>(event->data);
        item.Set("name", message->prefix ? message->prefix : "mpv");
        item.Set("level", message->level ? message->level : "warn");
        item.Set("data", message->text ? message->text : "");
      }

      if (event->event_id == MPV_EVENT_END_FILE && event->data) {
        mpv_event_end_file* end = static_cast<mpv_event_end_file*>(event->data);
        switch (end->reason) {
          case MPV_END_FILE_REASON_EOF:
            item.Set("reason", "eof");
            break;
          case MPV_END_FILE_REASON_STOP:
            item.Set("reason", "stop");
            break;
          case MPV_END_FILE_REASON_QUIT:
            item.Set("reason", "quit");
            break;
          case MPV_END_FILE_REASON_ERROR:
            item.Set("reason", "error");
            if (end->error < 0) item.Set("error", mpv_error_text(end->error));
            break;
          case MPV_END_FILE_REASON_REDIRECT:
            item.Set("reason", "redirect");
            break;
          default:
            item.Set("reason", "unknown");
            break;
        }
      }

      if (event->event_id == MPV_EVENT_FILE_LOADED) {
        loaded_ = true;
        if (pending_audio_) {
          int ret = apply_network_profile(pending_audio_network_);
          if (ret >= 0) ret = command({"audio-add", pending_audio_url_, "select",
                                      pending_audio_title_, pending_audio_language_});
          pending_audio_ = false;
          if (ret < 0) item.Set("error", "audio-add failed: " + mpv_error_text(ret));
          else has_external_audio_ = true;
        }
        if (pending_subtitle_) {
          int ret = apply_network_profile(pending_subtitle_network_);
          if (ret >= 0) ret = command({"sub-add", pending_subtitle_url_, "select",
                                      pending_subtitle_title_, pending_subtitle_language_});
          pending_subtitle_ = false;
          if (ret < 0) item.Set("error", "sub-add failed: " + mpv_error_text(ret));
          else has_external_subtitle_ = true;
        }
      }

      if (event->event_id == MPV_EVENT_PROPERTY_CHANGE && event->data) {
        mpv_event_property* prop = static_cast<mpv_event_property*>(event->data);
        item.Set("name", prop->name ? prop->name : "");
        if (prop->data) {
          switch (prop->format) {
            case MPV_FORMAT_FLAG:
              item.Set("data", *static_cast<int*>(prop->data) != 0);
              break;
            case MPV_FORMAT_INT64:
              item.Set("data", static_cast<double>(*static_cast<int64_t*>(prop->data)));
              break;
            case MPV_FORMAT_DOUBLE:
              item.Set("data", *static_cast<double*>(prop->data));
              break;
            case MPV_FORMAT_STRING: {
              char* value = *static_cast<char**>(prop->data);
              item.Set("data", value ? value : "");
              break;
            }
            default:
              item.Set("data", env.Null());
              break;
          }
        } else {
          item.Set("data", env.Null());
        }
      }

      events.Set(index++, item);
    }

    return events;
  }

  Napi::Value Destroy(const Napi::CallbackInfo& info) {
    cleanup();
    return info.Env().Undefined();
  }

  void cleanup() {
    alive_ = false;
    if (handle_) {
      const char* stop[] = {"stop", nullptr};
      mpv_command(handle_, stop);
      set_mpv_wakeup_callback(handle_, nullptr, nullptr);
    }
    if (render_context_) {
      set_mpv_render_update_callback(render_context_, nullptr, nullptr);
    }
    if (update_callback_) {
      update_callback_.Release();
      update_callback_ = nullptr;
    }
    if (event_callback_) {
      event_callback_.Release();
      event_callback_ = nullptr;
    }
    if (render_context_) {
      mpv_render_context_free(render_context_);
      render_context_ = nullptr;
    }
    if (handle_) {
#ifdef __linux__
      // Linux libmpv lives in an isolated linker namespace so its FFmpeg stack
      // cannot bind to Electron's. Reuse initialized handles instead of calling
      // the namespace-unsafe terminal destructor; renderer and media state have
      // already been released, so the pooled core remains idle between players.
      recycle_linux_handle(handle_);
      handle_ = nullptr;
#else
      mpv_terminate_destroy(handle_);
      handle_ = nullptr;
#endif
    }
#ifdef __APPLE__
    destroy_iosurface_target();
    if (gl_context_) {
      CGLDestroyContext(gl_context_);
      gl_context_ = nullptr;
    }
#endif
#ifdef _WIN32
    destroy_dx_shared_target();
    if (dx_interop_device_) {
      wglDXCloseDeviceNV_(dx_interop_device_);
      dx_interop_device_ = nullptr;
    }
    if (d3d_context_) {
      d3d_context_->Release();
      d3d_context_ = nullptr;
    }
    if (d3d_device_) {
      d3d_device_->Release();
      d3d_device_ = nullptr;
    }
    if (win_gl_context_) {
      wglMakeCurrent(nullptr, nullptr);
      wglDeleteContext(win_gl_context_);
      win_gl_context_ = nullptr;
    }
    if (win_dc_) {
      ReleaseDC(win_hwnd_, win_dc_);
      win_dc_ = nullptr;
    }
    if (win_hwnd_) {
      DestroyWindow(win_hwnd_);
      win_hwnd_ = nullptr;
    }
#endif
  }

#ifdef __APPLE__
  static void* get_proc_address(void*, const char* name) {
    return dlsym(RTLD_DEFAULT, name);
  }

  bool init_gl() {
    CGLPixelFormatAttribute attrs[] = {
      kCGLPFAAccelerated,
      kCGLPFAAllowOfflineRenderers,
      static_cast<CGLPixelFormatAttribute>(0),
    };
    CGLPixelFormatObj pixel_format = nullptr;
    GLint num_formats = 0;
    if (CGLChoosePixelFormat(attrs, &pixel_format, &num_formats) != kCGLNoError || !pixel_format) {
      return false;
    }
    CGLContextObj context = nullptr;
    CGLError error = CGLCreateContext(pixel_format, nullptr, &context);
    CGLDestroyPixelFormat(pixel_format);
    if (error != kCGLNoError || !context) {
      return false;
    }
    gl_context_ = context;
    return true;
  }

  void destroy_iosurface_target() {
    if (gl_context_) {
      CGLSetCurrentContext(gl_context_);
      if (fbo_) {
        glDeleteFramebuffers(1, &fbo_);
        fbo_ = 0;
      }
      if (texture_) {
        glDeleteTextures(1, &texture_);
        texture_ = 0;
      }
    }
    if (surface_) {
      CFRelease(surface_);
      surface_ = nullptr;
    }
    surface_width_ = 0;
    surface_height_ = 0;
  }

  bool ensure_iosurface_target(int width, int height) {
    if (surface_ && surface_width_ == width && surface_height_ == height) {
      return true;
    }

    destroy_iosurface_target();
    CGLSetCurrentContext(gl_context_);

    CFMutableDictionaryRef props = CFDictionaryCreateMutable(
      kCFAllocatorDefault,
      0,
      &kCFTypeDictionaryKeyCallBacks,
      &kCFTypeDictionaryValueCallBacks
    );
    if (!props) return false;

    auto set_number = [&](CFStringRef key, int value) {
      CFNumberRef number = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &value);
      CFDictionarySetValue(props, key, number);
      CFRelease(number);
    };

    int pixel_format = 'BGRA';
    int bytes_per_row = static_cast<int>(IOSurfaceAlignProperty(kIOSurfaceBytesPerRow, width * 4));
    int alloc_size = static_cast<int>(IOSurfaceAlignProperty(kIOSurfaceAllocSize, bytes_per_row * height));
    set_number(kIOSurfaceWidth, width);
    set_number(kIOSurfaceHeight, height);
    set_number(kIOSurfaceBytesPerElement, 4);
    set_number(kIOSurfaceBytesPerRow, bytes_per_row);
    set_number(kIOSurfaceAllocSize, alloc_size);
    set_number(kIOSurfacePixelFormat, pixel_format);

    IOSurfaceRef surface = IOSurfaceCreate(props);
    CFRelease(props);
    if (!surface) return false;

    glGenTextures(1, &texture_);
    glBindTexture(GL_TEXTURE_RECTANGLE, texture_);
    glTexParameteri(GL_TEXTURE_RECTANGLE, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_RECTANGLE, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_RECTANGLE, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_RECTANGLE, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    CGLError error = CGLTexImageIOSurface2D(
      gl_context_,
      GL_TEXTURE_RECTANGLE,
      GL_RGBA,
      width,
      height,
      GL_BGRA,
      GL_UNSIGNED_INT_8_8_8_8_REV,
      surface,
      0
    );
    if (error != kCGLNoError) {
      CFRelease(surface);
      return false;
    }

    glGenFramebuffers(1, &fbo_);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_RECTANGLE, texture_, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
      CFRelease(surface);
      destroy_iosurface_target();
      return false;
    }

    surface_ = surface;
    surface_width_ = width;
    surface_height_ = height;
    return true;
  }
#endif

#ifdef _WIN32
  static void* get_proc_address(void*, const char* name) {
    void* proc = reinterpret_cast<void*>(wglGetProcAddress(name));
    if (proc) return proc;
    HMODULE opengl = GetModuleHandleA("opengl32.dll");
    if (!opengl) opengl = LoadLibraryA("opengl32.dll");
    return opengl ? reinterpret_cast<void*>(GetProcAddress(opengl, name)) : nullptr;
  }

  template <typename T>
  bool load_gl_proc(T& out, const char* name) {
    out = reinterpret_cast<T>(get_proc_address(nullptr, name));
    return out != nullptr;
  }

  bool init_gl() {
    HINSTANCE instance = GetModuleHandle(nullptr);
    WNDCLASSA wc = {};
    wc.lpfnWndProc = hidden_wnd_proc;
    wc.hInstance = instance;
    wc.lpszClassName = "ElectronMpvVideoHiddenGLWindow";
    RegisterClassA(&wc);

    win_hwnd_ = CreateWindowA(
      wc.lpszClassName,
      "electron-mpv-video-gl",
      WS_OVERLAPPEDWINDOW,
      0,
      0,
      1,
      1,
      nullptr,
      nullptr,
      instance,
      nullptr
    );
    if (!win_hwnd_) return false;

    win_dc_ = GetDC(win_hwnd_);
    if (!win_dc_) return false;

    PIXELFORMATDESCRIPTOR pfd = {};
    pfd.nSize = sizeof(pfd);
    pfd.nVersion = 1;
    pfd.dwFlags = PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER;
    pfd.iPixelType = PFD_TYPE_RGBA;
    pfd.cColorBits = 32;
    pfd.cDepthBits = 24;
    pfd.iLayerType = PFD_MAIN_PLANE;

    int pixel_format = ChoosePixelFormat(win_dc_, &pfd);
    if (!pixel_format || !SetPixelFormat(win_dc_, pixel_format, &pfd)) {
      return false;
    }

    win_gl_context_ = wglCreateContext(win_dc_);
    if (!win_gl_context_ || !wglMakeCurrent(win_dc_, win_gl_context_)) {
      return false;
    }

    if (!load_gl_proc(glGenFramebuffers_, "glGenFramebuffers") ||
        !load_gl_proc(glBindFramebuffer_, "glBindFramebuffer") ||
        !load_gl_proc(glFramebufferTexture2D_, "glFramebufferTexture2D") ||
        !load_gl_proc(glCheckFramebufferStatus_, "glCheckFramebufferStatus") ||
        !load_gl_proc(glDeleteFramebuffers_, "glDeleteFramebuffers") ||
        !load_gl_proc(wglDXOpenDeviceNV_, "wglDXOpenDeviceNV") ||
        !load_gl_proc(wglDXCloseDeviceNV_, "wglDXCloseDeviceNV") ||
        !load_gl_proc(wglDXRegisterObjectNV_, "wglDXRegisterObjectNV") ||
        !load_gl_proc(wglDXUnregisterObjectNV_, "wglDXUnregisterObjectNV") ||
        !load_gl_proc(wglDXLockObjectsNV_, "wglDXLockObjectsNV") ||
        !load_gl_proc(wglDXUnlockObjectsNV_, "wglDXUnlockObjectsNV")) {
      return false;
    }

    D3D_FEATURE_LEVEL levels[] = {
      D3D_FEATURE_LEVEL_11_1,
      D3D_FEATURE_LEVEL_11_0,
      D3D_FEATURE_LEVEL_10_1,
      D3D_FEATURE_LEVEL_10_0,
    };
    D3D_FEATURE_LEVEL selected_level = D3D_FEATURE_LEVEL_11_0;
    HRESULT hr = D3D11CreateDevice(
      nullptr,
      D3D_DRIVER_TYPE_HARDWARE,
      nullptr,
      D3D11_CREATE_DEVICE_BGRA_SUPPORT,
      levels,
      ARRAYSIZE(levels),
      D3D11_SDK_VERSION,
      &d3d_device_,
      &selected_level,
      &d3d_context_
    );
    if (FAILED(hr)) {
      hr = D3D11CreateDevice(
        nullptr,
        D3D_DRIVER_TYPE_WARP,
        nullptr,
        D3D11_CREATE_DEVICE_BGRA_SUPPORT,
        levels,
        ARRAYSIZE(levels),
        D3D11_SDK_VERSION,
        &d3d_device_,
        &selected_level,
        &d3d_context_
      );
      if (FAILED(hr)) return false;
    }

    dx_interop_device_ = wglDXOpenDeviceNV_(d3d_device_);
    return dx_interop_device_ != nullptr;
  }

  void destroy_dx_shared_target() {
    if (win_gl_context_) {
      wglMakeCurrent(win_dc_, win_gl_context_);
      if (dx_interop_object_) {
        wglDXUnregisterObjectNV_(dx_interop_device_, dx_interop_object_);
        dx_interop_object_ = nullptr;
      }
      if (win_gl_fbo_) {
        glDeleteFramebuffers_(1, &win_gl_fbo_);
        win_gl_fbo_ = 0;
      }
      if (win_gl_texture_) {
        glDeleteTextures(1, &win_gl_texture_);
        win_gl_texture_ = 0;
      }
    }
    if (dx_shared_handle_) {
      CloseHandle(dx_shared_handle_);
      dx_shared_handle_ = nullptr;
    }
    if (dx_keyed_mutex_) {
      dx_keyed_mutex_->Release();
      dx_keyed_mutex_ = nullptr;
    }
    if (d3d_export_texture_) {
      d3d_export_texture_->Release();
      d3d_export_texture_ = nullptr;
    }
    if (d3d_interop_texture_) {
      d3d_interop_texture_->Release();
      d3d_interop_texture_ = nullptr;
    }
    dx_width_ = 0;
    dx_height_ = 0;
  }

  bool ensure_dx_shared_target(int width, int height) {
    if (d3d_export_texture_ && d3d_interop_texture_ && dx_width_ == width && dx_height_ == height) {
      return true;
    }

    destroy_dx_shared_target();
    dx_error_.clear();
    wglMakeCurrent(win_dc_, win_gl_context_);

    D3D11_TEXTURE2D_DESC interop_desc = {};
    interop_desc.Width = static_cast<UINT>(width);
    interop_desc.Height = static_cast<UINT>(height);
    interop_desc.MipLevels = 1;
    interop_desc.ArraySize = 1;
    interop_desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
    interop_desc.SampleDesc.Count = 1;
    interop_desc.Usage = D3D11_USAGE_DEFAULT;
    interop_desc.BindFlags = D3D11_BIND_RENDER_TARGET | D3D11_BIND_SHADER_RESOURCE;
    interop_desc.MiscFlags = D3D11_RESOURCE_MISC_SHARED;

    HRESULT hr = d3d_device_->CreateTexture2D(&interop_desc, nullptr, &d3d_interop_texture_);
    if (FAILED(hr)) {
      dx_error_ = "CreateTexture2D(interop) failed: " + hex_u32(static_cast<unsigned long>(hr));
      return false;
    }

    D3D11_TEXTURE2D_DESC export_desc = interop_desc;
    export_desc.MiscFlags = D3D11_RESOURCE_MISC_SHARED_NTHANDLE | D3D11_RESOURCE_MISC_SHARED_KEYEDMUTEX;
    hr = d3d_device_->CreateTexture2D(&export_desc, nullptr, &d3d_export_texture_);
    if (FAILED(hr)) {
      dx_error_ = "CreateTexture2D(export) failed: " + hex_u32(static_cast<unsigned long>(hr));
      destroy_dx_shared_target();
      return false;
    }

    hr = d3d_export_texture_->QueryInterface(__uuidof(IDXGIKeyedMutex), reinterpret_cast<void**>(&dx_keyed_mutex_));
    if (FAILED(hr) || !dx_keyed_mutex_) {
      dx_error_ = "QueryInterface(IDXGIKeyedMutex) failed: " + hex_u32(static_cast<unsigned long>(hr));
      destroy_dx_shared_target();
      return false;
    }

    IDXGIResource1* dxgi_resource = nullptr;
    hr = d3d_export_texture_->QueryInterface(__uuidof(IDXGIResource1), reinterpret_cast<void**>(&dxgi_resource));
    if (FAILED(hr) || !dxgi_resource) {
      dx_error_ = "QueryInterface(IDXGIResource1) failed: " + hex_u32(static_cast<unsigned long>(hr));
      destroy_dx_shared_target();
      return false;
    }

    hr = dxgi_resource->CreateSharedHandle(
      nullptr,
      DXGI_SHARED_RESOURCE_READ | DXGI_SHARED_RESOURCE_WRITE,
      nullptr,
      &dx_shared_handle_
    );
    dxgi_resource->Release();
    if (FAILED(hr) || !dx_shared_handle_) {
      dx_error_ = "CreateSharedHandle failed: " + hex_u32(static_cast<unsigned long>(hr));
      destroy_dx_shared_target();
      return false;
    }

    glGenTextures(1, &win_gl_texture_);
    glBindTexture(GL_TEXTURE_2D, win_gl_texture_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    dx_interop_object_ = wglDXRegisterObjectNV_(
      dx_interop_device_,
      d3d_interop_texture_,
      win_gl_texture_,
      GL_TEXTURE_2D,
      WGL_ACCESS_WRITE_DISCARD_NV
    );
    if (!dx_interop_object_) {
      dx_error_ = "wglDXRegisterObjectNV failed: " + hex_u32(GetLastError());
      destroy_dx_shared_target();
      return false;
    }

    if (!wglDXLockObjectsNV_(dx_interop_device_, 1, &dx_interop_object_)) {
      dx_error_ = "wglDXLockObjectsNV(target setup) failed: " + hex_u32(GetLastError());
      destroy_dx_shared_target();
      return false;
    }

    glGenFramebuffers_(1, &win_gl_fbo_);
    glBindFramebuffer_(GL_FRAMEBUFFER, win_gl_fbo_);
    glFramebufferTexture2D_(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, win_gl_texture_, 0);
    bool complete = glCheckFramebufferStatus_(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    wglDXUnlockObjectsNV_(dx_interop_device_, 1, &dx_interop_object_);
    if (!complete) {
      dx_error_ = "OpenGL framebuffer for WGL/D3D11 interop texture is incomplete";
      destroy_dx_shared_target();
      return false;
    }

    dx_width_ = width;
    dx_height_ = height;
    return true;
  }
#endif

  mpv_handle* handle_ = nullptr;
  mpv_render_context* render_context_ = nullptr;
  std::atomic<bool> alive_ = true;
  Napi::ThreadSafeFunction update_callback_;
  Napi::ThreadSafeFunction event_callback_;
  uint64_t next_observer_id_ = 1;
  bool has_external_audio_ = false;
  bool has_external_subtitle_ = false;
  bool loaded_ = false;
  bool pending_audio_ = false;
  bool pending_subtitle_ = false;
  bool skip_silence_ = false;
  bool pitch_uses_filter_ = false;
  double playback_pitch_ = 1.0;
  std::vector<double> equalizer_gains_;
  std::string pending_audio_url_;
  std::string pending_audio_title_;
  std::string pending_audio_language_;
  std::string pending_subtitle_url_;
  std::string pending_subtitle_title_;
  std::string pending_subtitle_language_;
  MediaNetworkProfile pending_audio_network_;
  MediaNetworkProfile pending_subtitle_network_;
  std::string mode_ = "software";
  std::string tls_ca_file_;
  int64_t timestamp_us_ = 0;
#ifdef __APPLE__
  CGLContextObj gl_context_ = nullptr;
  IOSurfaceRef surface_ = nullptr;
  GLuint texture_ = 0;
  GLuint fbo_ = 0;
  int surface_width_ = 0;
  int surface_height_ = 0;
#endif
#ifdef _WIN32
  HWND win_hwnd_ = nullptr;
  HDC win_dc_ = nullptr;
  HGLRC win_gl_context_ = nullptr;
  ID3D11Device* d3d_device_ = nullptr;
  ID3D11DeviceContext* d3d_context_ = nullptr;
  ID3D11Texture2D* d3d_interop_texture_ = nullptr;
  ID3D11Texture2D* d3d_export_texture_ = nullptr;
  IDXGIKeyedMutex* dx_keyed_mutex_ = nullptr;
  HANDLE dx_shared_handle_ = nullptr;
  HANDLE dx_interop_device_ = nullptr;
  HANDLE dx_interop_object_ = nullptr;
  GLuint win_gl_texture_ = 0;
  GLuint win_gl_fbo_ = 0;
  int dx_width_ = 0;
  int dx_height_ = 0;
  std::string dx_error_;
  PFNGLGENFRAMEBUFFERSPROC glGenFramebuffers_ = nullptr;
  PFNGLBINDFRAMEBUFFERPROC glBindFramebuffer_ = nullptr;
  PFNGLFRAMEBUFFERTEXTURE2DPROC glFramebufferTexture2D_ = nullptr;
  PFNGLCHECKFRAMEBUFFERSTATUSPROC glCheckFramebufferStatus_ = nullptr;
  PFNGLDELETEFRAMEBUFFERSPROC glDeleteFramebuffers_ = nullptr;
  PFNWGLDXOPENDEVICENVPROC wglDXOpenDeviceNV_ = nullptr;
  PFNWGLDXCLOSEDEVICENVPROC wglDXCloseDeviceNV_ = nullptr;
  PFNWGLDXREGISTEROBJECTNVPROC wglDXRegisterObjectNV_ = nullptr;
  PFNWGLDXUNREGISTEROBJECTNVPROC wglDXUnregisterObjectNV_ = nullptr;
  PFNWGLDXLOCKOBJECTSNVPROC wglDXLockObjectsNV_ = nullptr;
  PFNWGLDXUNLOCKOBJECTSNVPROC wglDXUnlockObjectsNV_ = nullptr;
#endif
};

Napi::Object Init(Napi::Env env, Napi::Object exports) {
  exports.Set("exitProcess", Napi::Function::New(env, [](const Napi::CallbackInfo& info) {
    int exit_code = 0;
    if (info.Length() > 0 && info[0].IsNumber()) {
      exit_code = info[0].As<Napi::Number>().Int32Value();
    }
    std::fflush(nullptr);
    std::_Exit(exit_code);
  }));
  return MpvPlayer::Init(env, exports);
}

NODE_API_MODULE(mpv_addon, Init)

}  // namespace
