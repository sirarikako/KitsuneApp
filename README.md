# KitsuneApp

本地优先的 Android 个人生活记录工具。采用 Kotlin、Jetpack Compose、Material 3 与 Room，最低 Android 8.0／API 26，界面以简体中文为主。

## 当前状态

2026-11-17：按设计完成阶段 1 的工程与用餐实现。记账、文件导入导出及备份尚未实现，应用内会明确显示后续阶段与未配置状态。

| 项目 | 实现状态 |
| --- | --- |
| Android 工程与 Gradle Wrapper | 已建立，依赖版本集中在 `gradle/libs.versions.toml` |
| 统一外观 | 明暗三模式、五个预设、HEX／HSV 自选、Tonal Spot、动态取色及旧系统回退；预览／应用／取消 |
| 狐狸图标 | 原创透明 PNG 前景、独立背景、Android 13 单色层；见 [资产说明](artwork/README.md) |
| 用餐 | 新增／编辑／删除、必选计划、条件必选方式、多选与临时新增分类、多行文本 |
| 查询与复用 | 日历定位、历史关键词与餐次／方式／分类筛选、复制上一餐内容、月度统计 |
| 方式与制作分类设置 | 新增、改名、排序、停用／启用；保留历史引用 |
| 本地数据 | Room v1、唯一索引、外键、统一事务入口与本地修订号；不使用破坏性迁移 |
| 记账 | 阶段 2，未实现 |
| JSON／CSV、目录备份与恢复 | 阶段 3，未实现 |
| GitHub 备份、正式签名 APK | 阶段 4，未实现 |

日常用餐记录不需要网络或账号。当前数据只保存在应用数据库，尚不能导出或换机恢复；卸载会删除记录。不要将此阶段的调试包作为完整正式版本。

## 文档入口

- [AGENTS.md](AGENTS.md)：协作规则与阅读顺序。
- [docs/PLAN.md](docs/PLAN.md)：完整需求、阶段和验收条件。
- [docs/DATA_FORMAT.md](docs/DATA_FORMAT.md)：v1 备份／CSV 设计契约，以及当前数据库实现边界。
- [docs/VERIFICATION.md](docs/VERIFICATION.md)：实际检查结果和未验证项。

## 工具链

| 项目 | 锁定／实测版本 |
| --- | --- |
| 构建 JDK | 完整 JDK 21；本次使用 Eclipse Temurin 21.0.9+10（仅 JRE 不足以构建） |
| Java／Kotlin 字节码 | 17 |
| Gradle | 8.13，Wrapper 校验发行包 SHA-256 |
| Android Gradle Plugin | 8.10.1 |
| Kotlin／Compose 编译插件 | 2.1.21 |
| KSP | 2.1.21-2.0.1 |
| Compose BOM | 2025.05.01 |
| Material Components／Color Utilities | 1.12.0 |
| Room | 2.7.1 |
| Lifecycle／Activity | 2.9.0／1.10.1 |
| compileSdk／targetSdk／minSdk | 35／35／26 |
| Android Build Tools | 35.0.0 |
| 应用 ID | `com.sirarikako.kitsune` |
| versionName／versionCode | 0.1.0／1 |

## 构建与安装

1. 安装 Android Studio 或完整 JDK 21、Android SDK Platform 35、Build Tools 35.0.0、Platform Tools，按 SDK 工具提示接受许可。
2. 在 Android Studio 打开仓库根目录，等待 Gradle 同步；或配置 `JAVA_HOME` 和 `ANDROID_HOME` 后使用命令行。也可在未跟踪的 `local.properties` 中填写 `sdk.dir=你的SDK路径`。
3. 所有依赖需在首次构建时下载；日常应用使用不依赖网络。

Linux／macOS：

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebugAndroidTest

# 以下需要已连接、解锁的 API 26+ 模拟器或设备
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:installDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:installDebug
```

调试包：`app/build/outputs/apk/debug/app-debug.apk`。也可运行：

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

调试包由本机自动生成的 debug 密钥签名，不是正式发布签名。同一应用 ID 且相同签名的升级安装通常保留数据库；不同机器的 debug 签名可能不同。不要通过卸载来解决签名冲突后误认为数据仍会保留。正式签名和长期升级方案在阶段 4 提供，私钥不入库。

CI 位于 `.github/workflows/android.yml`，包含构建、JVM 单元测试、lint、测试 APK 编译，以及 API 35 模拟器设备测试。CI 上传调试 APK 与验证报告。配置存在不代表远端 CI 已执行，实际结果见验证记录。

## 实现结构

```text
app/src/main/java/com/sirarikako/kitsune/
  core/data/         Room 实体、DAO、Repository、用餐约束与统计
  core/theme/        Material Tonal Spot、动态取色、主题偏好
  feature/meals/     记录表单、日历／历史筛选、月度统计
  feature/settings/  词表管理与外观预览
  ui/                ViewModel、四个导航入口与共用组件
app/schemas/         已导出的 Room v1 schema
```

UI → ViewModel → Repository → Room。数据库事务是保存成功的边界，新分类和用餐关联一起提交；冲突不覆盖已有记录。名称引用基于 UUID，规范化索引采用 NFC、trim 和 Locale.ROOT 小写；自由文本不裁剪。

Material Color Utilities 通过单一适配函数使用固定版本；该库对工具类标有库内 API 限定，适配函数局部说明并处理此限定。升级该依赖时须复核 API 与种子色对比度测试。

所有业务变更经统一入口递增修订号。阶段 1 没有可用的备份目标，不积累虚构的历史快照任务；阶段 3 引入目标授权及同事务不可变 outbox 后，才能承诺每次更新对应备份。未来新增模块应通过显式 Room 迁移扩展，不能使用 `fallbackToDestructiveMigration`。

## 实施顺序

- [x] 计划、协作约定、README 与数据格式设计。
- [x] 阶段 1 代码：工程、主题、狐狸图标、用餐与分类管理。
- [ ] 阶段 1 完整设备验收：所有明暗／动态取色、图标遮罩、大字体与键盘组合。
- [ ] 阶段 2：多钱包、计算器、收支、转账、退款与统计。
- [ ] 阶段 3：JSON／CSV、自动目录备份、恢复与 30 版本保留。
- [ ] 阶段 4：GitHub 私有库、异常与升级验证、正式签名 APK。

## 常见问题

- **找不到 SDK**：通过 Android Studio 安装上述组件，并配置未跟踪的 `local.properties` 或 SDK 环境变量。
- **找不到 Java 编译器**：确认使用完整 JDK 21，`java -version` 与 `javac -version` 都应可用。
- **动态取色不可选**：需要 Android 12／API 31+；旧设备使用保留的预设。
- **同一天同一餐保存冲突**：返回已有记录编辑，不能新增覆盖。
- **关闭方式的分类开关后仍有历史分类**：这是设计行为；编辑备注保留历史分类，主动清空或切换到不显示分类的方式才清除。
- **备份显示未配置**：当前版本尚未提供备份，不会把本地保存误报为云端成功。
