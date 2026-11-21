# KitsuneApp

一个本地优先的 Android 个人生活记录工具，计划从用餐记录、多钱包记账和数据备份开始，逐步扩展功能。

旧版 0.1.0 实现及 PR #1 已因 UI 重新设计而放弃；PR #2 保留的狐狸图标资产继续用于新版。当前 Android 工程依据新版模块化规范开发。

## 当前状态

Android 工程、Room v1、首页、用餐、钱包、记账、分类管理和外观设置已接入。JSON、CSV 和文件夹备份已接入。云端备份开发与验证证据见 [开发进度](docs/IMPLEMENTATION_PROGRESS.md)。

## 保留的狐狸图标

- [artwork/fox-foreground.png](artwork/fox-foreground.png)：原创透明源图。
- [artwork/android/res/](artwork/android/res/)：彩色自适应图标、独立背景与 Android 13+ 单色层。
- [artwork/README.md](artwork/README.md)：资产来源、原始提示词、接入步骤及验证边界。
- [artwork/SHA256SUMS](artwork/SHA256SUMS)：图标文件摘要。

图标保留验证仅覆盖文件一致性、PNG 透明度、XML 语法及资源引用；新版已接入，实际启动器显示尚待验收。网页预览仍使用狐狸 emoji 示意，Android 复用上述资产。

## 文档入口

| 文档 | 用途 |
| --- | --- |
| [AGENTS.md](AGENTS.md) | 开发前必读：文档入口、协作规则、验证要求 |
| [docs/README.md](docs/README.md) | 按功能与数据契约的设计入口、维护职责 |
| [docs/PLAN.md](docs/PLAN.md) | 产品范围、实施阶段和历史章节导航 |
| [docs/DATA_FORMAT.md](docs/DATA_FORMAT.md) | v1 数据契约总索引，详细规范位于 docs/data |
| [docs/ui/README.md](docs/ui/README.md) | UI 设计交接、现行文件索引与已验证／未实现边界 |

## UI 设计预览

直接用 Edge／Chrome 打开 [docs/ui/index.html](docs/ui/index.html)，无需联网即可查看当前唯一的统一视觉方案（默认杏橙配色）。茶绿等预设、自选主色及系统壁纸取色均为同一套组件的配色选项，不是独立视觉方案。可编辑源稿及演示边界见 [UI 设计说明](docs/ui/README.md)。

更新源稿后，在项目根目录重新生成独立预览：

```powershell
python docs/ui/build_preview.py
```

此命令仅依赖 Python 标准库，从 `docs/ui/src/` 拼接生成兼容源稿、离线网页预览与校验清单。已使用 Python 3.12.6 验证（2026-11-21），生成文件的 SHA-256 一致性检查通过；不生成 Android 应用或 APK。

建议交接指令：

> 请阅读 AGENTS.md、README.md、docs/README.md，并按任务读取相应功能模块、数据契约及 UI 说明；对照现有预览实施，记录真实构建和测试结果。

## 第一版计划提供什么

### 用餐

- 按日期记录早餐、午餐、晚餐；同一天同一餐唯一。
- “是否计划在家吃”必选是／否；为是时必须单选一种用餐方式，为否时方式可不填。
- 用餐方式在设置中管理，默认下厨、外卖；每种方式可决定是否显示多选制作分类。
- 制作分类支持下拉选择和录入时新增，例如炒菜、预制菜。
- 菜品与备注使用多行文本，支持历史搜索、复制和月度统计。

### 记账

- 多钱包，每个钱包独立币种及初始余额；可标记为“独立核算”，默认不进入各项收支统计与流水筛选，仍可手动选择。
- 通用收入／支出分类，以及可按钱包分别配置的专属收入／支出分类；未配置时使用通用分类。支持计算器输入、钱包转账、部分／全部退款。
- 所有账目记录发生日期与时间（精确到分钟），默认当前日期时间，可手动补录与修改。
- 跨币种转账手填两侧金额，按币种分别统计，不做自动汇率或统一资产折算。

### 数据与外观

- 可阅读的完整 JSON 备份与恢复、标准 UTF-8 CSV 导入导出。
- 指定目录自动备份，每个数据集保留一份最新成功的完整自动备份。
- 可选 GitHub 私有仓库备份，应用内可主动恢复当前云端完整备份。
- 柔和卡片界面，预设调色板、自选主色、跟随系统明暗、支持的平台上动态取色。
- 原创暖橙色卡通狐狸头像图标，提供彩色自适应和单色主题版本。

