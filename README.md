# CarYTM - 老款 Android 6 车机专属 YouTube Music 客户端

专为**老款车载中控大屏（Android 6.0 Marshmallow, API 23）**量身打造的开源 YouTube Music 原生客户端。针对老款车机**低分辨率横屏（1024×600 / 800×480）**、**系统 WebView 无法更新**、**内存紧张（1GB~2GB）**、**TLS 根证书过期**以及**YouTube 官方协议频繁改动**等严苛痛点进行了专项攻坚。

---

## 🚗 核心特色与攻坚技术方案

### 1. 彻底移除 WebView 依赖：免 WebView 账号登录
- **痛点**：Android 6 的内置 WebView 冻结在 Chrome 44~53 内核，且无法升级。访问 `accounts.google.com` 会被 Google 判定为不安全环境直接强行拦截（提示 *"This browser or app may not be secure"*）。
- **破局**：采用类似 **SmartTube / Google TV 的 Device Code Flow（设备码授权）**。
  - 打开 App，屏幕自动生成专属二维码与 8 位大写授权码；
  - 拿出手机微信或浏览器扫码直达 `https://www.google.com/device`，点击确认授权；
  - 车机自动完成 Token 轮询并同步您的私人歌单、收藏喜欢（Liked Songs）与播放历史，**车机全程不加载任何网页**！
  - **备用方案**：内置局域网 NanoHTTPD 服务器，手机连同 Wi-Fi/车机热点访问车机 IP（端口 8888）即可一键粘贴浏览器 Cookie 导入。

### 2. 补齐 TLS 1.3 与最新证书信任链
- **痛点**：Android 6 缺乏现代 TLS 1.3 支持，且内置根证书库早已过期，直接请求 Google/YouTube 节点频繁抛出 `SSLHandshakeException`。
- **方案**：网络底层集成 **Google Conscrypt (BoringSSL)** 引擎，优先注入系统 Security Provider，为 API 23 设备补全 TLS 1.3 协议栈及最新根证书。

### 3. 抗 YouTube 协议失效：解耦架构与随时重新打包
- **痛点**：YouTube 频繁改动视频/音频流签名解密算法（`s` 参数、`n` 限制算法），导致第三方客户端频繁报 403 Forbidden 瘫痪。
- **方案**：音频流提取全面接入全球维护最活跃的 **`TeamNewPipe/NewPipeExtractor`** 引擎（内置脱机 JS 解析器解密），并与 UI 完全解耦。
- **GitHub Actions 一键云端打包**：
  - 项目配置了自动化 CI/CD 流水线（`.github/workflows/build.yml`）；
  - 当 YouTube 协议发生变动时，您只需在 GitHub 网页端修改 `build.gradle` 中的 `newPipeExtractorVersion` 并提交 Commit，Actions 将在 **3 分钟内自动编译产出全新的 Release APK**，无需在本地配置繁杂的 Android SDK 环境！

### 4. 车规级横屏 UI 与低性能适配 (1024×600 / 800×480)
- **极度轻量**：弃用在老旧车载芯片（全志 T3/瑞芯微 RK3188/展讯）上极易卡顿、GC 频繁的 Jetpack Compose，采用纯原生 **XML Layout + ViewBinding + RecyclerView**，常驻运行内存控制在 **50MB~80MB**。
- **横向 Rail 导航**：左侧 130dp 固定导航栏（首页、歌单、搜索、设置），右侧双列大卡片流。
- **大触控靶区**：按键与列表项高度统一设定为 `>= 56dp`，行车颠簸时也能轻松点击。
- **全屏行车播放台**：左侧大封面 + 右侧巨型播放控制按键，贴心配备 **快退 10 秒 / 快进 10 秒** 实体按键。

### 5. 车载硬件深度融合
- **方向盘按键（方控）**：全面接入 `MediaSessionCompat` 与 `MediaButtonReceiver`，支持方向盘物理按键切歌、暂停/播放。
- **导航语音避让（Audio Ducking）**：精细化实现 `AudioManager.OnAudioFocusChangeListener`，当车载高德地图/百度地图播报语音时，音乐自动压低至 20%，播报结束后平滑恢复。
- **本地弱网缓存**：基于 ExoPlayer 搭建 500MB 本地磁盘 LRU 缓存，行驶在隧道、地下车库时音乐丝滑不中断。

