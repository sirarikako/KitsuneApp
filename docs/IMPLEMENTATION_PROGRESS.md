# Android 开发进度

基线为 GitHub `main` 的 `9e0dc53`，开发分支为 `feat/android-full-implementation`。功能规则以 `docs/features/` 为准，数据契约以 `docs/data/` 为准。

| 阶段 | 状态 | Git 检查点 |
| --- | --- | --- |
| 1 工程、共享界面、Room v1、用餐与外观 | 已实现，设备验收待补齐 | `feat(android): initialize Compose app, Room and meals` |
| 2 钱包、专属分类、记账、退款与统计 | 开发中 | 待提交 |
| 3 JSON、CSV、目录备份与恢复 | 待验证 | 待提交 |
| 4 GitHub 备份和交付验收 | 待实现 | 待提交 |

## 阶段 1

- 审查覆盖用餐必填、日期餐次唯一性、分类关联和历史保留、主题修订、归属校验、系统数据备份排除。关联分类及记录使用同一事务。Room 首发 schema 为 v1，仓库没有已发布的历史 Room schema。
- 采用 JDK 21、Gradle Wrapper、SDK 35；云端原 Java 缺少编译器，已安装完整 JDK。Debug 构建、2 项 JVM 测试、lint 和仪器测试 APK 构建通过。软件模拟器的系统引导尚未完成时 ADB 安装失败，设备用例待运行。Robolectric 运行时依赖被镜像 403 拒绝，Room 测试改用 Android 原生测试；JVM 测试验证字段字节数、日期、时间和规范化名称。
- 原生页面使用现行统一主题和通用容器，暂缺记录来源容器展开、主题过渡及逐屏视觉验收。文件授权和设备迁移组合未验证。
- 下一步为流水、钱包管理、精确计算和统计。设备测试继续使用 API 35 软件模拟器或 CI。
