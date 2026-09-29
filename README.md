# 📱 AI 智阅小说 (NovelAI Assistant)

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-purple.svg?style=flat-square" alt="Language" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-blue.svg?style=flat-square" alt="UI" />
  <img src="https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean%20Architecture-orange.svg?style=flat-square" alt="Architecture" />
  <img src="https://img.shields.io/badge/BYOK-Privacy%20First-red.svg?style=flat-square" alt="BYOK" />
  <img src="https://img.shields.io/badge/Latest%20Version-v0.8.0-success.svg?style=flat-square" alt="Version" />
</p>

---

## 📖 项目简介

**AI 智阅小说（NovelAI Assistant）** 是一款面向数字阅读爱好者的现代全格式电子书阅读器，深度融合了**上下文长文本 AI 伴读助手（RAG/Agent）**与**沉浸式 AI 连续续写及平行世界分支推演**能力。

应用坚持 **BYOK (Bring Your Own Key)** 原则，作为纯本地客户端运行，无需第三方中继服务器。用户自主填入大模型 API Key（兼容所有 OpenAI 规范接口），敏感数据使用 **Android Keystore 加密存储于本地**，杜绝数据外泄与账号跑路风险。

整体视觉遵循 **iOS Human Interface Guidelines (HIG)** 设计美学，融合 Squircle 大圆角矩形、实时高斯模糊毛玻璃（Frosted Glass）、弹性物理动效与深度护眼的全深色模式。

---

## ✨ 核心特性矩阵

### 1. 📚 沉浸式阅读引擎 (Reader Engine)
- **多格式导入支持**：无缝支持本地 `.txt`、`.epub`、`.md` 等电子书解析与阅读。
- **双阅读排版模式**：
  - **仿真左右翻页（Paged，默认）**：重写高精度换行与分页算法，杜绝多段落死锁排版故障；支持屏幕左右点击翻页、跨章平滑过渡与首末页手势阻尼。
  - **无缝上下滚动（Scroll）**：平滑垂直连续长卷阅览。
- **高自由度排版定制**：亮度、字号（A-/A+）、行距、边距、段距自由微调；内置衬线体（Serif）与无衬线体一键切换。
- **5 套经典阅读主题**：白天纯白、羊皮纸米黄、豆沙绿护眼、暗夜深蓝、OLED 极暗纯黑。
- **目录抽屉与长按章节管理**：
  - 智能正则提取目录与原生 EPUB 目录映射；
  - 支持 **[全部] / [仅原作] / [AI衍生] / [分支树]** 四维目录筛选；
  - 目录内**长按任意章节**呼出操作菜单，支持章节**重命名**与**安全永久删除**。

### 2. 🎧 智能语音听书 (TTS Companion)
- **开箱即用**：原生 Android TTS 语音合成驱动，零延迟即点即播。
- **智能选段与断点续播**：
  - 支持类似番茄小说的**从当前屏幕所在页首段直接起播**；
  - 正文长按任意文字片段呼出浮层，支持**「从此处朗读」**；
  - 听书朗读期间对应段落实时以**柔和紫色微光高亮**跟随，自动翻页连播。
- **听书控制胶囊**：底部常驻播放/暂停切换、停止按钮与 **0.75x ~ 2.0x 无级语速调节**。

### 3. 🤖 AI 深度伴读与划线即问 (RAG & Chat)
- **划线即问浮动胶囊**：阅读过程中长按任意文字片段，即时呼出操作胶囊：
  - **「解析深意」**：剖析选段伏笔、行文手法与隐喻；
  - **「情绪总结」**：解析段落情感基调与人物内心；
  - **「AI 吐槽」**：以幽默辛辣口吻即兴吐槽。
- **独立 AI 伴读助手主页**：
  - 支持**情节回忆**（回忆前文线索）、**人物图谱分析**（人物关系与生平）与**剧情深读**；
  - **原生文本自由选择**：采用原生 `SelectionContainer`，长按自由框选与复制，杜绝粗暴自动复制整段；
  - **即时交互视觉反馈**：发送后展现思考动画气泡、输入框顶部线性进度条与流式输出动态光标；
  - 提供**清空聊天记录**功能（含二次确认对话框）。

### 4. ⚡ AI 连续续写与平行世界分支 (Continuation & Branching)
- **全书最新章追踪**：续写默认开启「⚡ 全书最新章」直接追踪模式，无需在长篇目录中反复寻找末尾章节；亦可自由切换指定章节。
- **连续续写模式**：新章生成后支持一键「保存并续写下一章」，自动将新章作为下一轮创作前驱，顶部配有创作链路进度条。
- **因果世界线追溯算法（`getChapterLineage`）**：分支推演严格向上溯源至该分支归属的历史世界线祖先节点，剔除无关主线噪音，保证设定连贯。
- **剧情分支树（Branch Tree）**：树状可视化展现主线与平行宇宙衍生分支，节点支持一键「续写此分支」或「创建新分支」。
- **章节身份标识**：`[原]` 原作章节、`[AI]` AI 续写章节、`[分]` 平行分支章节。

### 5. 🔑 BYOK 多模型中心与任务分流 (Model Routing)
- **多模型厂商模版预设**：OpenAI、Anthropic Claude、DeepSeek、Moonshot (Kimi)、SiliconFlow (硅基流动)、Ollama (本地私有部署) 等。
- **通用 OpenAI 协议兼容**：支持自定义 Base URL、API Key 与 Model Name，兼容各类聚合分发与中转平台。
- **真实生效的多场景分流**：
  - 为**续写场景**指定长文本拟合能力强的高阶模型（如 Claude 3.5 Sonnet / DeepSeek-V2.5）；
  - 为**速览伴读场景**指定响应极速的轻量模型（如 GPT-4o-mini / DeepSeek-Lite）。