---

## 🛠️ 编译与打包方式

### 方式一：GitHub 网页端全自动打包（推荐，无需本地开发环境）

1. 将本项目 Fork 或上传到您的 GitHub 仓库；
2. 进入仓库的 **Actions** 标签页，在左侧选择 **Build CarYTM APK**；
3. 点击 **Run workflow** 按钮；
4. 等待约 2~3 分钟构建完成，点击进入构建记录，在 **Artifacts** 处即可直接下载 `CarYTM-debug-apk`；
5. 将 APK 拷贝到 U 盘插入车机安装即可。

### 方式二：本地命令行构建

如果本地已配置 Android SDK 和 Java 17+：

```bash
# Windows
.\gradlew.bat assembleDebug

# Linux / macOS
chmod +x gradlew
./gradlew assembleDebug
```
构建产物输出于：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 🔄 协议失效时的“2 分钟极速自愈”指南

当 YouTube 官方更新协议导致歌曲无法加载播放时：

1. 打开浏览器访问 [NewPipeExtractor Releases](https://github.com/TeamNewPipe/NewPipeExtractor/releases)，查看最新发布的版本号（例如 `v0.24.5`）；
2. 打开本项目根目录下的 `build.gradle`：
   ```groovy
   ext {
       // 将此处的版本号修改为最新发布的版本
       newPipeExtractorVersion = "v0.24.5"
   }
   ```
3. 在 GitHub 网页端点击 **Commit changes**；
4. GitHub Actions 会自动触发重新打包，3 分钟后即可下载包含最新解密协议的 APK 安装包！

---

## 📱 账号登录指南

### 方式一：手机扫码 / TV 设备码（首选）
1. 在车机打开 CarYTM，进入【歌单】或【设置】页面，点击【手机扫码 / TV 授权登录】；
2. 车机屏幕会展示一个专属二维码及 8 位大写字母代码（例如 `ABCD-EFGH`）；
3. 拿起手机扫码，或在手机浏览器中打开 `https://www.google.com/device`；
4. 在手机网页中输入车机屏幕上的 8 位代码，选择您的 Google 账号点击授权；
5. 车机端将在 3~5 秒内自动检测到授权成功，并立即刷新出您的 YouTube Music 个人收藏歌单与喜欢列表。

### 方式二：局域网 Cookie 导入（备用）
1. 确保手机与车机处于同一 Wi-Fi（或手机连接车机发出的 Wi-Fi 热点）；
2. 在车机【设置】中点击【局域网 Cookie 导入】；
3. 车机会弹出提示，例如：`请在手机浏览器访问 http://192.168.43.1:8888`；
4. 手机打开该网址，在输入框粘贴您在电脑/手机网页版 YouTube Music 获取的 Cookie 文本，点击提交即可。

---

## 📂 项目结构概览

```
CarYTM/
├── .github/workflows/build.yml     # 自动化 CI/CD 构建脚本
├── app/
│   ├── build.gradle                # 包含 Conscrypt / ExoPlayer / NewPipe 等依赖配置
│   ├── src/main/
│   │   ├── AndroidManifest.xml     # 横屏模式、方控按键监听广播声明
│   │   ├── java/com/carytm/music/
│   │   │   ├── CarYtmApp.kt        # Application 初始化 (TLS 1.3 / Extractor / 缓存)
│   │   │   ├── auth/               # Google TV 设备码认证 & 局域网 Cookie 导入服务
│   │   │   ├── extractor/          # NewPipeExtractor 音轨与签名解密
│   │   │   ├── net/                # Conscrypt TLS 1.3 引擎 & Innertube API
│   │   │   ├── player/             # ExoPlayer、方控 MediaSessionCompat、导航压音
│   │   │   └── ui/                 # 横屏专属 Rail 导航、全屏播放台、歌单网格
│   │   └── res/                    # 800x480 & 1024x600 布局、高对比度深色车载主题
└── build.gradle                    # 顶层构建文件与 NewPipeExtractor 集中版本控制
```
