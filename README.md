# 微信 AI 回复助手 V4

V4 新增 App 内服务器配置页，不再需要修改源码填写后端地址；支持可选访问口令。

核心流程：微信聊天页 → AI 悬浮球 → 手动粘贴或主动授权读取当前屏幕 → AI 生成建议 → 一键复制 → 用户自己发送。

## 当前状态
- Android 悬浮球：已实现
- 手动聊天文本生成：已实现
- 一次性屏幕捕获：已实现
- AI 后端示例：已包含在 backend/
- App 内 HTTPS 后端配置：已实现
- 自动发送微信：不做
- 后台读取微信数据库：不做

## 还差什么才能直接使用
需要先部署 backend/ 并配置 OPENAI_API_KEY，然后把 HTTPS 地址填进 App。源码可用 Android Studio 编译，也已包含 GitHub Actions 自动打包流程：上传到 GitHub 后打开 Actions，运行 “Build Android APK”，完成后下载 `WechatAI-V4-debug-apk`。
