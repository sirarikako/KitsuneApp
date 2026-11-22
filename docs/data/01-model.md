# v1 数据模型与通用约定

## 1. 版本与数据边界

| 概念 | v1 约定 |
| --- | --- |
| Room 数据库 schemaVersion | 计划从 1 开始；当前没有已创建的数据库 |
| 完整 JSON formatVersion | 数字 `1` |
| 完整 JSON format | 固定字符串 `kitsune-backup` |
| CSV | 两套固定表头，称为 meals-v1 和 ledger-v1；不在文件开头插入非 CSV 注释 |
| 应用版本 | 与上述格式版本独立，初始化工程时记录 |

完整 JSON 是模块业务数据和可迁移设置的快照。CSV 是记录交换格式，不用于完整恢复钱包初始余额、方式配置或主题。Room 表可以使用规范化关系，JSON 使用便于迁移的聚合结构，二者不要求字段布局完全相同。

## 2. 通用约定

### 2.1 标识、文本与时间

- 实体 ID、datasetId、snapshotId 使用 UUID，导出为小写标准带连字符形式。每类实体的 ID 唯一，不从显示名称生成 ID。
- datasetId 标识一套业务数据；恢复后采用恢复文件的数据集身份。本机任务代次和安装身份不属于业务备份。
- createdAt、updatedAt 在数据库中用 UTC 的 `YYYY-MM-DDTHH:mm:ss.SSSZ` 保存，按 [JSON 导出规则](02-json.md#41-顶层结构)写为 UTC+8。导入接受 `Z` 或 `+08:00` 结尾的有效 ISO 时间，包括没有小数秒的形式，再统一规范为 UTC 毫秒。
- date 是严格有效的 `YYYY-MM-DD` 日历日期，不附加时区，不通过 UTC 日期推算餐次或月份。
- 财务流水另有必填 time，使用本地业务时间字符串 `HH:mm`（24 小时制，00:00–23:59），不含秒或时区。数据库可使用严格校验的文本列，JSON／CSV 均保持相同形式；不用餐次、createdAt 或当前时间猜测缺失值。
- date＋time 记录用户选择的发生日期时间，保存后不因设备时区变化自动转换；createdAt／updatedAt 仍为独立 UTC 审计时间。
- 名称非空，保存前去除首尾空白。数据库 normalizedName 按 Unicode NFC、trim、Locale.ROOT 小写计算；不删除名称内部空格。
- normalizedName 是派生检索字段，不进入 JSON；恢复时重新计算后校验唯一性，不能无提示合并冲突对象。
- dishes、note 为字符串，未填保存为空字符串，不保存为 null。保留中文、引号和多行内容，不把自由文本当代码执行。
- 枚举使用本文指定的大写 ASCII 值。JSON 布尔值必须是真正的 true／false，不能用字符串、数字或 null 代替。

### 2.2 金额与修订号

- 单笔流水金额及钱包初始余额存为最小货币单位的有符号 64 位整数，范围为 `-9223372036854775808` 至 `9223372036854775807`。
- **JSON 的所有 `*Minor` 金额字段及 revision 使用十进制字符串。** 这是跨工具传输的表示方式；数据库中仍然是整数，不能用 Double 中转。
- 整数字符串不含小数点、正号、分组符或前导零，零统一为 `"0"`，不接受 `"-0"`。revision 为非负 64 位整数；流水金额必须大于零；初始余额可为负。
- 钱包保存 currencyCode 和 currencyScale，实际金额为 `minor / 10^currencyScale`。例：CNY scale=2，`"1280"` 表示 12.80；JPY scale=0，`"1280"` 表示 1280。
- currencyCode 为三位大写货币代码，currencyScale 为 0 至 4 的整数。创建钱包从支持的 ISO 币种选择并确定精度；恢复时保留文件内精度，不根据新系统的默认值重解释旧金额。
- 同一数据集内同币种钱包的精度必须一致。产生流水后币种和精度不可修改。
- 退款累计、钱包余额及统计加总采用任意精度整数。派生余额及统计可超出 64 位范围，按最小单位精确格式化。记录编辑、删除、导入或恢复时只对存储字段应用 64 位范围检查，同时校验退款累计上限等业务约束。
- 计算器将十进制输入转为精确分数，表达式求值结束后一次性按钱包精度 HALF_UP，再校验正数与单笔金额上限。例如两位小数币种的 `1÷3×100` 得到 `33.33`。CSV 输入超精度直接报错，不静默舍入。

### 2.3 空值与集合

- JSON 对象中的本文规定字段都必须存在；可空引用显式写 null。
- 没有记录的集合写 `[]`，不省略、不写 null。
- 制作分类关联在数据库中使用关联表，在 JSON 记录内写为 preparationCategoryIds 数组；元素不得重复。
- isDefault 只用于 Category 中的收入、支出兜底项；每个账目分类域恰好有一个兜底，制作分类全部为 false。
- 排序字段 sortOrder 为非负整数，允许相同，界面以 ID 作为稳定的次排序。
- 归档对象必须保留在完整快照中；归档不表示删除引用。

## 3. 逻辑实体与约束

普通业务实体共有 `id,createdAt,updatedAt`。以下列出其余字段；所有字段的序列化类型以[JSON v1 契约](02-json.md#4-json-v1)为准。

### 3.1 用餐

| 对象 | 字段 | 约束 |
| --- | --- | --- |
| MealMethod | name、normalizedName、sortOrder、archived、showPreparationCategories | 规范化名称唯一；初始下厨=true、外卖=false；开关不依赖名称判断 |
| Category（PREPARATION） | scope、name、normalizedName、sortOrder、archived、isDefault | scope 内名称唯一，isDefault=false |
| MealRecord | date、mealSlot、plannedAtHome、mealMethodId、dishes、note | date＋mealSlot 唯一；mealSlot 为 BREAKFAST／LUNCH／DINNER |
| MealPreparationCategory | mealId、categoryId | 联合唯一；两端引用存在，分类 scope 必须为 PREPARATION |

必须同时满足：

1. plannedAtHome 为 true 或 false，持久化时不能空；表单初始未选只是 UI 草稿。
2. 为 true 时 mealMethodId 必填且指向存在的方式；为 false 时可为空。
3. mealMethodId 为空时 preparationCategoryIds 必须为空；方式存在时分类可以为空。
4. 允许历史记录引用归档的方式或分类。新表单只提供可选的未归档项，编辑历史记录不强制换掉原选择。
5. showPreparationCategories=false 不代表历史关联无效。恢复和 CSV 导入不能根据当前开关丢弃已有分类。
6. 用户主动清空方式或换到不显示分类的方式，保存时清除该记录分类；仅修改方式设置或编辑备注不会清除历史分类。

不建立 isCooked、是否下厨或将“外卖”映射成 plannedAtHome=false 的兼容字段。当前尚无已发布旧数据，不需要为聊天早期方案虚构迁移格式。

### 3.2 钱包与收支分类

Wallet 字段：`name,normalizedName,currencyCode,currencyScale,openingBalanceMinor,sortOrder,archived,separateAccounting,incomeCategoryMode,expenseCategoryMode`。

- 为使 CSV 的“钱包名称＋币种”能唯一映射，同一数据集内 `(normalizedName,currencyCode)` 唯一，包含已归档钱包；相同名称的不同币种钱包允许存在。
- normalizedName 只用于匹配，不作为外键；同名映射不明确时必须在预览阶段解决，不能随意选第一个钱包。
- `separateAccounting` 为布尔值，创建默认 `false`；它只影响查询的默认钱包范围，余额及历史记录按原有钱包 ID 不变。独立核算钱包仍进入完整 JSON 备份和恢复，不能在 CSV 导出或导入时因默认筛选被漏掉。修改此值允许在钱包已有流水后进行。
- 余额为派生值，不导出 currentBalance 或余额缓存，也不以缓存覆盖流水。
- 早期 v1 JSON 快照的字段缺省迁移及严格校验例外，统一按 [JSON v1 契约](02-json.md#41-顶层结构) 执行。
- `incomeCategoryMode`／`expenseCategoryMode` 的枚举值为 DEFAULT／CUSTOM，分别决定该钱包收入／支出的可用类别集合，创建默认 DEFAULT；`separateAccounting` 与类别模式相互独立。
- Category 新增 `walletId`：PREPARATION 及通用 INCOME／EXPENSE 分类的值为 null，专属收入／支出分类为存在的钱包 UUID。收入和支出各仅有一个 `walletId=null,isDefault=true` 的全局“未分类”兜底项；`walletId!=null` 的分类必须 `isDefault=false`，且不能使用保留显示名“未分类”。
- 分类名称唯一键为 `(scope, walletId 或 GLOBAL, normalizedName)`，包含归档项，同名在不同钱包合法；不可用 NULL 的普通数据库 UNIQUE 比较代替 GLOBAL 规范化归属比较。此规则不影响 PREPARATION 的既有全局唯一性。
- DEFAULT 的活动选项为该 scope 的未归档通用类别（含全局兜底）；CUSTOM 的活动选项为该钱包同 scope 的未归档专属类别及全局兜底。CUSTOM 即使当前没有专属类别也只提供全局兜底，绝不因空表自动切回 DEFAULT。切回 DEFAULT 时专属类别和历史关联不删除。
- 分类以稳定 UUID 引用，流水历史即使所属钱包已切换模式、分类已停用／归档，仍合法保留并展示。新增分类自动归属当时选择的设置作用域；直接录入创建分类也按当前钱包与收支方向归属。退款分类来自原支出，不取到账钱包的候选类别。
- isDefault=true 的全局分类不能删除或停用；其他已引用分类只能停用。现行候选类别切换或重命名不自动改写已有流水的 categoryId。

### 3.3 财务流水

FinancialEntry 字段：

`date,time,type,walletId,amountMinor,categoryId,toWalletId,toAmountMinor,originalExpenseId,note`。

| type | walletId | categoryId | toWalletId／toAmountMinor | originalExpenseId |
| --- | --- | --- | --- | --- |
| INCOME | 入账钱包 | 必填，INCOME 分类（按当前钱包收入模式，或历史保留） | 均 null | null |
| EXPENSE | 支出钱包 | 必填，EXPENSE 分类（按当前钱包支出模式，或历史保留） | 均 null | null |
| TRANSFER | 转出钱包 | null | 均必填，目标钱包须不同 | null |
| REFUND | 退款到账钱包 | null，分类由原支出取得 | 均 null | 必填，指向 EXPENSE |

- amountMinor 必须为正；转账 toAmountMinor 也为正。
- 同币种转账两侧精度相同、金额相等；跨币种分别以两侧钱包的最小单位记录。
- 退款到账钱包币种与原支出钱包相同，累计退款不能超过原支出；不允许引用收入、转账或另一笔退款。
- 原支出不存在、删除原支出后留下退款、修改原支出后累计退款超额，均为错误。
- 退款不保存独立分类，以原支出的当前 categoryId 计算；导入、编辑和统计保持这一规则。校验新建／显式修改收支分类时，所选类别必须属于该钱包当前有效候选集（或全局兜底）；只修改金额、日期、备注等非类别字段时，保留原 categoryId，允许其为历史已停用／此前模式类别。
- 收入、支出、转账和退款都必须填写 date、time；转账两侧共用一组发生日期时间。新建时取表单打开时的本地当前日期和分钟，用户可修改；编辑或保存不重新取当前时钟覆盖。
- 不对业务日期时间强加“必须晚于 createdAt”等规则，用户可以补录历史记录。列表按 date、time 降序，再按 createdAt 降序、id 升序；业务日／月统计仍按 date。

对钱包 w 的余额贡献：

| 流水关系 | 余额变化 |
| --- | --- |
| INCOME.walletId = w | +amountMinor |
| EXPENSE.walletId = w | -amountMinor |
| REFUND.walletId = w | +amountMinor |
| TRANSFER.walletId = w | -amountMinor |
| TRANSFER.toWalletId = w | +toAmountMinor |

余额 = openingBalanceMinor ＋上述全部贡献。期间统计先按流水自身的 date 和所选钱包过滤，再按币种计算收入及净支出。收入只加 INCOME，净支出为 EXPENSE 减 REFUND，转账与初始余额不计收支。退款归属其 walletId 指向的到账钱包，分类从 originalExpenseId 关联的原支出读取，即使原支出不在所选日期或钱包范围内也适用。例：同币种 A 支出 100，B 退款到账 30，两笔都落入所选日期范围时，A、B、合选的净支出分别为 100、−30、70。分类统计须以 categoryId 分组，不把不同钱包同名专属类别或同名通用类别误合并；显示同名时补充所属钱包标签。归档钱包在当前钱包范围包含它时仍保留其统计贡献。默认钱包范围只选择 `separateAccounting=false` 的钱包（含归档的历史钱包）；用户显式选中独立核算钱包后才参与账本流水和统计，首页本月收支固定使用默认范围。显式选中的钱包 ID 集合与默认范围区分存储；没有常规钱包时默认范围为空，不自动扩大到独立钱包。

### 3.4 主题设置

ThemePreferences 为单例，没有业务 UUID：

| 字段 | 值 |
| --- | --- |
| themeMode | SYSTEM／LIGHT／DARK |
| colorSource | PRESET／CUSTOM／SYSTEM |
| presetId | apricot／sage／mist_blue／lavender／rose |
| customSeedColor | 大写 `#RRGGBB`，不透明 RGB |

初始值为 `SYSTEM, PRESET, apricot, #C47A4A`。presetId 与种子色对应：apricot=`#C47A4A`、sage=`#5F7F64`、mist_blue=`#587C9B`、lavender=`#8A729F`、rose=`#A76F82`。

即使当前选择系统取色，仍保存上次预设与自选色。恢复到不支持动态取色的系统时，保留 colorSource=SYSTEM，运行时按保存的预设回退，不改写偏好，也不自动生成一次备份。

v1 只导出以上主题偏好；计算出的整套色板、壁纸颜色、系统当前明暗、表单草稿、最近使用控件状态不进入 JSON。

### 3.5 CSV 导入凭据

ImportReceipt 字段：`kind,sha256,importedAt`，联合唯一键为 `(kind,sha256)`。

- kind 为 MEALS 或 LEDGER；sha256 为成功导入的原文件字节 SHA-256，小写 64 位十六进制字符串，包含原文件的 BOM 和换行差异。
- importedAt 为 UTC 时间；只有成功新增至少一条记录后，才在同一事务中保存凭据。
- 凭据随 JSON 迁移，用于恢复后识别同一个无 ID 文件。不同字节的文件不保证凭摘要去重，仍需 ID 与冲突校验。
- 不保存源绝对路径或原 CSV 内容；取消、失败及纯重复无新增的操作不创建凭据或新数据修订。

### 3.6 仅本机内部状态

以下对象不进入完整备份：

- 当前数据修订计数、备份任务代次、各目标的待处理和完成状态。
- 目录授权 URI、目标配置、连接状态、网络偏好、Token 与 Keystore 密钥。
- GitHub 文件 SHA、上传调度时间、冲突和暂停状态、在途上传信息、安装设备标识、临时快照文件与恢复暂存库。
- 业务库归属标记、迁移待确认状态及容量统计。这些状态和业务库均按 [Android 系统备份与设备迁移规则](../features/08-data-management.md#97-android-系统备份与设备迁移)排除在系统备份之外。
- GitHub 仓库容量提醒阈值、检查结果和提醒记录。这些设置与状态仅保存在本机。

每个启用目标跟踪待备份的最新本机修订、实际发布的 datasetId／snapshotId／revision 及其任务代次和重试状态。业务事务持久化新修订及待处理状态，待处理状态也可从已持久化的修订与完成状态推导。备份任务从数据库一致性读取完整数据和对应修订；未执行的中间修订可合并，生成的文件内容只对应实际导出的修订。恢复时先停止旧任务发布，生成新代次，旧任务的完成结果不得更新新代次的状态。

GitHub 另行持久化以下本机状态，调度和交互见 [GitHub 私有库规则](../features/09-backup.md#94-github-私有库)。

| 本机状态 | 用途 |
| --- | --- |
| 数据集 ID、仓库 ID、分支、路径及 `lastAcceptedRemoteSha` | 界定可更新的目标。基准分别表示未确认、已确认文件不存在或已确认的文件 blob SHA。切换目标或数据集时不得沿用旧基准。 |
| 合并窗口、最近一次写入和重试的计时状态 | 保存系统开机标识、单调计时起点及对应等待时长。最近一次写入按仓库、分支、路径记录，包含手动写入。记录使用 UTC，界面按 UTC+8 显示，设备重启后的计时按[GitHub 任务接续规则](../features/09-backup.md#任务接续计时与联网)重新核对。 |
| 任务代次、目标、数据集 ID、写入的远端前置条件、本机修订、snapshotId、内容 SHA-256、未确认写入状态 | 在写入可能发出前一并持久化，供超时、取消或进程终止后核对。内容 SHA-256 对实际发送的 JSON 原始字节计算。 |
| 冲突或恢复后暂停状态 | 在用户确认对应远端版本并成功发布前阻止自动上传。取消、重启或“立即备份”不解除暂停。 |
| 仓库容量提醒阈值、仓库 ID、最近有效容量（KB）与检查时间、检查状态、待提醒状态、上次已提示容量 | 阈值为本机设置，随 App 重启保留。读数和提醒按仓库 ID 归属。查询失败不使用零值替代。计量与去重见 [仓库容量提醒规则](../features/09-backup.md#仓库容量提醒)。 |

