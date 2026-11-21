# Android 架构设计

## 8. 技术架构与核心数据接口

### 8.1 结构

- 用 Kotlin 与 Compose 构建原生界面，统一使用 Material 3 设计系统。
- UI → ViewModel → Repository → Room；界面观察本地状态，备份服务不能成为读取业务数据的前置依赖。
- 按 `feature/meals`、`feature/ledger`、`feature/settings` 分包，核心能力放入数据库、主题／组件、备份、文件访问等共享包。
- 第一版采用按功能分包的单体应用，不为两个业务模块引入动态插件或独立后端。
- 新功能接入应用导航、首页卡片、自己的实体与业务逻辑，以及统一备份的导出／校验／恢复处理。
- 选择实施时相互兼容的稳定工具链和依赖，集中锁定版本；不得使用浮动依赖版本。实际 JDK、SDK 和构建命令验证后记录在 README 与 AGENTS.md。

### 8.2 实体与约束

| 对象 | 最低必需信息 |
| --- | --- |
| MealRecord | UUID、date、mealSlot、plannedAtHome（不可空布尔值）、mealMethodId（可空引用）、dishes、note、createdAt、updatedAt；日期与餐次唯一 |
| MealMethod | UUID、name、normalizedName、sortOrder、archived、showPreparationCategories；初始下厨／外卖 |
| Category | UUID、scope（PREPARATION／INCOME／EXPENSE）、walletId（仅收支专属分类有值，否则 null）、name、normalizedName、sortOrder、archived、isDefault；同(scope、walletId)下规范化名称唯一，收支各有一个全局兜底项 |
| MealPreparationCategory | mealId、categoryId，唯一联合关联，多对多 |
| Wallet | UUID、name、normalizedName、currencyCode、currencyScale、openingBalanceMinor、sortOrder、archived、separateAccounting、incomeCategoryMode、expenseCategoryMode、时间戳 |
| FinancialEntry | UUID、date、time（HH:mm，必填）、type（INCOME／EXPENSE／TRANSFER／REFUND）、walletId、amountMinor、categoryId、toWalletId、toAmountMinor、originalExpenseId、note、时间戳 |
| ThemePreferences | themeMode（SYSTEM／LIGHT／DARK）、colorSource（PRESET／CUSTOM／SYSTEM）、presetId、customSeedColor |
| ImportReceipt | kind（MEALS／LEDGER）、源文件 sha256、importedAt；随 JSON 迁移，防止同一无 ID CSV 重复导入 |
| DataState／备份执行状态 | 数据集标识、本地修订号、任务代次，以及各目标已确认的备份、待处理状态与失败信息；执行状态仅保存在本机 |

- FinancialEntry 的 walletId 对收入／支出／退款表示对应钱包，对转账表示转出钱包；toWalletId 和 toAmountMinor 只用于转账。
- 转账使用两侧钱包的货币精度；退款通过 originalExpenseId 引用原支出，分类从原支出取得。
- UUID 是业务数据身份，显示名称可改，所有关联用 ID，不用中文字符串作为外键或条件。
- 数据库中的金额为 Long；JSON 中金额与 revision 以十进制字符串传输，避免中间工具损失整数精度。具体字段与示例以[数据模型](../data/01-model.md)及[JSON v1 契约](../data/02-json.md)为准。
- 规范化分类名至少包含去除首尾空格和大小写无关比较；保留用户显示写法。收支类别唯一性按 (scope,walletId,normalizedName) 校验，通用 walletId=null 作为同一个逻辑命名空间。各分类域不互相混用。分类引用的归属和有效范围通过稳定 ID 校验；转账不设分类，退款取原支出分类。
- 日历日期保存为 `YYYY-MM-DD`，不把当地日期先转换成 UTC 再按 UTC 分组；createdAt／updatedAt 使用 UTC。财务流水额外保存本地业务 time（HH:mm），与 date 配对；这不是创建时间，不随设备时区变化自动换算。日／月统计仍按记录的 date 分组，转账两侧共用同一业务日期和时间。
- 只在表单草稿允许 plannedAtHome 尚未选择。持久化、CSV 导入、JSON 恢复都必须满足其非空约束。
- plannedAtHome 为 true 时 mealMethodId 不可空；false 时可以为空。mealMethodId 为空时制作分类必须为空。
- 方式当前关闭分类开关不意味着历史分类非法，不能在数据库迁移或恢复时据此清空历史关联。
- 数据库 schema 与备份 formatVersion 分别版本化；升级必须显式迁移，不能用破坏性重建丢弃用户数据。

### 8.3 事务与统一变更入口

- 记录保存、关联分类创建、转账、退款、批量导入都走业务事务；成功后才向 UI 返回保存成功。
- 在业务事务内按 [完整 JSON 保存约束](../data/02-json.md#45-完整-json-的保存约束)校验提交后的数据。录入、编辑、批量导入及恢复写库共用规则。新增或加重结构超限时提示具体字段或总量并回滚，不增加修订或安排备份。已有超限数据按该节的整理规则处理，GitHub 容量按[完整数据可靠性](../features/08-data-management.md#92-最新完整数据的可靠性)处理。
- 所有可备份数据变更经统一入口增加修订号，并为已启用的备份目标登记最新待备份状态。
- 创建分类并保存记录算一次业务提交；CSV 批量导入算一次提交；主题应用一次算一次提交。
- 备份任务、进度、重试、系统主题变化等内部状态不能触发递归备份。
- 删除普通记录也增加修订号，后续完整备份反映删除后的数据。单设备完整快照方案不要求为将来的多端合并提前引入 tombstone 系统。

