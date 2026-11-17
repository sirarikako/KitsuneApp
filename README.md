# KitsuneApp

2026-11-17：因 UI 已重新设计，本聊天开发的 0.1.0 应用版本已放弃。旧版实现 PR #1 已放弃；保留的狐狸图标单独提交，供新版 UI 使用。

## 当前保留内容

- [artwork/fox-foreground.png](artwork/fox-foreground.png)：原创透明狐狸源图。
- [artwork/android/res/](artwork/android/res/)：彩色自适应图标、独立背景和 Android 13+ 单色主题图标。
- [artwork/README.md](artwork/README.md)：资产来源、原始提示词、接入步骤和验证边界。
- [artwork/SHA256SUMS](artwork/SHA256SUMS)：图标文件摘要。

当前分支不包含旧版 Android 工程、UI、业务代码、测试或构建 CI，也没有可构建的应用版本。后续开发以重新设计的资料为准。

## 历史资料

[AGENTS.md](AGENTS.md)、[docs/PLAN.md](docs/PLAN.md) 和 [docs/DATA_FORMAT.md](docs/DATA_FORMAT.md) 来自最初的设计交接。业务与数据需求可供参考，原有 UI 结构和实现阶段不代表新 UI 方案已经确认；须结合最新设计使用。

图标保留验证仅涵盖文件内容一致、PNG 透明度、XML 语法与资源引用；未对已放弃的应用重新运行构建或业务测试。
