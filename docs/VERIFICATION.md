# 阶段 1 验证记录

日期：2026-11-17。验证对象：KitsuneApp 0.1.0，阶段 1。

## 本地构建环境

Linux x86_64，完整 Eclipse Temurin JDK 21.0.9+10，SDK Platform 35、Build Tools 35.0.0。完整工具链版本及通用命令以 README 为准。云环境最初仅有 JRE，已在工作区安装完整 JDK 与 SDK；工作区工具缓存、本地 SDK 路径及调试密钥均不纳入源码。

## 实际结果

- `:app:assembleDebug`：通过，已生成调试 APK（0.1.0，minSdk 26，targetSdk 35）。
- `:app:testDebugUnitTest`：9 项通过，0 失败，0 跳过。
- `:app:lintDebug --rerun-tasks`：通过，0 错误、0 警告。
- `:app:assembleDebugAndroidTest`：通过，已生成测试 APK。
- `:app:connectedDebugAndroidTest`：API 26／Android 8.0 x86_64 软件模拟器上 6 项通过，0 失败，0 跳过。最终组合构建命令返回 BUILD SUCCESSFUL。
- `apksigner verify --verbose`：通过，使用 Android debug 密钥与 APK v2 签名；不是正式发布签名。
- XML、导出的 Room schema JSON、本地文档链接与 Git 空白检查：通过。

JVM 测试覆盖显式必选计划、条件必选方式、计划与实际方式独立、停用引用、历史分类、严格日期、名称规范化、按月统计、未填方式和上一餐选择。主题测试另覆盖 HEX 规范化，以及五种预设和黑、白、红、绿、蓝种子在浅／深模式下 160 组正文色角色的 WCAG 对比度（至少 4.5:1）；这不替代全界面视觉验收。

设备测试覆盖 Room 初始播种幂等、唯一索引与外键、分类＋记录同事务、冲突与修订溢出回滚、历史关联保留、删除级联、主题修订，以及 Compose 表单必填交互。

最终 APK SHA-256：

```text
8d99d963c9f58088baa2859864df5059d5757b9840c88246aaef49d9c81e9bf9
```

模拟器未使用硬件加速，启动中曾出现系统 UI 无响应与 ADB 属性读取超时；等待系统稳定后最终测试全部通过。此结果只证明上述 API 26 测试范围，不表示真机或新 Android 版本都已验收。

## Android 16 安装反馈与文件检查（2026-11-17）

- 用户设备：三星 Galaxy S25，Android 16。用户反馈聊天内点击“安装”没有弹出安装器；改从“三星我的文件 → 下载”打开原 APK 后，已成功安装并打开 KitsuneApp。这是用户实际设备的确认，非云端模拟结果；并不代表所有业务与外观功能已在该设备验收。
- APK 未重新构建或重新签名，SHA-256 与上文一致。检查确认包含 arm64-v8a 原生库、没有 maxSdkVersion／testOnly 限制；APK v2 签名校验通过。
- `zipalign -c -P 16 -v 4` 通过；arm64-v8a 与 x86_64 原生库的 ELF LOAD 段对齐均为 0x4000（16 KiB）。这些静态检查不能替代所有 16 KiB 真机运行测试。
- 云端 Android 16 软件模拟器未完成稳定启动，没有声称 Android 16 模拟器自动化通过。
- 首次 GitHub Actions 的 build 作业在 SDK 初始化失败（run 44043896388）：setup-android 默认请求已不存在的 `tools` 包。现改为显式安装 Platform Tools、当前 compileSdk 对应平台和 Build Tools，并将设备测试矩阵扩展为 API 35／36。修改后的远端结果仍以 GitHub Actions 实际运行记录为准。

## 尚未完成的验收

- Android 12+ 动态取色、Android 13+ 主题图标、不同启动器圆形／圆角方形遮罩。
- 极端自选色的完整界面对比度、大字体、全部键盘／屏幕尺寸组合及辅助功能。
- 用户真机操作、实际重启、升级安装和发布签名。
- 记账、导入导出、备份和恢复均未实现，不存在这些模块的运行验证结果。
- GitHub Actions 需推送后以远端实际结果为准。
