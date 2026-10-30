<div align="center">

<img src="assets/brand/icon.png" width="88" alt="截题图标" />

# 截题 · 你的随身 AI 学习搭子 🌱

**把题目想明白，把思路留下来。**

在 Android 平板上，圈出卡住的一小步，获得循序渐进的提示；  
回到 Windows，把题目、对话和学习记录整理成可以再次翻看的复盘。

[下载体验](https://github.com/helh1723-lang/JieTi-/releases/latest) · [产品介绍 PDF](截题_产品介绍.pdf) · [使用与验证说明](docs/verification.md)

![版本](https://img.shields.io/github/v/release/helh1723-lang/JieTi-?color=819a87&style=flat-square)
![Android](https://img.shields.io/badge/Android-12%2B-89a98b?style=flat-square&logo=android&logoColor=white)
![License](https://img.shields.io/github/license/helh1723-lang/JieTi-?color=d8a7b1&style=flat-square)

</div>

---

## 🐣 截题能帮你做什么？

看课件、刷习题、读讲义时，有时并不需要一整页答案，只是卡在一句话、一个公式，或者不知道第一步该从哪里开始。

**截题把“随手问一小步”和“之后还能找回来”连在一起。** 当前围绕数学与信号系统学习，提供平板悬浮答疑、个人资料库、章节规划和 Windows 学习手记。

| 你正在做的事 | 截题可以陪你完成 |
| --- | --- |
| 在课件或讲义里遇到难题 | 用悬浮球框选题目，预览后提问，减少来回复制和切换 |
| 有一点思路，却不知道怎么继续 | 从轻提示开始，再选择检查步骤、继续提示或完整讲解 |
| 想问“刚才那一步为什么成立” | 围绕同一道题连续追问，保留原题与对话上下文 |
| 已经手写了一页解题过程 | 上传照片或相册图片，识别步骤并获取讲评 |
| 手头有自己的教材和笔记 | 导入资料，建立本地文字检索，沿引用回到原页核对 |
| 学过很多，复习时却找不到重点 | 按章节查看知识点、练习证据和复习安排，再导出学习复盘 |

## ✨ 为什么做成这样？

### 一点提示，也可以很有用

默认从最小必要提示开始，把独立思考的空间留给你。需要更多帮助时，再逐步展开；明确想看完整解析，也可以直接切换。

### 学习过程值得被留下

截图、追问、修改条件、手写作答和讲评不必散落在不同地方。学习记录可以编辑、删除，也能在电脑上整理成复盘材料，方便下次接着想。

### 用你自己的资料，建立自己的学习空间

支持 PDF、图片、DOCX、TXT 和 Markdown。文字资料可先进入本地全文检索；扫描件、公式和图表再逐页处理。引用保留来源与页码，方便回看，也方便发现并纠正识别错误。

### 用证据认识你，允许你随时纠正

截题会根据实际对话与作答证据更新偏好和掌握情况，一次错误不会直接变成永久的“薄弱标签”。记忆可以查看、纠正、暂停、锁定或遗忘，个性化始终保留你的控制权。

### 平板随手学，电脑慢慢整理

Android 负责悬浮截图、答疑和练习；Windows 负责浏览、编辑、同步与导出学习记录。两端通过同一局域网配对同步，适合“平板做题、电脑复盘”的学习习惯。

### 轻一点，也安静一点

暖米白、鼠尾草绿与轻盈的玻璃质感，让界面尽量不抢走题目的注意力。输入区按需展开，支持深色主题与减少动画；新版也针对课程和章节切换减少了重复计算。

## 🧩 一道题，可以这样学

1. **圈出来**：在平板打开“开始悬浮学习”，框选题目或卡住的区域。
2. **问一小步**：先要一个方向，再根据需要继续追问。
3. **自己试一试**：写下步骤，或拍照上传手写过程，获取讲评。
4. **留下线索**：保留本题的关键条件、卡点和对话，之后继续查看。
5. **隔天再练**：结合章节熟练度、周计划与每日三题，回头巩固。

> 每日三题从已导入资料或已配置的联网题源中挑选完整题目；来源不完整、题源不足时会显示缺口，不为凑数编造题目。

## 🌿 从一题走向一章

当前内置的课程结构包括：

- **数学**：高等数学、线性代数、概率论与数理统计，共 23 章、157 个知识点。
- **信号与系统**：数学基础及七章课程内容，共 52 个知识点。

你可以设置学习目标与每日时间，查看章节要点、知识点状态和复习安排。目录不等于已附带教材：应用不分发个人讲义，缺少正文的内容会标明待补充，需自行导入有权使用的资料。

[了解章节规划](docs/curriculum-0.2.1.md) · [了解每日三题与手写作答](docs/bugfixes-0.2.0.md)

## 🚀 开始和截题一起学习

### 🧭 版本与下载入口

| 平台 | 当前源码版本 | 安装与体验 |
| --- | --- | --- |
| Android 平板 | **0.2.3 · versionCode 13** | [下载公开 APK](https://github.com/helh1723-lang/JieTi-/releases/download/v0.2.3/Jieti-0.2.3-tablet.apk) |
| Windows 学习手记 | **0.2.1** | 按下方开发说明运行或打包；当前 Release 未提供 Windows 安装包 |

Android 与 Windows 分别维护版本号。公开版保留应用功能，使用通用导师配置；个人教学档案和本机设置不会随仓库发布。

🌱 **2026-10-30 已核对源码同步状态**：[查看版本与同步说明](docs/source-sync-2026-10-30.md)。

### Android 平板

1. 从 [Releases](https://github.com/helh1723-lang/JieTi-/releases/latest) 下载 Android APK，当前公开版本为 **0.2.3**，支持 **Android 12 及以上**。
2. 在应用设置中填写自己的 **AIPING API Key、接口地址和模型名**，保存并测试。默认接口地址为 `https://aiping.cn/api/v1`。
3. 选择支持图片输入、流式输出及工具调用的导师模型，其他角色可按需配置。
4. 点击“开始悬浮学习”，按系统提示授予悬浮显示和本次屏幕捕获权限，然后试着圈出第一道题。

公开 APK 是启用 R8 与资源压缩的非 Debug 构建，仍采用个人测试签名。已有用户升级时请核对签名并覆盖安装，保留原有数据。

### Windows 学习手记

Windows 源码当前为 **0.2.1**，可按下方开发说明运行或构建；v0.2.3 Release 未附带新的 Windows 安装包。

电脑与平板连接同一 Wi-Fi 或热点，在电脑开启配对，再用平板扫码或粘贴配对信息。同步后，可在电脑查看学习记录，并生成：

- **给 GPT 的学习复盘 Markdown**：整理题目、有效记忆、对话证据与复盘问题。
- **我的错题复盘 PDF**：保留原题截图、解析与练习空间，方便回顾。

## 🍵 使用前的小约定

- **需要自行配置模型服务**：应用不附赠 API Key；答疑、扫描件识别和部分后台处理会产生相应服务的调用费用。
- **资料优先保存在本地**：文字检索无需联网模型；在线答疑、图片识别与搜索会按使用功能将相关内容发送到你配置的服务，应用并非全程离线。
- **同步是设备间同步**：目前使用同一局域网内的配对与同步，不是登录账号后自动云同步。
- **AI 也需要核对**：数学结论、OCR 转写和图解可能出错；请结合原题、讲义与自己的推导检查。识别不清的内容应补拍或人工修正。

公开版本使用通用教学配置，不预置个人学校、背景、历史学习记录或模型密钥。API Key 通过 Android Keystore 加密保存在本机，不进入同步包与复盘导出。

[隐私说明](docs/privacy.md) · [版本验证范围](docs/verification.md) · [流畅度优化说明](docs/performance-0.2.3.md)

<details>
<summary>🛠️ 给想自己搭建、研究或贡献代码的朋友</summary>

### 桌面开发

安装 Node.js 与 npm，在项目根目录运行：

```powershell
npm ci
npm run verify
npm run desktop
```

### Android 构建

准备 JDK 17 和 Android SDK，配置 `JAVA_HOME` 与 `ANDROID_HOME`，在 Windows 上运行：

```powershell
# 个人平板优化包：非 Debug、R8 与资源压缩
powershell -ExecutionPolicy Bypass -File scripts/build-android.ps1 -Tablet

# 开发调试包
powershell -ExecutionPolicy Bypass -File scripts/build-android.ps1
```

### Windows 打包

```powershell
npm run package
```

项目包含 Android 原生应用、Electron / React 学习手记及共享协议。数据库升级保留迁移，避免破坏性清库；提交代码时请勿包含个人资料、运行数据库、配对凭据、签名私钥或 API Key。

[架构与数据约定](docs/architecture.md) · [公开版本验证记录](docs/public-release-0.2.3.md)

欢迎通过 [Issues](https://github.com/helh1723-lang/JieTi-/issues) 分享使用场景、问题与建议。描述问题时，请先遮住题目之外的个人信息和密钥。

</details>

---

<div align="center">

### 🐾 小脚印收集站

🐈 谢谢你路过截题的小小学习角落，留下一枚猫猫脚印吧。🌷

[![README visits](https://hits.sh/github.com/helh1723-lang/JieTi-.svg?style=for-the-badge&label=visits&color=819a87)](https://hits.sh/github.com/helh1723-lang/JieTi-/)

<sub>从本计数器启用后累计 README 图片访问次数，非去重人数；缓存可能造成延迟。点击徽章可查看统计。</sub><br />
<sub>访问计数由 <a href="https://github.com/silentsoft/hits">Hits</a> 提供。</sub>

<br /><br />

🌱 愿每一次卡住，都能长出一点新的理解。

</div>
