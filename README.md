# 微信 AI 回复助手 V4

手机端微信悬浮 AI 回复建议工具。

## 功能
- Android 悬浮球
- 手动粘贴聊天生成回复
- 用户主动授权的一次性屏幕捕获
- AI 后端示例（backend/）
- App 内配置 HTTPS 后端地址
- 一键复制建议，由用户自己确认发送
- 不读取微信数据库，不自动发送微信

## 云端 APK
仓库已配置 GitHub Actions。每次推送 main 分支会自动构建 debug APK，也可在 Actions 页面手动运行 Build Android APK。

## 使用前
AI 功能需要部署 backend/，在服务端配置 OPENAI_API_KEY，再把 HTTPS 地址填进 App。
