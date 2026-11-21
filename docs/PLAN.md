# KitsuneApp 开发计划与交接说明

> 旧版 0.1.0 实现及 PR #1 已因重新设计而放弃，狐狸图标资产由 PR #2 保留在 artwork。本文件已按本轮新版 UI 更新；后续开发以当前计划和 docs/ui 设计资料为准，不恢复旧版界面或实现。

更新日期：2026-11-21（Asia/Shanghai）<br>
状态：需求、交接文档与现行 UI 源稿及离线预览已保存，尚未初始化 Android 工程。

## 1. 如何使用本文件

本文件整合了本聊天中已经确认的完整方案及后续修改，供更换模型后直接交接开发。后续开发应以本文件和用户的新指示为准，不应重新采用聊天中已经被替换的早期设计。

基础交接文档已完成；现行 [docs/ui](ui/README.md) 包含经过全局组件统一的可编辑源稿、独立离线预览及唯一的统一视觉方案。过期截图已移出当前工作树；历史 UI 不作为实现依据。当前为设计交接阶段，Android 工程尚未初始化，正式开发按本计划实施。

开始准备交接资料前项目为空仓库，仅有 `.git`；目前已准备基础文档、UI 设计文件，并保留远端 artwork 中的狐狸图标资产。旧版应用已放弃，当前分支没有可运行的 Android 工程、数据库或业务测试；网页 UI 已具备部分演示交互，但正式业务、数据结构、图标接入和 Android 构建仍为待实现／验证项。

阅读顺序为 AGENTS.md → README.md → 本计划 → [功能文档](features/01-common.md) → DATA_FORMAT.md → [UI 设计说明](ui/README.md)。本计划负责产品需求、设计规则和验收；DATA_FORMAT.md 将这些规则具体化为待实现的 v1 数据契约；现行 UI 源稿及由其生成的离线预览提供视觉和交互参考，演示结构不替代正式数据契约；废弃截图及历史 UI 不作为实现依据。


## 2. 产品定位、范围与默认值

### 2.1 目标

开发一个自己长期使用、可以逐步追加功能的 Android 个人工具箱，暂名 **KitsuneApp**。第一版包含：

- 用餐记录：记录每天三餐、在家吃的计划、用餐方式、制作分类、菜品与备注。
- 记账：多个钱包、独立币种、收支、分类、计算器金额输入、转账、退款、查询与月度统计。
- 数据管理：完整 JSON 备份与恢复、标准 CSV 导入导出、指定文件夹与 GitHub 私有库的最新完整数据自动备份。
- 个性化：柔和生活卡片风格、预设调色板、自选主色、跟随系统明暗、系统动态取色、原创卡通狐狸头像图标。

### 2.2 使用边界

- 单手机为主，本地数据库为业务数据的唯一事实来源；日常记录、查询和统计无需联网。
- GitHub 用于保存最新完整云端备份。换机时由用户主动恢复。
- 不依赖自建服务器，不设置应用账号注册流程；GitHub 连接是可选配置。
- 第一版用餐与记账独立录入，不自动将用餐转换成账目。
- 第一版不做预算、信用卡账单、借贷、分期、自动汇率、折算总资产、多设备冲突自动合并、照片附件或运行时插件系统。

### 2.3 实施默认值

以下是已经提出的实现默认值，不要求用户在开发前再次选择：

- 平台：Android 8.0／API 26 及以上，首版简体中文，通过签名 APK 安装。
- 技术：Kotlin、Jetpack Compose、Material 3、Room、WorkManager。
- 外观：柔和圆角卡片，默认“跟随系统明暗＋杏橙预设”。
- 钱包：用户自行创建，币种必须明确选择，初始余额默认 0，独立核算默认关闭；不预设所有钱包使用人民币或其他同一币种。
- 分类：收入、支出各提供一个全局“未分类”兜底项和可管理的通用分类；各钱包的收入／支出分类可分别切换为专属配置，未配置时自动沿用通用分类。制作分类初始提供“炒菜、预制菜”。
- 本地自动备份：用户授权后启用，每个数据集在该目录保留一份最新成功的完整自动备份。
- 云端备份：用户配置并连接成功后启用，默认允许任意联网方式，可改为仅 Wi-Fi。GitHub 自动上传合并连续变更，后台最小上传间隔为 30 分钟，详见第 9.4 节。
- 完整备份采用可阅读、未加密的 JSON；凭证不进入备份。
- 第一次录入用餐不预选在家吃或用餐方式；记账默认支出、今天、最近使用的未归档常规钱包。独立核算钱包只能手动选择。

## 3. 信息架构与主要页面
对应规则已迁移至 [features/01-common.md](features/01-common.md)。

## 4. 用餐功能详细规则
对应规则已迁移至 [features/03-meals.md](features/03-meals.md)。

## 5. 记账功能详细规则
对应规则已迁移至 [features/05-ledger.md](features/05-ledger.md)。

## 6. 外观、调色板与系统主题
对应规则已迁移至 [features/07-appearance.md](features/07-appearance.md)。

