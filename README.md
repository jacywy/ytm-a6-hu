# CarYTM - YouTube Music Client for Legacy Android 6.0 Car Head Units

<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher.xml" width="96" height="96" alt="CarYTM Logo" />
</p>

<p align="center">
  <a href="https://github.com/jacywy/ytm-a6-hu/actions/workflows/build.yml"><img src="https://github.com/jacywy/ytm-a6-hu/actions/workflows/build.yml/badge.svg" alt="Build Status" /></a>
  <a href="https://github.com/jacywy/ytm-a6-hu/releases"><img src="https://img.shields.io/github/v/release/jacywy/ytm-a6-hu?include_prereleases&label=Release" alt="Latest Release" /></a>
  <img src="https://img.shields.io/badge/Platform-Android%206.0%2B%20%28API%2023%2B%29-green.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/Architecture-Landscape%20Car%20HU-blue.svg" alt="Car HU" />
  <img src="https://img.shields.io/badge/License-GPL%20v3-orange.svg" alt="License" />
</p>

---

## 🌐 Language Navigation / 语言导航
- [English](#-carytm---overview-english)
- [中文版](#-carytm---老款-android-6-车机专属-youtube-music-客户端中文)

---

# 🎵 CarYTM - Overview (English)

**CarYTM** is a lightweight, open-source YouTube Music native client designed specifically for **legacy automotive head units running Android 6.0 Marshmallow (API 23+)** with horizontal touchscreens (**1024×600** and **800×480**).

Legacy in-dash car infotainment units suffer from severe constraints:
- **Frozen, outdated System WebViews** (Chrome 44–53) that cannot be updated and are blocked by Google OAuth with `"This browser or app may not be secure"`.
- **Expired Root CA certificates and lack of TLS 1.3**, causing constant `SSLHandshakeException` errors.
- **Limited RAM (1 GB – 2 GB)** and low-end automotive SoCs (Allwinner T3, Rockchip RK3188, Spreadtrum).
- **Frequent YouTube protocol changes**, breaking stream URLs and SABR playback.
- **Unstable cellular network** during driving (tunnels, mountain roads, parking garages).

CarYTM solves all these challenges with zero WebView dependencies, modern BoringSSL cryptography, pure offline playback, steering wheel controls, and instant CI/CD self-healing builds.

---

## 🚗 Key Features & Architectural Highlights

### 1. Zero-WebView Account Authentication (Google TV Device Flow & LAN Cookie Sync)
* **The Problem**: Android 6's WebView cannot access `accounts.google.com`. Google aggressively blocks old browsers from logging in.
* **The Solution**: Implements **Google TV / SmartTube Device Code Flow (`/o/oauth2/device/code`)**:
  1. Open CarYTM, display a QR code and an 8-character user code on the car display.
  2. Scan the QR code or visit `https://www.google.com/device` on your mobile phone or laptop.
  3. Authorize your account; the car head unit polls the token endpoint and automatically syncs your personal playlists, liked songs, and library. **Zero web pages are rendered on the car unit.**
* **LAN Cookie Import**: Built-in lightweight HTTP server (`NanoHTTPD`). Connect your phone to the car's Wi-Fi hotspot and access `http://<car-ip>:8888` to paste and sync browser cookies instantly with a bilingual web UI.

### 2. TLS 1.3 & Modern Cryptography on Android 6 (BoringSSL)
* Ships with **Google Conscrypt (BoringSSL)** injected into the Security Provider on app launch.
* Fully backports **TLS 1.3** and modern trusted root certificates to Android 6.0 devices, eliminating all SSL handshake and cipher suite failures when communicating with Google endpoints.

### 3. Decoupled Protocol Engine & "2-Minute Self-Healing" via GitHub Actions
* Powered by **`TeamNewPipe/NewPipeExtractor` (v0.26.5+)** with headless JS stream deciphering (handling `s` and `n` parameter transformations).
* **Automated CI/CD Hot-Fix Workflow**: Whenever YouTube changes its backend encryption algorithms, you do not need an Android development environment on your PC. Simply change the version tag in `build.gradle` on the GitHub website, and GitHub Actions automatically compiles and releases a new APK within 3 minutes.

### 4. Dedicated "Offline Music" Library & Zero-Network Playback
* **Pure Offline Playback**: Cached songs play directly from local storage (`SimpleCache`) without cellular network or SIM cards.
* **Offline Library**: Dedicated card in the Library tab showing total cached tracks, with track selection, "Play All", and "Shuffle" controls.

### 5. Full-Track Background Preloading
* **Full-Song Pre-Caching**: While the current song is playing, a background coroutine pre-resolves the next track's URL and caches the audio stream to local disk.
* **Deterministic Shuffle Candidate Preload**: In shuffle mode, the next random candidate is pre-selected and cached in advance for seamless, zero-buffer playback.

### 6. Smart Cache Eviction & Custom Storage Limit
* **Configurable Storage Limit**: Choose between `200 MB`, `500 MB (Default)`, `1 GB`, `2 GB`, or `5 GB` directly from Settings.
* **LRU Eviction**: Automatically purges incomplete fragments first, then evicts the least recently played tracks when the storage threshold is reached.

### 7. Automotive Audio Focus & Navigation Ducking
* **Navigation Voice Ducking (0.2f)**: When navigation apps (Amap, Baidu Maps, Google Maps) speak, music volume smoothly lowers to 20% and restores to 100% when finished.
* **Call Interruption Handling**: Automatically pauses during incoming phone calls and smoothly resumes playback when the call ends.

### 8. Automotive Ergonomic UI & Hardware Integration
* **Lightweight Native UI**: Pure **XML + ViewBinding + RecyclerView** with a memory footprint of only **50 MB – 80 MB**, avoiding GC overhead on legacy automotive chips.
* **Landscape Ergonomics**: 130dp left rail navigation with dual-column cards designed for `1024×600` and `800×480` displays with large touch targets (`>= 48–56dp`).
* **Steering Wheel Controls**: Full `MediaSessionCompat` and `MediaButtonReceiver` integration for steering wheel track skipping and play/pause buttons.

### 9. Bilingual Localization (English & Simplified Chinese)
* **Automatic & Manual Switching**: Automatically adapts to vehicle system language, with an in-app language switcher under Settings (`Follow System`, `简体中文`, `English`).

---

## 📱 Installation & Downloads

### Download Pre-built APKs
Download the latest APK release from the [GitHub Releases](https://github.com/jacywy/ytm-a6-hu/releases) page:
- **Latest Release**: [v0.2.7 - App Debug APK](https://github.com/jacywy/ytm-a6-hu/releases/download/v0.2.7/app-debug.apk)

### Install via ADB (USB or Wi-Fi)
```bash
adb install -r app-debug.apk
adb shell am start -n com.carytm.music/.ui.MainActivity
```

---

## 🔐 Login Instructions

### Method 1: Mobile QR Code / Google TV Code (Recommended)
1. In CarYTM, go to **Library** or **Settings**, tap **"Phone QR / TV Login"**.
2. A QR code and an 8-letter code (e.g. `ABCD-EFGH`) will appear.
3. Scan the QR code or navigate to `https://www.google.com/device` on your phone/PC browser.
4. Enter the 8-letter code and grant permission to your Google account.
5. CarYTM will sync your personalized YouTube Music playlists and Liked Songs within 3–5 seconds.

### Method 2: Local Wi-Fi Cookie Import (Fallback)
1. Ensure your phone is connected to the same Wi-Fi or car hotspot.
2. In CarYTM **Settings**, tap **"LAN Cookie Import"**.
3. The prompt displays a URL like `http://192.168.43.1:8888`.
4. Open this URL on your phone's browser, paste your YouTube cookies, and tap Submit.

---

## 🔄 Self-Healing Guide: Update NewPipeExtractor in 2 Minutes

When YouTube modifies its anti-scraping or cipher algorithms:
1. Check the newest release at [TeamNewPipe/NewPipeExtractor Releases](https://github.com/TeamNewPipe/NewPipeExtractor/releases).
2. Open `build.gradle` in the root of this repository:
   ```groovy
   ext {
       newPipeExtractorVersion = "v0.26.5" // Change to the latest version
   }
   ```
3. Commit changes on GitHub. GitHub Actions will build a freshly fixed APK within 3 minutes.

---

## 📂 Project Structure

```
CarYTM/
├── .github/workflows/build.yml     # Automated GitHub Actions CI/CD pipeline
├── app/
│   ├── build.gradle                # Dependencies (Conscrypt, ExoPlayer, NewPipe, Glide)
│   ├── src/main/
│   │   ├── AndroidManifest.xml     # Landscape config, MediaButtonReceiver, Foreground Service
│   │   ├── java/com/carytm/music/
│   │   │   ├── CarYtmApp.kt        # App lifecycle & Conscrypt TLS 1.3 bootstrap
│   │   │   ├── auth/               # Google TV device auth & NanoHTTPD cookie sync
│   │   │   ├── extractor/          # NewPipeExtractor integration & stream resolver
│   │   │   ├── model/              # SongItem, PlaylistItem, AuthModels
│   │   │   ├── net/                # Conscrypt TLS OkHttp clients & Innertube API
│   │   │   ├── player/             # ExoPlayer, OfflineRepository, CarAudioFocusManager, PlaybackService
│   │   │   ├── ui/                 # Landscape Rail UI, Offline detail, PlayerActivity, Settings
│   │   │   └── util/               # LocaleHelper (multi-language dynamic switching)
│   │   └── res/                    # Layouts, themes, values (en), values-zh, values-en
│   └── proguard-rules.pro          # ProGuard rules for Conscrypt, ExoPlayer, NewPipe
└── build.gradle                    # Top-level build file with centralized extractor version
```

---

## 📄 License
This project is licensed under the [GPL-3.0 License](LICENSE).

---

<br />

# 🚗 CarYTM - 老款 Android 6 车机专属 YouTube Music 客户端（中文）

专为**老款车载中控大屏（Android 6.0 Marshmallow, API 23+）**量身打造的开源 YouTube Music 原生客户端。针对老款车机**低分辨率横屏（1024×600 / 800×480）**、**系统内置 WebView 无法升级**、**运行内存极度紧张（1GB~2GB）**、**TLS 根证书过期**以及**YouTube 官方协议频繁改动**等严苛痛点进行了专项工程攻坚。

---

## 🌟 核心特色与技术攻坚

### 1. 彻底移除 WebView 依赖：免 WebView 账号登录（Google TV 设备码授权与局域网 Cookie 导入）
* **痛点**：Android 6 的内置 WebView 冻结在 Chrome 44~53 内核且无法升级。访问 Google 登录页会被安全策略强行拦截（提示 *"This browser or app may not be secure"*）。
* **方案**：采用类似 **SmartTube / Google TV 的 Device Code Flow（设备码授权）**：
  1. 车机屏幕自动生成专属二维码与 8 位大写授权码；
  2. 手机扫码直达 `https://www.google.com/device`，确认授权；
  3. 车机后台自动完成 Token 轮询并同步个人歌单、喜欢收藏与历史记录，**车机全程不加载任何网页**！
* **局域网 Cookie 导入备选**：内置轻量 NanoHTTPD 服务，手机连接车机 Wi-Fi/热点后访问车机 IP（端口 8888）即可一键粘贴 Cookie 导入，支持中英双语网页提示。

### 2. 补齐 TLS 1.3 与现代化证书信任链（Google Conscrypt / BoringSSL）
* 底层集成 **Google Conscrypt (BoringSSL)** 引擎，在 App 启动时优先注入系统 Security Provider。
* 为 Android 6.0 补全 **TLS 1.3** 协议栈及最新根证书库，彻底解决直接连接 Google/YouTube CDN 节点时的 `SSLHandshakeException`。

### 3. 抗 YouTube 协议变动：解耦架构与 2 分钟极速自愈（GitHub Actions 自动化编译）
* 音频流解析全面接入全球维护最活跃的 **`TeamNewPipe/NewPipeExtractor`** 引擎（内置脱机 JS 虚拟机，解密 `s` 与 `n` 签名算法）。
* **自动化云端打包流水线**：配置 GitHub Actions（`.github/workflows/build.yml`）。当 YouTube 协议发生变动时，只需在 GitHub 网页修改 `build.gradle` 中的版本号并提交，Actions 会在 **3 分钟内自动编译产出全新的 Release APK**，无需本地配置 Android 开发环境。

### 4. 专属【本地离线音乐】曲库与脱网播放
* **纯脱网本地解码**：已缓存的歌曲在无网络、无信号环境下直接从磁盘 `SimpleCache` 寻址解码，零网络消耗秒播。
* **离线专属专区**：歌单顶部常驻【本地离线音乐】卡片，实时显示离线歌曲数，支持自由选曲、一键“全部播放”与“随机播放”。

### 5. 全曲后台预缓存
* **全量流式预下载**：当前歌曲播放时，后台协程使用 `CacheWriter` 提前对下一首歌曲进行全量音频流写入。
* **随机播放提前预载**：随机模式下提前选定并缓存下一首随机歌曲，切歌时直接命中本地缓存无缝起播。

### 6. 智能缓存管理与自定义容量
* **自定义缓存上限**：设置页面提供 `200 MB`、`500 MB (默认)`、`1 GB`、`2 GB`、`5 GB` 档位调节与实时存储占用显示。
* **LRU 淘汰机制**：存储超出上限时优先清理未完整下载的文件，并按最近播放时间淘汰最久未听的歌曲。

### 7. 车规级音频焦点管理与导航压音（Audio Focus & Ducking）
* **导航语音平滑压音（Duck 0.2f）**：车载高德地图/百度地图语音播报时，音乐音量自动压低至 20%，播报完毕平滑恢复 100%，行车听歌不中断。
* **电话打断自动恢复**：接听车载电话或临时语音消息时音乐安全暂停，通话结束后自动恢复播放。

### 8. 车载专属横屏交互与方控硬件融合
* **极度轻量低功耗**：采用原生 **XML + ViewBinding + RecyclerView**，常驻运行内存仅 **50MB~80MB**，在老款车载芯片上运行流畅。
* **横屏 Rail 导航**：左侧 130dp 固定导航 Rail 与右侧双列大卡片布局，按键高度全部设为 `>= 48–56dp`，颠簸路段不易误触。
* **方向盘按键（方控）**：全面接入 `MediaSessionCompat` 与 `MediaButtonReceiver`，支持方向盘物理按键上一首、下一首、暂停/播放与中控返回键。

### 9. 完整中英双语支持（中文 / 英文）
* **跟随系统与应用内切换**：自动适配车载系统默认语言，并支持在设置中心独立切换（跟随系统 / 简体中文 / English）。

---

## 📦 安装与下载

### 预编译 APK 下载
进入本仓库的 [Releases](https://github.com/jacywy/ytm-a6-hu/releases) 页面下载最新构建：
- **最新正式版本**：[v0.2.7 - app-debug.apk](https://github.com/jacywy/ytm-a6-hu/releases/download/v0.2.7/app-debug.apk)

### 命令行安装
```bash
adb install -r app-debug.apk
adb shell am start -n com.carytm.music/.ui.MainActivity
```

---

## 📱 账号登录指南

### 方式一：手机扫码 / TV 设备码（首选）
1. 在车机打开 CarYTM，进入【歌单】或【设置】页面，点击【手机扫码 / TV 登录】；
2. 车机屏幕会展示专属二维码及 8 位大写字母代码（例如 `ABCD-EFGH`）；
3. 手机扫码，或在手机浏览器中打开 `https://www.google.com/device`；
4. 输入车机屏幕上的 8 位代码，选择 Google 账号点击授权；
5. 车机端将在 3~5 秒内自动同步并刷新您的 YouTube Music 个人歌单与喜欢列表。

### 方式二：局域网 Cookie 导入（备选）
1. 确保手机与车机处于同一 Wi-Fi（或手机连接车机 Wi-Fi 热点）；
2. 在车机【设置】中点击【局域网 Cookie 导入】；
3. 车机提示访问地址（例如 `http://192.168.43.1:8888`）；
4. 手机打开该网址，粘贴您在浏览器版 YouTube Music 获取的 Cookie 文本提交即可。

---

## 🔄 协议失效时的“2 分钟极速自愈”指南

当 YouTube 官方更新反爬协议导致歌曲无法加载播放时：
1. 访问 [NewPipeExtractor Releases](https://github.com/TeamNewPipe/NewPipeExtractor/releases) 查看最新发布的版本号（例如 `v0.26.5`）；
2. 打开本项目根目录下的 `build.gradle`：
   ```groovy
   ext {
       newPipeExtractorVersion = "v0.26.5" // 修改为最新版本
   }
   ```
3. 在 GitHub 网页点击 **Commit changes**；
4. GitHub Actions 会自动触发重新打包，3 分钟后在 Releases 或 Actions Artifacts 处即可下载最新可用版 APK！

---

## 📂 项目结构

```
CarYTM/
├── .github/workflows/build.yml     # GitHub Actions 自动化 CI/CD 打包流水线
├── app/
│   ├── build.gradle                # 依赖项配置 (Conscrypt, ExoPlayer, NewPipe, Glide)
│   ├── src/main/
│   │   ├── AndroidManifest.xml     # 横屏车载配置、方控广播接收器、前台服务声明
│   │   ├── java/com/carytm/music/
│   │   │   ├── CarYtmApp.kt        # 应用入口与 Conscrypt TLS 1.3 优先注入
│   │   │   ├── auth/               # Google TV 设备码授权与 NanoHTTPD Cookie 局域网同步
│   │   │   ├── extractor/          # NewPipeExtractor 协议集成与音频流解析
│   │   │   ├── model/              # 数据模型 (SongItem, PlaylistItem, AuthModels)
│   │   │   ├── net/                # Conscrypt TLS OkHttp 客户端与 Innertube 接口封装
│   │   │   ├── player/             # ExoPlayer、OfflineRepository、音频焦点与后台播放服务
│   │   │   ├── ui/                 # 横屏 Rail 导航、离线曲库详情、播放器全屏界面、设置中心
│   │   │   └── util/               # LocaleHelper (中英多语言动态切换辅助类)
│   │   └── res/                    # 车载高对比度布局、主题样式、values (en)、values-zh、values-en
│   └── proguard-rules.pro          # Conscrypt、ExoPlayer、NewPipe 混淆防劣化规则
└── build.gradle                    # 顶层构建文件，统一集中管理 Extractor 协议引擎版本
```

---

## 📄 开源许可证
本项目采用 [GPL-3.0 License](LICENSE) 开源协议。
