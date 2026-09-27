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
* **LAN Cookie Import**: Built-in lightweight HTTP server (`NanoHTTPD`). Connect your phone to the car's Wi-Fi hotspot and access `http://<car-ip>:8888` to paste and sync browser cookies instantly.

### 2. TLS 1.3 & Modern Cryptography on Android 6 (BoringSSL)
* Ships with **Google Conscrypt (BoringSSL)** injected into the Security Provider on app launch.
* Fully backports **TLS 1.3** and modern trusted root certificates to Android 6.0 devices, eliminating all SSL handshake and cipher suite failures when communicating with Google endpoints.

### 3. Decoupled Protocol Engine & "2-Minute Self-Healing" via GitHub Actions
* Powered by **`TeamNewPipe/NewPipeExtractor` (v0.26.5+)** with headless JS stream deciphering (handling `s` and `n` parameter transformations).
* **Automated CI/CD Hot-Fix Workflow**: Whenever YouTube changes its backend encryption algorithms, you do not need an Android development environment on your PC. Simply change the version tag in `build.gradle` on the GitHub website, and GitHub Actions automatically compiles and releases a new APK within 3 minutes.

### 4. Dedicated "Offline Music" Library & 100% Zero-Network Playback
* **Permanent Video ID Cache Key**: Binds ExoPlayer's `customCacheKey` to the unique `videoId`.
* **Pure Offline Decoding**: Fully cached songs can be played in underground parking garages or remote areas without internet or SIM cards. The player decodes directly from local disk (`SimpleCache`) without making any network requests.
* **Dedicated Offline Section**: A prominent **"Offline Music" (本地离线音乐)** card in the Library tab displays exact count of 100% cached songs, with direct track selection, "Play All", and "Shuffle" controls.

### 5. Full-Track Background Preloading & Instant Switching
* **Full-Song Pre-Caching**: While the current song is playing, a background coroutine pre-resolves the next track's URL and caches the **entire audio file** to local disk using ExoPlayer's `CacheWriter`.
* **Deterministic Shuffle Candidate Preload**: In shuffle mode, the next random candidate is pre-selected and cached in advance so that skipping to the next track hits the local cache with **zero buffering lag**.
* **Instant Audio Cutoff**: Skipping songs stops the previous audio immediately and shows buffering feedback, eliminating overlapping sounds.

### 6. Two-Tier Smart Cache Eviction & Custom Storage Limit
* **User-Configurable Storage**: Choose between `200 MB`, `500 MB (Default)`, `1 GB`, `2 GB`, or `5 GB` directly from Settings.
* **Two-Tier Eviction Algorithm (`trimCacheIfNeeded`)**:
  - **Tier 1 (Fragment Purge)**: When storage limit is exceeded, incomplete/interrupted tracks (`isFullyCached == false`) are deleted first.
  - **Tier 2 (LRU Protection)**: If space is still needed, the oldest played complete tracks are evicted by Least Recently Used (LRU) timestamp. Recently and currently playing tracks are protected.
* **Incomplete Song Isolation**: Incomplete audio fragments are strictly excluded from the Offline list to prevent mid-song playback interruptions.

### 7. Thread-Safe Automotive Audio Focus & Navigation Ducking
* **Main Thread Dispatch**: Audio focus changes are strictly dispatched to `Handler(Looper.getMainLooper())`, preventing `IllegalStateException: Player is accessed on the wrong thread` crashes on modern Android 12–14 and automotive ROMs.
* **Navigation Voice Ducking (0.2f)**: When navigation apps (Amap, Baidu Maps, Google Maps) speak, CarYTM smoothly lowers music volume to 20% and restores to 100% when finished without stopping playback.
* **Transient Call/Voice Interruption**: Automatically pauses on phone calls or voice messages, remembering state (`resumeOnFocusGain = true`), and automatically resumes playback when the call ends.
* **Explicit Play/Pause State**: Prevents accidental playback inversion during background focus changes.

### 8. Automotive Ergonomic UI & Hardware Integration
* **Lightweight Native UI**: Pure **XML Layout + ViewBinding + RecyclerView** with memory footprint capped at **50 MB – 80 MB**, avoiding Jetpack Compose GC overhead on weak automotive chips.
* **Landscape Ergonomics**: 130dp left rail navigation with dual-column touch cards designed for `1024×600` and `800×480` displays. Touch targets are `>= 48–56dp` for safe driving operation.
* **Steering Wheel Controls**: Full `MediaSessionCompat` and `MediaButtonReceiver` integration for steering wheel track skipping and play/pause buttons.
* **Hardware Back Button Handling**: Physical and steering wheel back buttons navigate smoothly between playlist track lists and playlist grids.