## 7. APP 图标
对应规则已迁移至 [features/07-appearance.md](features/07-appearance.md)。

## 8. 技术架构与核心数据接口
对应规则已迁移至 [architecture/ANDROID.md](architecture/ANDROID.md)。

## 9. 完整 JSON 备份、本地自动备份与 GitHub
对应规则已迁移至 [features/08-data-management.md](features/08-data-management.md)。

## 10. CSV 导入导出
对应规则已迁移至 [features/08-data-management.md](features/08-data-management.md)。

## 11. 开发文档与仓库交付

用户要求提前准备的四份基础交接文档均已建立，本轮另保存 UI 设计。后续开发时持续维护以下资料，并将设计状态补充为实际实现和验证情况：

| 文件 | 内容 |
| --- | --- |
| `AGENTS.md` | 文档阅读入口、协作约定与验证要求；业务设计引用本计划，数据契约引用 DATA_FORMAT，构建命令引用 README，不重复维护 |
| `README.md` | 项目介绍、环境搭建、运行、打包、安装、常见问题及实际验证状态 |
| `docs/PLAN.md` | 本文件，需求、默认值、开发阶段、验收标准及变更记录 |
| `docs/DATA_FORMAT.md` | 待实现的 v1 实体与数据契约、JSON／CSV 样例、版本号、迁移及兼容规则；开发后与实际实现同步 |
| `docs/ui/` | 已保存 UI 源稿、独立预览、打包工具及演示边界；设计规则仍维护在本计划 |

- 文档必须与实际实现同步，不能把尚未运行的命令标注为已验证。
- 配置 Git 忽略规则，排除构建产物、本机 SDK 路径、签名私钥、凭证和用户个人数据。
- 提供可重复构建的工程；记录签名与升级安装方式，签名密钥不进入仓库。
- 日后变更需求时更新计划的相关段落与变更记录，避免只在聊天中修订而遗漏文件。

## 12. 实施阶段

### 阶段 1：项目骨架、文档、主题与用餐

- 初始化 Android 工程，锁定稳定工具链与依赖，维护已有 AGENTS.md、README.md 和数据格式文档，补充实际版本、命令与验证结果。
- 从首个安装包起配置第 9.7 节的系统备份排除规则与安装归属检查，业务录入复用第 8.3 节的完整 JSON 保存校验。
- 建立导航、主题系统、共用组件、数据库与基础迁移框架。
- 完成调色板、预览应用、系统跟随；复用 artwork 中的狐狸图标并完成新版接入。
- 完成用餐记录、方式管理、制作分类、搜索、复制和统计。
- 此阶段即可构建、安装并离线录入用餐。

### 阶段 2：记账

- 完成钱包、分类、计算器、收支、转账、退款、查询和按币种统计。
- 优先验证金额精度与业务约束，再进行页面细节调整。

### 阶段 3：本地数据管理

- 完成完整 JSON 导出、格式迁移、恢复前备份与事务恢复。
- 完成本地目录授权、最新完整备份的安全替换、失败重试与状态页。
- 完成 CSV 模板、预览校验、映射、导入与导出。

### 阶段 4：GitHub 与正式安装包

- 完成私有仓库连接、凭证存储、最新完整备份的合并调度、远端版本核对、冲突处理与主动恢复。
- 完成自动备份后的仓库容量检查、可编辑的提醒阈值及 App 内提醒。
- 验证异常状态、升级迁移、主题图标和真机操作；交付签名 APK 与使用说明。
- 在用户未提供目录授权或 GitHub 配置时，应用应保持可用且明确显示未配置，不伪造集成成功；可用假实现／测试服务验证逻辑，实际连接由用户在应用内完成。

每阶段保持项目可构建、核心流程可运行。测试、文档和数据兼容工作随阶段完成，不留到最后统一补做。

## 13. 验收与测试计划
对应规则已迁移至 [testing/ACCEPTANCE.md](testing/ACCEPTANCE.md)。

## 14. 官方技术参考

以下为规划时查阅的参考，实施时核对所选稳定依赖对应的 API，不依赖未经验证的旧版本示例：

