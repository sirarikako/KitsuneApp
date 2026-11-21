# KitsuneApp 开发计划与交接说明

> 旧版 0.1.0 实现及 PR #1 已因重新设计而放弃，狐狸图标资产由 PR #2 保留在 artwork。本文件已按本轮新版 UI 更新；后续开发以当前计划和 docs/ui 设计资料为准，不恢复旧版界面或实现。

更新日期：2026-11-21（Asia/Shanghai）<br>
状态：远端分支已保存模块化设计与 UI 预览；尚无已并入远端的 Android 工程（本地未推送提交不在此核验范围）。

## 1. 如何使用本文件

本文件保留产品范围、阶段安排、历史章节入口及变更记录。详细业务规则已迁至 [模块设计导航](README.md)，开发时按任务阅读对应模块，不采用被替换的历史设计。

基础交接文档已完成；现行 [docs/ui](ui/README.md) 包含经过全局组件统一的可编辑源稿、独立离线预览及唯一的统一视觉方案。过期截图已移出当前工作树；历史 UI 不作为实现依据。当前可访问的远端分支仅包含设计交接资料；Android 实现状态须以实际并入的代码为准，开发按模块设计实施。

开始准备交接资料前项目为空仓库，仅有 `.git`；目前已准备基础文档、UI 设计文件，并保留远端 artwork 中的狐狸图标资产。旧版应用已放弃；当前远端分支没有可运行的 Android 工程、数据库或业务测试（不代表本地未推送分支状态）；网页 UI 已具备部分演示交互，但正式业务、数据结构、图标接入和 Android 构建仍为待实现／验证项。