## 技术方向

| 项目 | 已确定方向 |
| --- | --- |
| 平台 | Android 8.0／API 26 及以上，默认简体中文 |
| 语言与界面 | Kotlin、Jetpack Compose、Material 3 |
| 本地数据 | Room，UI → ViewModel → Repository → 本地数据库 |
| 后台任务 | WorkManager，文件目录通过系统授权 |
| 工程组织 | 按用餐、记账、设置分包，统一主题、数据库和备份服务 |
| 交付 | 可重复构建的工程、签名 APK、运行与升级说明 |

## 环境、构建与安装

- JDK 21，Gradle 8.11.1，AGP 8.9.2，Kotlin 2.1.21，Room 2.7.2；依赖定义集中在 `gradle/libs.versions.toml`。
- compileSdk／targetSdk 35，minSdk 26，Build Tools 35.0.0。
- 应用标识 `com.kitsuneapp`，版本 `1.0.0`（versionCode 1）。调试 APK 由 Android 调试密钥签名。

安装 JDK 和 Android SDK 后，在仓库根目录运行。Windows PowerShell 将 `./gradlew` 换为 `.\gradlew.bat`。

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
# 需要已启动的模拟器或已连接的真机
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:installDebug
```

调试 APK 生成在 `app/build/outputs/apk/debug/app-debug.apk`。测试和 lint 报告位于 `app/build/reports/`。CI 将构建产物和报告上传为 Actions artifacts。

## 数据使用方式

- **本地记录**：没有网络、没有配置 GitHub，也能记录和查询。
- **文件夹备份**可在设置中通过系统选择器授权目录。目录暂时不可用时可继续录入，恢复访问后备份最新数据。备份按 UTC 日期时间加序号命名，校验并发布后才替换上一份。
- **GitHub** 使用单独配置的私有仓库、分支和限定仓库权限的 Token。自动上传合并连续修改，后台写入至少间隔 30 分钟。需要时可点“立即备份”。发现远端备份有变化会暂停上传，用户可选择恢复云端、确认以本机数据覆盖，或取消并保持暂停。详见 [GitHub 备份规则](docs/features/09-backup.md#94-github-私有库)。
- **完整恢复**会在校验后整体替换业务数据。新装空库可直接恢复；已有用户数据时，先把保护文件保存到授权目录或通过系统选择器单次导出。已绑定 GitHub 时，恢复后由用户单独确认是否更新云端备份。
- **CSV**：用于批量记录和表格查看，导入前预览与校验；钱包、完整设置和全量迁移使用 JSON。
- **备份形式**：JSON 内容本身不加密；Token 不包含在其中。不要把个人备份作为源码提交。

实际文件规范、字段和样例见 [DATA_FORMAT.md](docs/DATA_FORMAT.md)。

## 实施顺序

- [x] 完整计划、协作约定、README 与数据格式设计。
- [x] 当前 UI 设计落盘：可编辑源稿、统一视觉方案、离线预览。废弃截图已移除。
- [x] 阶段 1：Android 工程、统一主题、狐狸图标、用餐与分类管理。
- [x] 阶段 2：钱包、计算器、收支、转账、退款与统计。
- [x] 阶段 3：JSON／CSV、最新完整数据的本地自动备份与恢复。
- [ ] 阶段 4：GitHub 私有库、异常处理、真机验证、签名 APK。

完整验收条件见 [跨模块验收计划](docs/testing/ACCEPTANCE.md)。完成阶段后更新此清单，并补充实际验证证据。

## 已做与未做的验证

文档验证覆盖文件编码与链接、需求一致性、JSON／CSV 示例的可解析性及样例数据关系。数据格式文档中的示例是虚构数据，不是从真实应用导出的结果。

早期 UI 原型已在离线浏览器中检查浅色、深色和窄屏布局及核心交互；历史备选视觉已废弃，不作为当前设计依据。最新全局组件标准化版本已经进行颜色公式和 JavaScript 语法检查、重新打包；尚未进行该版本的浏览器视觉回归。过期截图已删除，不作为开发依据。现行视觉源稿及验证边界见 [UI 设计说明](docs/ui/README.md)。

本次 Android 构建、测试及设备验证范围见 [开发进度](docs/IMPLEMENTATION_PROGRESS.md)。