- [Android 应用架构](https://developer.android.com/topic/architecture)
- [离线优先架构](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Room 本地数据库](https://developer.android.com/training/data-storage/room)
- [Compose Material 3、明暗与动态颜色](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)
- [Android 自适应与主题图标](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Storage Access Framework 与目录授权](https://developer.android.com/training/data-storage/shared/documents-files)
- [WorkManager 任务、约束与重试](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- [WorkManager 唯一任务与冲突策略](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work)
- [CoroutineWorker 执行时限](https://developer.android.com/reference/androidx/work/CoroutineWorker)与[长时 Worker](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [Android 单调计时](https://developer.android.com/reference/android/os/SystemClock)与[网络传输判定](https://developer.android.com/develop/connectivity/network-ops/reading-network-state)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android Auto Backup 与设备迁移规则](https://developer.android.com/identity/data/autobackup)
- [GitHub 仓库 Contents API](https://docs.github.com/en/rest/repos/contents)
- [GitHub 仓库元数据 REST API](https://docs.github.com/en/rest/repos/repos#get-a-repository)
- [GitHub REST API 限流](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)与[错误处理](https://docs.github.com/en/rest/using-the-rest-api/troubleshooting-the-rest-api)

## 15. 本次交接变更记录

2026-11-16：将聊天方案首次保存为项目文件，并合并以下已确认修改：

- 单手机本地优先、GitHub 云端备份与主动恢复；多钱包独立币种、不自动折算。
- 用餐事后填写，一餐多个制作分类，CSV 与完整 JSON 导入导出。
- 柔和卡片风；新增预设调色板、自选主色、跟随系统明暗与系统动态取色。
- 图标采用由实现者自行绘制的暖橙色卡通狐狸头像，支持自适应与单色主题图标。
- 计划在家吃改为必填是／否；旧是否下厨改为设置中可维护的用餐方式，初始下厨／外卖。
- 用餐方式在计划在家吃为是时必填单选，为否时仍显示且可不填；制作分类按方式开关显示。
- 首次计划落盘时，AGENTS.md、README.md 和 DATA_FORMAT.md 尚待准备。
- 首次计划落盘任务只保存并检查本文件，随后停止，等待后续指示。

2026-11-16（后续文档补齐）：用户追加要求由当前模型提前准备基础文档，方便其他模型接手。

- 新增根目录 AGENTS.md、README.md 和 docs/DATA_FORMAT.md；明确当前没有 Android 工程，构建命令仅为待验证模板。
- 细化 v1 数据表示：JSON Long 金额和 revision 为字符串、钱包精度显式保存、兜底分类标志、导入凭据随数据恢复、自动／手动／恢复前快照分类。
- 为 CSV 名称映射补充“钱包规范化名称＋币种唯一”的实现约束，示例涵盖用餐、跨币种转账和部分退款。
- 更新本计划中的当前范围、数据契约与文档状态；当前任务仍只完成文档，不开始应用开发。

2026-11-17（记账时间）：收入、支出、转账和退款增加必填的本地业务 time（HH:mm），默认当前分钟、可修改；同步录入、展示、排序、JSON／CSV 和验收规则。当前格式尚未发布，直接修订 v1 设计，不将旧设计中的缺失时间静默补零。

2026-11-17（UI 设计保存）：保存早期视觉原型（其中备选方案现已废弃）及本轮确认的首页、紧凑记账与计算器弹层、月份选择、钱包范围、记录删除、用餐筛选和紧凑列表、月度／全部记录统计条形。增加 docs/ui 的源稿、离线预览、可复现打包工具及 15 张截图，更新 README 和协作入口；Android 开发阶段仍未开始。

2026-11-17（系统壁纸配色）：在已设计的系统动态取色规则下补齐“跟随系统壁纸”入口，独立于明暗模式；网页新增三种配色来源、草稿预览、应用／取消，并保留上次预设与自选色。动态壁纸颜色在网页中仅作明确标注的示意，实际接入仍属 Android 实现阶段。

2026-11-18（自动备份）：本地目录和 GitHub 均以保护最新完整数据为目标，待处理状态合并到最新修订。本地在新备份发布后替换旧自动备份，应用内的 GitHub 恢复使用当前备份文件。

2026-11-18（设计审查）：补齐恢复前保护与恢复后的云端确认，采用日期时间加序号命名自动备份。明确跨钱包退款的统计归属、共享的日期范围、计算器最终舍入和派生余额范围，并同步首页多币种与外观预览规则。

2026-11-18（GitHub 备份设计修订）：明确自动上传的合并窗口和最小间隔、已接受的远端版本、冲突操作及恢复期间在途请求的处理。

2026-11-20（GitHub 备份二次评审）：补齐备份文件大小与版本校验、API 错误分类、后台任务接续、时钟变化、仅 Wi-Fi 和手动恢复历史备份的规则。

2026-11-20（GitHub 备份三次评审）：明确大文件的流式处理与内存边界，以及 Worker 超时取消和未确认写入的接续规则。

2026-11-20（GitHub 备份四次评审）：将完整备份限制接入业务保存，补齐容量提醒、系统备份排除及设备迁移后的重新确认规则。

2026-11-21（GitHub 仓库容量提醒）：增加自动备份后的容量检查、设置中的阈值和超限弹窗。

2026-11-21（用餐与账本 UI）：统一记录行、筛选控件、录入页和统计组件，更新当时的 UI 原型及关键页面快照（历史快照已删除）。

2026-11-21（设置外观预览）：仅在有待应用的外观修改时展示预览、取消和应用按钮。

2026-11-21（现行设计资料清理）：PR #10／#11 已统一卡片和全局组件规范。删除过期截图及已覆盖、已废弃 CSS 定义，保持业务规则、当前视觉方案与图标资产不变；仅以现行源稿及生成预览作为视觉参考。

2026-11-21（统一为单一视觉方案）：废弃清爽日常备选方案，仅保留原杏橙手账的组件造型，卡片圆角固定为 17；保留五种预设、自选主色、系统取色、明暗及全局动效，不引入额外的风格设置或数据字段。