阅读顺序为 AGENTS.md → README.md → 本计划 → [功能文档](features/01-common.md) → DATA_FORMAT.md → [UI 设计说明](ui/README.md)。本计划负责产品范围与开发阶段；功能规则详见 [模块设计](README.md)，DATA_FORMAT.md 是 v1 数据契约索引；现行 UI 源稿及由其生成的离线预览提供视觉和交互参考，演示结构不替代正式数据契约；废弃截图及历史 UI 不作为实现依据。


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
- 云端备份：用户配置并连接成功后启用，默认允许任意联网方式，可改为仅 Wi-Fi。GitHub 自动上传合并连续变更，后台最小上传间隔为 30 分钟，详见 [GitHub 私有库规则](features/09-backup.md#94-github-私有库)。
- 完整备份采用可阅读、未加密的 JSON；凭证不进入备份。
- 第一次录入用餐不预选在家吃或用餐方式；记账默认支出、今天、最近使用的未归档常规钱包。独立核算钱包只能手动选择。

## 3. 信息架构与主要页面

四个底栏入口、共用组件和全局动效见 [公共规范](features/01-common.md)；首页专属规则见 [首页](features/02-home.md)。

### 3.1 用餐与账本的共用界面

规则详见 [用餐与账本的共用界面](features/01-common.md#31-用餐与账本的共用界面)。

### 3.2 全局动效与页面过渡

规则详见 [全局动效与页面过渡](features/01-common.md#32-全局动效与页面过渡)。

## 4. 用餐功能详细规则

用餐全部规则（含计划在家吃、用餐方式和制作分类）见 [用餐](features/03-meals.md)。

### 4.1 记录字段

规则详见 [记录字段](features/03-meals.md#41-记录字段)。

### 4.2 用餐方式设置

规则详见 [用餐方式设置](features/03-meals.md#42-用餐方式设置)。

### 4.3 制作分类与状态切换

规则详见 [制作分类与状态切换](features/03-meals.md#43-制作分类与状态切换)。

### 4.4 查询、复用与统计

规则详见 [查询、复用与统计](features/03-meals.md#44-查询复用与统计)。

## 5. 记账功能详细规则

钱包归 [钱包模块](features/04-wallets.md)，收入／支出分类归 [分类模块](features/06-categories.md)，流水、转账、退款与统计归 [记账模块](features/05-ledger.md)。

### 5.1 钱包

规则详见 [钱包](features/04-wallets.md#51-钱包)。

### 5.2 收支与分类

规则详见 [收支与分类](features/06-categories.md#52-收支与分类)。

### 5.3 金额计算器

规则详见 [金额计算器](features/05-ledger.md#53-金额计算器)。

### 5.4 转账

规则详见 [转账](features/05-ledger.md#54-转账)。

### 5.5 退款

规则详见 [退款](features/05-ledger.md#55-退款)。

### 5.6 查询与统计

规则详见 [查询与统计](features/05-ledger.md#56-查询与统计)。

## 6. 外观、调色板与系统主题

正式主题规则见 [外观模块](features/07-appearance.md)。

### 6.1 独立的两个设置维度

规则详见 [独立的两个设置维度](features/07-appearance.md#61-独立的两个设置维度)。

### 6.2 预设与自选颜色

规则详见 [预设与自选颜色](features/07-appearance.md#62-预设与自选颜色)。

### 6.3 系统跟随与兼容

规则详见 [系统跟随与兼容](features/07-appearance.md#63-系统跟随与兼容)。

### 6.4 预览、保存和备份

规则详见 [预览、保存和备份](features/07-appearance.md#64-预览保存和备份)。

## 7. APP 图标

图标资源与接入规则见 [外观模块](features/07-appearance.md)。

### 7.1 视觉规格

规则详见 [视觉规格](features/07-appearance.md#71-视觉规格)。

### 7.2 资产与接入

规则详见 [资产与接入](features/07-appearance.md#72-资产与接入)。

## 8. 技术架构与核心数据接口

架构及事务约束见 [Android 架构模块](architecture/ANDROID.md)。

### 8.1 结构

规则详见 [结构](architecture/ANDROID.md#81-结构)。

### 8.2 实体与约束

规则详见 [实体与约束](architecture/ANDROID.md#82-实体与约束)。

### 8.3 事务与统一变更入口

规则详见 [事务与统一变更入口](architecture/ANDROID.md#83-事务与统一变更入口)。

## 9. 完整 JSON 备份、本地自动备份与 GitHub

JSON、恢复与设备迁移见 [数据管理](features/08-data-management.md)；本地目录、GitHub 上传、冲突、Worker 和容量提醒见 [备份模块](features/09-backup.md)。

### 9.1 备份格式与边界

规则详见 [备份格式与边界](features/08-data-management.md#91-备份格式与边界)。

### 9.2 最新完整数据的可靠性

规则详见 [最新完整数据的可靠性](features/08-data-management.md#92-最新完整数据的可靠性)。

### 9.3 本地目录

规则详见 [本地目录](features/09-backup.md#93-本地目录)。

### 9.4 GitHub 私有库

规则详见 [GitHub 私有库](features/09-backup.md#94-github-私有库)。

#### 备份文件读取与大小限制

请查看 [备份模块：备份文件读取与大小限制](features/09-backup.md#备份文件读取与大小限制)。

#### 自动上传频率

请查看 [备份模块：自动上传频率](features/09-backup.md#自动上传频率)。

#### 仓库容量提醒

请查看 [备份模块：仓库容量提醒](features/09-backup.md#仓库容量提醒)。

#### 任务接续、计时与联网

请查看 [备份模块：任务接续、计时与联网](features/09-backup.md#任务接续计时与联网)。

#### Worker 超时与停止

请查看 [备份模块：Worker 超时与停止](features/09-backup.md#worker-超时与停止)。

#### API 错误与重试

请查看 [备份模块：API 错误与重试](features/09-backup.md#api-错误与重试)。

#### 远端变化与冲突

请查看 [备份模块：远端变化与冲突](features/09-backup.md#远端变化与冲突)。

### 9.5 状态展示

规则详见 [状态展示](features/09-backup.md#95-状态展示)。

### 9.6 完整恢复流程

规则详见 [完整恢复流程](features/08-data-management.md#96-完整恢复流程)。

### 9.7 Android 系统备份与设备迁移

规则详见 [Android 系统备份与设备迁移](features/08-data-management.md#97-android-系统备份与设备迁移)。

## 10. CSV 导入导出

CSV 导入、导出与预览规则见 [数据管理](features/08-data-management.md)，格式及列约束见 [CSV 契约](data/03-csv.md)。

### 10.1 标准模板

规则详见 [标准模板](features/08-data-management.md#101-标准模板)。

### 10.2 预览与提交

规则详见 [预览与提交](features/08-data-management.md#102-预览与提交)。

## 11. 开发文档与仓库交付

用户要求提前准备的四份基础交接文档均已建立，本轮另保存 UI 设计。后续开发时持续维护以下资料，并将设计状态补充为实际实现和验证情况：

| 文件 | 内容 |
| --- | --- |
| `AGENTS.md` | 文档阅读入口、协作约定与验证要求；业务规则引用 docs/features，数据契约引用 docs/data，构建命令引用 README，不重复维护 |
| `README.md` | 项目介绍、环境搭建、运行、打包、安装、常见问题及实际验证状态 |
| `docs/PLAN.md` | 本文件，范围、默认值、开发阶段、旧入口及变更记录 |
| `docs/README.md`、`docs/features/` | 当前详细业务设计的导航与唯一来源 |
| `docs/architecture/`、`docs/testing/` | 技术架构与跨模块验收 |
| `docs/DATA_FORMAT.md`、`docs/data/` | 数据契约索引与模型、JSON、CSV、兼容性分册 |
| `docs/ui/` | 分模块 UI 源稿、自动生成的独立预览与演示边界；规则以对应功能模块为准 |

- 文档必须与实际实现同步，不能把尚未运行的命令标注为已验证。
- 配置 Git 忽略规则，排除构建产物、本机 SDK 路径、签名私钥、凭证和用户个人数据。
- 提供可重复构建的工程；记录签名与升级安装方式，签名密钥不进入仓库。
- 日后变更需求时更新对应功能或数据模块；涉及范围和阶段调整时再更新本计划与变更记录。

## 12. 实施阶段

### 阶段 1：项目骨架、文档、主题与用餐

- 初始化 Android 工程，锁定稳定工具链与依赖，维护已有 AGENTS.md、README.md 和数据格式文档，补充实际版本、命令与验证结果。
- 从首个安装包起配置 [系统备份排除与设备迁移](features/08-data-management.md#97-android-系统备份与设备迁移)，业务录入复用 [统一事务与数据保存校验](architecture/ANDROID.md#83-事务与统一变更入口)。
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

完整验收项见 [跨模块验收计划](testing/ACCEPTANCE.md)。

### 13.1 用餐

规则详见 [用餐](testing/ACCEPTANCE.md#131-用餐)。

### 13.2 记账

规则详见 [记账](testing/ACCEPTANCE.md#132-记账)。

### 13.3 外观与图标

规则详见 [外观与图标](testing/ACCEPTANCE.md#133-外观与图标)。

### 13.4 备份、导入与恢复

规则详见 [备份、导入与恢复](testing/ACCEPTANCE.md#134-备份导入与恢复)。

### 13.5 测试实施与交付证据

规则详见 [测试实施与交付证据](testing/ACCEPTANCE.md#135-测试实施与交付证据)。

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
