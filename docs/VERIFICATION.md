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

## 尚未完成的验收

- Android 12+ 动态取色、Android 13+ 主题图标、不同启动器圆形／圆角方形遮罩。
- 极端自选色的完整界面对比度、大字体、全部键盘／屏幕尺寸组合及辅助功能。
- 用户真机操作、实际重启、升级安装和发布签名。
- 记账、导入导出、备份和恢复均未实现，不存在这些模块的运行验证结果。
- GitHub Actions 需推送后以远端实际结果为准。