---

## 📱 Installation & Downloads

### Download Pre-built APKs
Download the latest APK release from the [GitHub Releases](https://github.com/jacywy/ytm-a6-hu/releases) page:
- **Latest Release**: [v0.2.2 - App Debug APK](https://github.com/jacywy/ytm-a6-hu/releases/download/v0.2.2/app-debug.apk)

### Install via ADB (USB or Wi-Fi)
```bash
adb install -r app-debug.apk
adb shell am start -n com.carytm.music/.ui.MainActivity
```

---

## 🔐 Login Instructions

### Method 1: Mobile QR Code / Google TV Code (Recommended)
1. In CarYTM, go to **Library** or **Settings**, tap **"手机扫码 / TV 登录"**.
2. A QR code and an 8-letter code (e.g. `ABCD-EFGH`) will appear.
3. Scan the QR code or navigate to `https://www.google.com/device` on your phone/PC browser.
4. Enter the 8-letter code and grant permission to your Google account.
5. CarYTM will sync your personalized YouTube Music playlists and Liked Songs within 3–5 seconds.

### Method 2: Local Wi-Fi Cookie Import (Fallback)
1. Ensure your phone is connected to the same Wi-Fi or car hotspot.
2. In CarYTM **Settings**, tap **"局域网 Cookie 导入"**.
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
│   │   │   └── ui/                 # Landscape Rail UI, Offline detail, PlayerActivity, Settings
│   │   └── res/                    # 800x480 & 1024x600 layouts, High-contrast dark car theme
└── build.gradle                    # Top-level build file with centralized extractor version
```

---

<br />

# 🚗 CarYTM - 老款 Android 6 车机专属 YouTube Music 客户端（中文）

专为**老款车载中控大屏（Android 6.0 Marshmallow, API 23+）**量身打造的开源 YouTube Music 原生客户端。针对老款车机**低分辨率横屏（1024×600 / 800×480）**、**系统内置 WebView 无法升级**、**运行内存极度紧张（1GB~2GB）**、**TLS 根证书过期**以及**YouTube 官方协议频繁改动**等严苛痛点进行了专项工程攻坚。

---

## 🌟 核心特色与技术攻坚

### 1. 彻底移除 WebView 依赖：免 WebView 账号登录
* **痛点**：Android 6 的内置 WebView 冻结在 Chrome 44~53 内核且无法升级。访问 Google 登录页会被安全策略强行拦截（提示 *"This browser or app may not be secure"*）。
* **方案**：采用类似 **SmartTube / Google TV 的 Device Code Flow（设备码授权）**：
  1. 车机屏幕自动生成专属二维码与 8 位大写授权码；
  2. 手机扫码直达 `https://www.google.com/device`，确认授权；
  3. 车机后台自动完成 Token 轮询并同步个人歌单、喜欢收藏与历史记录，**车机全程不加载任何网页**！
* **局域网 Cookie 导入备选**：内置轻量 NanoHTTPD 服务，手机连接车机 Wi-Fi/热点后访问车机 IP（端口 8888）即可一键粘贴 Cookie 导入。

### 2. 补齐 TLS 1.3 与现代化证书信任链
* 底层集成 **Google Conscrypt (BoringSSL)** 引擎，在 App 启动时优先注入系统 Security Provider。
* 为 Android 6.0 补全 **TLS 1.3** 协议栈及最新根证书库，彻底解决直接连接 Google/YouTube CDN 节点时的 `SSLHandshakeException`。

### 3. 抗 YouTube 协议变动：解耦架构与 2 分钟极速自愈
* 音频流解析全面接入全球维护最活跃的 **`TeamNewPipe/NewPipeExtractor`** 引擎（内置脱机 JS 虚拟机，解密 `s` 与 `n` 签名算法）。
* **自动化云端打包流水线**：配置 GitHub Actions（`.github/workflows/build.yml`）。当 YouTube 协议发生变动时，只需在 GitHub 网页修改 `build.gradle` 中的版本号并提交，Actions 会在 **3 分钟内自动编译产出全新的 Release APK**，无需本地配置 Android 开发环境。

### 4. 专属【本地离线音乐】曲库与 100% 脱网纯离线播放
* **唯一 VideoId 缓存键绑定**：将 ExoPlayer 的 `customCacheKey` 与歌曲 `videoId` 强绑定。
* **纯脱网本地解码**：完整下载/缓存的歌曲在进入长隧道、地下车库、偏远山区等**完全无信号、拔掉 SIM 卡**的环境下，播放器直接从磁盘 `SimpleCache` 寻址解码，**不请求 YouTube 接口，零网络消耗秒播**。
* **离线专属管理专区**：歌单顶部常驻 **【本地离线音乐】** 卡片，实时显示完整离线曲目数。点击展开离线详情，支持查看歌曲列表、选择任意歌曲起播，以及一键“全部播放”与“随机播放”。

### 5. 全曲后台预缓存与即刻切歌
* **全量流式预下载**：当前歌曲播放时，后台协程使用 `CacheWriter` 提前对下一首歌曲进行**全量音频流写入**。
* **随机播放模式预选锁定**：随机播放下提前预测并下载锁定的下一首随机歌曲，切歌时实现**零等待无缝起播**。
* **切歌即刻静音**：切换歌曲瞬间立即执行 `stop()` 并转入缓冲反馈，彻底消除上一首歌继续响导致的“没按成功”错觉。

### 6. 两级智能缓存淘汰机制与自定义容量
* **自定义缓存上限**：设置页面提供 `200 MB`、`500 MB (默认)`、`1 GB`、`2 GB`、`5 GB` 单选调节与实时占用显示。
* **智能两级淘汰策略 (`trimCacheIfNeeded`)**：
  1. **第一优先级（清理碎片）**：空间不足时，自动将因中途跳歌、网络中断导致的未完整缓存音频块（`isFullyCached == false`）**优先彻底删除**；
  2. **第二优先级（LRU 淘汰旧歌）**：碎片清理后若仍超限，才根据最近收听时间戳逐个淘汰最久未听的完整歌曲，**当前播放曲目与常听歌曲受到绝对保护**。
* **碎片隔离保护**：未完全缓存的半拉歌曲绝不进入离线列表，杜绝离线听歌到一半卡死报错。

### 7. 线程安全的车规级音频焦点管理（Audio Focus）
* **主线程 Looper 强制派发**：所有音频焦点事件强制切换回 `Handler(Looper.getMainLooper())` 执行，彻底解决跨线程访问播放器引发的 `IllegalStateException: Player is accessed on the wrong thread` 闪退。
* **导航语音平滑压音（Duck 0.2f）**：车载高德地图/百度地图语音播报时，音乐音量自动压低至 20%，播报完毕平滑恢复 100%，**行车听歌不中断**。
* **电话与语音打断自动恢复**：接听车载电话或临时语音消息时，音乐安全暂停并标记状态，通话结束后**自动恢复播放**。
* **明确的 `play()` / `pause()` 状态机**：杜绝因布尔值翻转错误在后台误起播。

### 8. 车载专属横屏交互与方控硬件融合
* **极度轻量低功耗**：摒弃在老款车载芯片（全志 T3/瑞芯微 RK3188）上卡顿严重的 Jetpack Compose，采用纯原生 **XML + ViewBinding + RecyclerView**，常驻运行内存仅 **50MB~80MB**。
* **横屏 Rail 导航**：左侧 130dp 固定导航 Rail，右侧双列大卡片布局。按键与条目高度全部设为 `>= 48–56dp`，颠簸路段不易误触。
* **方向盘按键（方控）**：全面接入 `MediaSessionCompat` 与 `MediaButtonReceiver`，支持方向盘物理按键上一首、下一首、暂停/播放。
* **物理返回键适配**：完美适配方向盘与中控返回键，从歌曲详情列表无缝回退至歌单网格。

---

## 📦 安装与下载

### 预编译 APK 下载
进入本仓库的 [Releases](https://github.com/jacywy/ytm-a6-hu/releases) 页面下载最新构建：
- **最新正式版本**：[v0.2.2 - app-debug.apk (21.49 MB)](https://github.com/jacywy/ytm-a6-hu/releases/download/v0.2.2/app-debug.apk)

### 命令行安装
```bash
adb install -r app-debug.apk
adb shell am start -n com.carytm.music/.ui.MainActivity
```

---

## 📱 账号登录指南

### 方式一：手机扫码 / TV 设备码（首选）
1. 在车机打开 CarYTM，进入【歌单】或【设置】页面，点击【手机扫码 / TV 授权登录】；
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

## 📄 开源许可证
本项目采用 [GPL-3.0 License](LICENSE) 开源协议。