- **实用配置支持**：横向平滑滚动服务商卡片、一键设为默认、连通性 Ping 测试、自动获取模型列表、顶部常驻使用文档指南、清空用量统计防误触二次确认。

### 6. 🎨 深度全深色模式与主题适配 (Dark Mode)
- **完善的 Material 3 暗色色彩规范**：补全完整 `surfaceContainer*` 容器色系，消除弹出浅色底板的晃眼隐患。
- **双重深色模式感知**：无论是**系统开启深色模式**还是**阅读器选择暗夜/极暗背景**，弹出的阅读设置面板、长按章节菜单、重命名/删除弹窗、划线选段操作条均自动无缝适配为舒适暗黑质感。

---

## 🛠️ 技术架构与选型

| 层次 | 技术选型 | 说明 |
| :--- | :--- | :--- |
| **语言** | Kotlin 100% | 现代 Android 官方首选语言，协程与 Flow 异步流支持 |
| **UI 框架** | Jetpack Compose + Material 3 | 声明式组件化，搭配自定义 Shape、Blur 实现 iOS 风格质感 |
| **架构模式** | MVVM / MVI + Clean Architecture | 表现层、业务逻辑层与数据持久层清晰解耦 |
| **依赖注入** | Hilt (Dagger) | 规范化依赖注入与生命周期绑定 |
| **本地数据库** | Room DB + Paging 3 | 书籍、章节目录、分支树、阅读进度与对话记录本地持久化 |
| **敏感加密存储** | Android Keystore | 安全加密存储各模型厂商 API Key，本地私密绝不外泄 |
| **用户偏好** | Jetpack DataStore Preferences | 存储阅读器排版、字号、语速与主题模式 |
| **毛玻璃与视觉** | Haze (dev.chrisbanes.haze) | 实时高性能高斯模糊毛玻璃背景穿透效果 |
| **网络通信** | OkHttp + SSE (Server-Sent Events) | 通用 OpenAI 兼容协议流式打字机交互响应 |

---

## 📂 项目结构概览

```text
app/src/main/java/com/novelai/assistant/
├── data/
│   ├── api/            # OpenAI 兼容协议 SSE 流式客户端与模型拉取
│   ├── db/             # Room 数据库实体 (Book, Chapter, AiChat) 与 DAO
│   ├── importer/       # TXT / EPUB / MD 本地文件解析器
│   ├── prefs/          # DataStore 阅读排版配置与主题持久化
│   ├── repository/     # 统一数据仓储层 (Book, Api, ReadingPreferences)
│   └── tts/            # Android 原生 TTS 听书播放管理器
├── domain/             # 业务用例与 Prompt 提示词工程构建器
├── ui/
│   ├── assistant/      # AI 伴读与解析主界面
│   ├── bookshelf/      # 书架、书单分类与书籍卡片
│   ├── components/     # 大标题、分段器、毛玻璃、Badge等公共组件
│   ├── continuation/   # AI 续写与世界线推演
│   ├── discover/       # 在线书源抓取 (番茄 / 菠萝包)
│   ├── novel/          # 应用路由宿主与全屏导航容器
│   ├── reader/         # 核心阅读器界面、分页引擎与设置面板
│   ├── settings/       # BYOK 模型中心、任务分流与提供商编辑
│   ├── theme/          # iOS HIG 调色板、排版、Shape 与全深色模式
│   └── tree/           # 平行世界剧情分支树可视化
└── MainActivity.kt     # 单 Activity 入口与 Edge-to-Edge 边到边沉浸式支持
```

---

## 🚀 快速上手与编译构建

### 环境要求
- **Android Studio**：Ladybug (2024.2.1) 或更高版本
- **JDK**：OpenJDK 17
- **Android SDK**：Compile SDK 34，Min SDK 26 (Android 8.0+)
- **Gradle**：8.7+

### 本地编译步骤
1. **克隆仓库**：
   ```bash
   git clone https://github.com/Zuo-yan/NovelAIAssistant.git
   cd NovelAIAssistant
   ```

2. **编译 Debug 版**：
   ```bash
   ./gradlew assembleDebug
   # 产物输出于：app/build/outputs/apk/debug/app-debug.apk
   ```

3. **编译 Release 版（安装包）**：
   ```bash
   ./gradlew assembleRelease
   # 产物输出于：app/build/outputs/apk/release/app-release.apk
   ```

4. **安装至连接的手机或安卓模拟器**：
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

---

## 📱 使用指南

1. **导入书籍**：
   - 首次进入主界面点击右上角「导入」按钮，选取本地 `.txt` 或 `.epub` 小说；
   - 系统将自动解析章节目录并加入书架。
2. **配置大模型 (BYOK)**：
   - 前往底部「设置」→「模型与 API」→ 点击「添加提供商」；
   - 填入您的 Base URL（如 `https://api.deepseek.com/v1`）与 API Key；
   - 点击右上角「📖 使用文档」可随时查阅各参数详细说明；
   - 在「任务分流」中为续写与伴读分别指定合适的模型。
3. **沉浸式阅读与听书**：
   - 点击书架书籍进入阅读器，点击屏幕左/右侧翻页，点击中间呼出菜单栏；
   - 点击底部「听书」即可从当前页首段自动语音朗读；
   - 长按任意文字片段呼出快捷浮层，一键「从此处朗读」或「解析深意」。
4. **AI 连续续写与平行分支**：
   - 点击底栏「AI 续写」自动定位最新章节，输入导向设定即可生成后续章节；
   - 生成后点击「保存并续写下一章」可无缝连续推演；
   - 点击顶部「分支树」可查看剧情分叉节点，随时推演全新世界线。

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源协议发布，欢迎 Fork、Star 与提交 PR！
