# KitsuneApp v1 数据格式与兼容契约

更新日期：2026-11-18。状态：**首版设计契约，尚未发布、尚无数据库或导入导出实现**。

本文把 [PLAN.md](PLAN.md) 的数据需求具体化，供后续模型建立实体、序列化器、校验器、导入器和测试。示例为虚构数据，不能被标注为真实应用导出。格式在首次发布前可以协调修订并同步计划；一旦发布，须遵守本文的版本兼容规则。

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
- 新建或修改实体保留 createdAt、updatedAt；导出使用 UTC 的 `YYYY-MM-DDTHH:mm:ss.SSSZ`。导入允许 UTC `Z` 结尾的有效 ISO 时间，包括没有小数秒的形式，然后统一规范化。
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

普通业务实体共有 `id,createdAt,updatedAt`。以下列出其余字段；所有字段的序列化类型以第 4 节为准。

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

Wallet 字段：`name,normalizedName,currencyCode,currencyScale,openingBalanceMinor,sortOrder,archived`。

- 为使 CSV 的“钱包名称＋币种”能唯一映射，同一数据集内 `(normalizedName,currencyCode)` 唯一，包含已归档钱包；相同名称的不同币种钱包允许存在。
- normalizedName 只用于匹配，不作为外键；同名映射不明确时必须在预览阶段解决，不能随意选第一个钱包。
- 余额为派生值，不导出 currentBalance 或余额缓存，也不以缓存覆盖流水。
- 收支 Category 的 scope 分别为 INCOME、EXPENSE，每域恰好一个 isDefault=true 的未归档兜底项。初始显示名为“未分类”，引用和兜底判断都依据 ID／标志。
- isDefault=true 的分类不能删除或停用；其他已引用分类只能停用。名称在各自 scope 内唯一。

### 3.3 财务流水

FinancialEntry 字段：

`date,time,type,walletId,amountMinor,categoryId,toWalletId,toAmountMinor,originalExpenseId,note`。

| type | walletId | categoryId | toWalletId／toAmountMinor | originalExpenseId |
| --- | --- | --- | --- | --- |
| INCOME | 入账钱包 | 必填，INCOME 分类 | 均 null | null |
| EXPENSE | 支出钱包 | 必填，EXPENSE 分类 | 均 null | null |
| TRANSFER | 转出钱包 | null | 均必填，目标钱包须不同 | null |
| REFUND | 退款到账钱包 | null，分类由原支出取得 | 均 null | 必填，指向 EXPENSE |

- amountMinor 必须为正；转账 toAmountMinor 也为正。
- 同币种转账两侧精度相同、金额相等；跨币种分别以两侧钱包的最小单位记录。
- 退款到账钱包币种与原支出钱包相同，累计退款不能超过原支出；不允许引用收入、转账或另一笔退款。
- 原支出不存在、删除原支出后留下退款、修改原支出后累计退款超额，均为错误。
- 退款不保存独立分类，以原支出的当前 categoryId 计算；导入、编辑和统计保持这一规则。
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

余额 = openingBalanceMinor ＋上述全部贡献。期间统计先按流水自身的 date 和所选钱包过滤，再按币种计算收入及净支出。收入只加 INCOME，净支出为 EXPENSE 减 REFUND，转账与初始余额不计收支。退款归属其 walletId 指向的到账钱包，分类从 originalExpenseId 关联的原支出读取，即使原支出不在所选日期或钱包范围内也适用。例：同币种 A 支出 100，B 退款到账 30，两笔都落入所选日期范围时，A、B、合选的净支出分别为 100、−30、70。归档钱包仍保留其统计贡献。

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
- 业务库归属标记、迁移待确认状态及容量统计。这些状态和业务库均按 [PLAN 的 Android 系统备份与设备迁移规则](PLAN.md#97-android-系统备份与设备迁移)排除在系统备份之外。

每个启用目标跟踪待备份的最新本机修订、实际发布的 datasetId／snapshotId／revision 及其任务代次和重试状态。业务事务持久化新修订及待处理状态，待处理状态也可从已持久化的修订与完成状态推导。备份任务从数据库一致性读取完整数据和对应修订；未执行的中间修订可合并，生成的文件内容只对应实际导出的修订。恢复时先停止旧任务发布，生成新代次，旧任务的完成结果不得更新新代次的状态。

GitHub 另行持久化以下本机状态，调度和交互见 [PLAN 的 GitHub 私有库规则](PLAN.md#94-github-私有库)。

| 本机状态 | 用途 |
| --- | --- |
| 数据集 ID、仓库 ID、分支、路径及 `lastAcceptedRemoteSha` | 界定可更新的目标。基准分别表示未确认、已确认文件不存在或已确认的文件 blob SHA。切换目标或数据集时不得沿用旧基准。 |
| 合并窗口、最近一次写入和重试的计时状态 | 保存系统开机标识、单调计时起点及对应等待时长。最近一次写入按仓库、分支、路径记录，包含手动写入。UTC 时间用于显示和记录，设备重启后的计时按 PLAN 第 9.4 节重新核对。 |
| 任务代次、目标、数据集 ID、写入的远端前置条件、本机修订、snapshotId、内容 SHA-256、未确认写入状态 | 在写入可能发出前一并持久化，供超时、取消或进程终止后核对。内容 SHA-256 对实际发送的 JSON 原始字节计算。 |
| 冲突或恢复后暂停状态 | 在用户确认对应远端版本并成功发布前阻止自动上传。取消、重启或“立即备份”不解除暂停。 |

## 4. JSON v1

### 4.1 顶层结构

| 字段 | JSON 类型 | 规则 |
| --- | --- | --- |
| format | string | `kitsune-backup` |
| formatVersion | integer | `1` |
| datasetId | string | UUID |
| snapshotId | string | UUID，每份快照的唯一身份 |
| revision | string | 非负 64 位十进制整数，本机数据修订，不能用于跨设备比较新旧 |
| exportedAt | string | UTC 时间 |
| backupKind | string | AUTO／MANUAL／PRE_RESTORE |
| data | object | 下表的业务快照 |

| data 字段 | 内容 |
| --- | --- |
| categories | 全部 Category 对象数组，各 scope 共用此数组 |
| meals.methods | 全部 MealMethod 对象数组 |
| meals.records | 全部 MealRecord 数组，每条包含 preparationCategoryIds |
| ledger.wallets | 全部 Wallet 数组 |
| ledger.entries | 全部 FinancialEntry 数组 |
| settings.theme | ThemePreferences |
| importReceipts | 全部成功导入凭据数组 |

- 共用时间字段适用于方式、分类、用餐、钱包、流水；importReceipts 使用 importedAt，主题单例不含时间字段。
- 导出不含 normalizedName，导入重建；Category 包含 isDefault，Wallet 包含 currencyScale。
- backupKind=AUTO 用于更新各目标的最新完整备份，包括设置页的“立即备份”。MANUAL 用于手动导出，PRE_RESTORE 用于恢复前的独立保护文件，两者由用户自行保管。
- 全部实体数组按 id 升序输出；关联 ID 数组去重并按 ID 升序输出；凭据按 kind、sha256 排序。字段按本节示例的固定顺序序列化，便于比较历史。
- 输出 UTF-8、无 BOM、LF 换行和两空格缩进；读取允许 UTF-8 BOM 与 CRLF。拒绝重复 JSON 对象键、非法数字和非标准 JSON。
- v1 使用明确字段集合：缺失必需字段、多余未知字段、未知枚举或模块均报错，不静默丢弃。增加字段或模块时提升 formatVersion 并编写迁移。

### 4.2 完整且可校验的虚构示例

此示例包含下厨晚餐、CNY／JPY 钱包、支出、跨币种转账及部分退款。为减少重复，数组对象采用紧凑排版；正式导出按上节统一缩进。

```json
{
  "format": "kitsune-backup",
  "formatVersion": 1,
  "datasetId": "00000000-0000-4000-8000-000000000001",
  "snapshotId": "00000000-0000-4000-8000-000000000002",
  "revision": "7",
  "exportedAt": "2026-11-16T12:00:00.000Z",
  "backupKind": "MANUAL",
  "data": {
    "categories": [
      {"id":"20000000-0000-4000-8000-000000000001","scope":"PREPARATION","name":"炒菜","sortOrder":0,"archived":false,"isDefault":false,"createdAt":"2026-11-16T10:00:00.000Z","updatedAt":"2026-11-16T10:00:00.000Z"},
      {"id":"20000000-0000-4000-8000-000000000002","scope":"INCOME","name":"未分类","sortOrder":0,"archived":false,"isDefault":true,"createdAt":"2026-11-16T10:00:00.000Z","updatedAt":"2026-11-16T10:00:00.000Z"},
      {"id":"20000000-0000-4000-8000-000000000003","scope":"EXPENSE","name":"未分类","sortOrder":0,"archived":false,"isDefault":true,"createdAt":"2026-11-16T10:00:00.000Z","updatedAt":"2026-11-16T10:00:00.000Z"}
    ],
    "meals": {
      "methods": [
        {"id":"10000000-0000-4000-8000-000000000001","name":"下厨","sortOrder":0,"archived":false,"showPreparationCategories":true,"createdAt":"2026-11-16T10:00:00.000Z","updatedAt":"2026-11-16T10:00:00.000Z"},
        {"id":"10000000-0000-4000-8000-000000000002","name":"外卖","sortOrder":1,"archived":false,"showPreparationCategories":false,"createdAt":"2026-11-16T10:00:00.000Z","updatedAt":"2026-11-16T10:00:00.000Z"}
      ],
      "records": [
        {"id":"30000000-0000-4000-8000-000000000001","date":"2026-11-16","mealSlot":"DINNER","plannedAtHome":true,"mealMethodId":"10000000-0000-4000-8000-000000000001","preparationCategoryIds":["20000000-0000-4000-8000-000000000001"],"dishes":"番茄炒蛋、米饭","note":"少放盐\n饭后散步","createdAt":"2026-11-16T11:00:00.000Z","updatedAt":"2026-11-16T11:00:00.000Z"}
      ]
    },
    "ledger": {
      "wallets": [
        {"id":"40000000-0000-4000-8000-000000000001","name":"日常钱包","currencyCode":"CNY","currencyScale":2,"openingBalanceMinor":"10000","sortOrder":0,"archived":false,"createdAt":"2026-11-16T10:00:00.000Z","updatedAt":"2026-11-16T10:00:00.000Z"},
        {"id":"40000000-0000-4000-8000-000000000002","name":"旅行钱包","currencyCode":"JPY","currencyScale":0,"openingBalanceMinor":"0","sortOrder":1,"archived":false,"createdAt":"2026-11-16T10:00:00.000Z","updatedAt":"2026-11-16T10:00:00.000Z"}
      ],
      "entries": [
        {"id":"50000000-0000-4000-8000-000000000001","date":"2026-11-16","time":"18:30","type":"EXPENSE","walletId":"40000000-0000-4000-8000-000000000001","amountMinor":"1280","categoryId":"20000000-0000-4000-8000-000000000003","toWalletId":null,"toAmountMinor":null,"originalExpenseId":null,"note":"食材","createdAt":"2026-11-16T10:30:00.000Z","updatedAt":"2026-11-16T10:30:00.000Z"},
        {"id":"50000000-0000-4000-8000-000000000002","date":"2026-11-16","time":"18:40","type":"TRANSFER","walletId":"40000000-0000-4000-8000-000000000001","amountMinor":"1000","categoryId":null,"toWalletId":"40000000-0000-4000-8000-000000000002","toAmountMinor":"200","originalExpenseId":null,"note":"跨币种转账示例","createdAt":"2026-11-16T10:40:00.000Z","updatedAt":"2026-11-16T10:40:00.000Z"},
        {"id":"50000000-0000-4000-8000-000000000003","date":"2026-11-16","time":"18:50","type":"REFUND","walletId":"40000000-0000-4000-8000-000000000001","amountMinor":"280","categoryId":null,"toWalletId":null,"toAmountMinor":null,"originalExpenseId":"50000000-0000-4000-8000-000000000001","note":"部分退款","createdAt":"2026-11-16T10:50:00.000Z","updatedAt":"2026-11-16T10:50:00.000Z"}
      ]
    },
    "settings": {
      "theme": {
        "themeMode": "SYSTEM",
        "colorSource": "PRESET",
        "presetId": "apricot",
        "customSeedColor": "#C47A4A"
      }
    },
    "importReceipts": []
  }
}
```

校验后的预期结果：CNY 钱包余额 80.00（100.00−12.80−10.00＋2.80），JPY 钱包余额 200；当月 CNY 收入 0、净支出 10.00。示例转账金额仅说明双金额字段，不代表真实汇率。

### 4.3 导出、恢复与幂等性

- 导出从一个一致的数据库修订生成，不能分别读取模块导致用餐、钱包和流水来自不同时间。
- 本地自动备份写完后读回并校验与预期内容一致，再按 [PLAN 的本地目录规则](PLAN.md#93-本地目录)发布。内容摘要等发布标记独立于 JSON，备份文件保持 v1 字段集合。
- 本地自动备份的文件名由 UTC 日期时间和同秒递增序号组成，例如 `kitsune-auto-20261118T093015Z-0001.json`。snapshotId 在 JSON 中标识备份。每个数据集在同一配置目录保留最新成功发布的一份 AUTO。清理前验证归属与发布状态，并以明确确认的新发布备份作为保留对象。发布失败时保留上一份有效备份。
- 云端恢复从 GitHub 的 `backups/latest.json` 读取当前完整备份。按 [PLAN 的 GitHub 私有库规则](PLAN.md#94-github-私有库)锁定同一提交读取元数据和原始字节，经字节数和 Git blob SHA 校验后生成恢复摘要。Git blob SHA 用于远端版本校验，上传尝试另用原始 JSON 字节的内容 SHA-256。每个目标独立确认实际完成的任务代次及修订，期间产生的新修订仍待处理。
- 完整恢复会整体替换业务数据。先全量校验并展示摘要；有用户数据或已修改设置时，在恢复事务前生成并校验 PRE_RESTORE 文件，可写入已授权目录或通过系统文件选择器单次导出。新装空库及只有未修改初始默认数据的库可直接恢复。
- 恢复保留输入的业务 ID、归档标志、审计时间和导入凭据；不重新生成默认分类或改写历史金额精度。
- 本机修订继续递增，导入快照的 revision 仅作来源信息。恢复后保留导入的 datasetId，后续完整备份生成新的 snapshotId。
- 恢复事务为已绑定的 GitHub 目标持久化暂停上传状态。成功恢复云端快照时记录该快照的文件 SHA；用户另行确认更新远端并发布成功后才恢复自动上传。覆盖或重建仅适用于用户确认的远端 SHA 或文件缺失状态。
- 写入结果不明时先按第 3.6 节的尝试信息核对。目标、数据集、snapshotId 及内容 SHA-256 一致才确认该次实际发布的修订，并用远端文件 blob SHA 更新该代次的基准。远端仍符合原写入条件时，可退避后从最新修订重新生成完整快照并重试。其他远端状态变化按冲突处理。旧代次的结果仅用于核对远端，不更新当前代次的备份状态。

### 4.4 完整 JSON 的解析与暂存

| 首版解析限制 | 计量方式 |
| --- | --- |
| 64 KiB 单个字段 | JSON 键和标量 token 的原始 UTF-8 字节数，不含字符串两侧引号。扫描时累计，在创建完整字符串或转换数值前拒绝超限 token。 |
| 200,000 个数组元素 | 全文件累计，包含各实体数组及关联 ID 等嵌套数组的元素。添加元素前检查。 |
| 16 层嵌套 | 根对象为第 1 层，进入子对象或数组时检查。 |

- 本地与云端的完整 JSON 恢复均采用上述结构限制。本地文件的读取受可用暂存空间约束，可处理超过云端上传及恢复字节上限的文件。导出同样校验结构限制，超限时报告原因并保留上次成功的备份，避免生成无法恢复的文件。
- 流式解析时按记录或关联逐项读取，分批写入独立于业务库的磁盘暂存库。跨记录的唯一性、关联关系及累计金额用暂存库查询完成。校验前不得在内存中构建完整文档或实体图。解析循环响应取消，超限、空间不足或校验出错均不展示可确认的恢复摘要。
- 所有校验及恢复前保护完成后，才在业务库事务中从暂存库分批写入。提交前取消或写入失败时回滚事务。暂存文件及暂存库按 [PLAN 的超时与停止规则](PLAN.md#worker-超时与停止)清理。

### 4.5 完整 JSON 的保存约束

- 录入、更新、CSV 导入和恢复写库使用与完整 JSON 导出相同的编码规则。提交前按序列化后的 UTF-8 字节数校验参与导出的字段，包含引号、反斜线、换行等转义产生的字节增量，字段计数仍不含字符串两端的引号。JSON 结构由格式定义并遵守第 4.4 节的深度限制。
- 在业务事务内按新增、编辑或删除对导出数组元素的实际增减，校验提交后的累计总数。计入同一操作新增的分类、关联 ID 和导入凭据。总数和数据同事务更新，缺少可信计数时从一致的业务数据重算。字段或累计总数超限导致事务回滚，修订号及备份状态不变。
- 云端容量预警按同一序列化规则估算，计入快照信封、分隔符、转义和所有导出的数据。由受影响的实体及关联更新统计，全量恢复或统计缺失时重算。最终大小由生成快照时实际计量，并按 [PLAN 第 9.2 节](PLAN.md#92-最新完整数据的可靠性)处理。云端容量不参与业务提交和本地文件的结构校验。
- 若版本迁移时已有数据超出当前结构限制，保留原数据并提示无法为当前修订生成完整备份。允许查看及逐步降低超限项的修改，修改不得新增或加重其他超限项。恢复到限制内后按正常规则备份。

## 5. CSV v1

### 5.1 编码与结构

- 导出 UTF-8、无 BOM；导入接受可选 UTF-8 BOM，允许 LF 或 CRLF。
- 第一条 CSV 记录必须是完整表头；读取按表头名称映射，允许列顺序改变，拒绝重复列、缺列或未知列。导出采用下方固定顺序。
- 使用标准 CSV 引号规则：含逗号、双引号或换行的字段用双引号包裹，内部双引号写为两个双引号。
- 枚举按表中固定值填写；planned_at_home 仅接受 `true` 或 `false`，空白不是 false。
- 空字段表示没有值，不能把字面文本 `null` 当空值。制作分类空字段或 `[]` 均表示空数组。
- 名称字段使用与实体一致的规范化匹配；dishes／note 不去掉用户内容中的换行或首尾字符。
- amount 和 to_amount 为不带币种符号、千分位或指数的正十进制文本，按货币精度导出；超精度输入报错。
- CSV 只作为数据解析，不执行其中的公式。导入后的文本保持为文本，不使用电子表格引擎求值。

### 5.2 用餐列

| 列 | 必填与含义 |
| --- | --- |
| id | 导出必有 UUID；外部新记录可空，导入时生成 |
| date | 必填，YYYY-MM-DD |
| meal_slot | BREAKFAST／LUNCH／DINNER |
| planned_at_home | true／false，必填 |
| meal_method | 方式名称；planned_at_home=true 时必填，否则可空 |
| preparation_categories | 分类名称的 JSON 字符串数组；没有方式时必须为空 |
| dishes | 可选多行文本 |
| note | 可选多行文本 |

以下为有效的 meals-v1 示例，包含多行备注。示例 ID 仍是业务 ID，不是 CSV 行号。

```csv
id,date,meal_slot,planned_at_home,meal_method,preparation_categories,dishes,note
30000000-0000-4000-8000-000000000001,2026-11-16,DINNER,true,下厨,"[""炒菜""]",番茄炒蛋、米饭,"少放盐
饭后散步"
30000000-0000-4000-8000-000000000002,2026-11-16,LUNCH,false,,[],公司附近午餐,
```

第二条用餐的 planned_at_home=false 且方式为空是合法记录，不得补成外卖。CSV 中现有方式关闭分类开关但带有历史分类也可导入；方式为空而分类非空则报错。

### 5.3 记账列

| 列 | 必填与含义 |
| --- | --- |
| id | 导出必有 UUID；外部新记录可空 |
| date | 必填，YYYY-MM-DD |
| time | 必填，24 小时制 HH:mm，不接受秒、空值或非法时分 |
| type | INCOME／EXPENSE／TRANSFER／REFUND |
| wallet | 对应钱包名称；转账为转出钱包 |
| currency | 钱包币种，必须匹配映射钱包 |
| amount | 正金额的十进制文本 |
| category | 收支分类名称；收支为空时使用对应兜底；转账必须空；退款规则见下 |
| to_wallet | 仅 TRANSFER 必填 |
| to_currency | 仅 TRANSFER 必填，匹配目标钱包 |
| to_amount | 仅 TRANSFER 必填，同币种时必须等于 amount |
| original_expense_id | 仅 REFUND 必填，指向已有或同批支出 |
| note | 可选多行文本 |

- 收支名称在各自 scope 内匹配，未发现时在整批事务中创建新分类。
- 转账的 category 与 original_expense_id 必须空；非转账的三项 to_* 都必须空。
- 退款导出 category 为原支出分类名称，方便表格查看；导入可空，非空时必须匹配已解析的原支出分类，不单独创建或覆盖该分类。
- 同批被退款引用的支出必须提供 UUID；无 ID 的支出不能被其他行通过行号或备注隐式引用。

以下 ledger-v1 与 JSON 示例中的三条账目对应：

```csv
id,date,time,type,wallet,currency,amount,category,to_wallet,to_currency,to_amount,original_expense_id,note
50000000-0000-4000-8000-000000000001,2026-11-16,18:30,EXPENSE,日常钱包,CNY,12.80,未分类,,,,,食材
50000000-0000-4000-8000-000000000002,2026-11-16,18:40,TRANSFER,日常钱包,CNY,10.00,,旅行钱包,JPY,200,,跨币种转账示例
50000000-0000-4000-8000-000000000003,2026-11-16,18:50,REFUND,日常钱包,CNY,2.80,未分类,,,,50000000-0000-4000-8000-000000000001,部分退款
```

### 5.4 名称映射、重复与事务

1. 读取完整文件，计算字节摘要并解析所有行；CSV 内换行不应破坏逻辑行号，错误同时标注记录序号及可定位的源行。
2. 钱包按规范化名称＋币种匹配已有钱包；允许用户在预览中显式映射不同名称，币种不能不同。不自动创建带有猜测余额的钱包。
3. 方式按规范化名称匹配；未知方式先映射，或在设置中创建后重试，不能猜测分类开关。
4. 解析制作分类与收支分类，新名称准备为待创建项；预览阶段不写数据库。
5. 同一文件中重复 ID 直接报告错误。与现有数据 ID 相同且业务字段一致则跳过；内容不同则冲突，不以更新时间推断覆盖。
6. 业务字段比较包括日期、财务流水的 time、方式、分类集合、金额、钱包和文本；不比较 CSV 未包含的 createdAt／updatedAt。
7. 不同 ID 占用同一日期餐次是冲突。不同 ID 的同金额同备注账目仍可是真实独立账目，不以近似内容去重。
8. 无 ID 行在准备提交时生成 UUID；先通过 ImportReceipt 拦截相同文件的重复导入，不能声称可自动识别所有内容相近的外部文件。
9. 校验全部引用、存储金额范围及退款累计后，展示新增／跳过／错误数量；有错误则整批不提交。
10. 实际提交前在事务中重新检查当前数据库和第 4.5 节的保存约束，防止预览后发生修改。一次提交包括新分类、记录、导入凭据及新增修订，登记最新待备份状态。业务事务失败时全部回滚。

## 6. 校验、迁移与兼容

### 6.1 恢复前校验顺序

1. 编码、JSON／CSV 语法、format 与版本、字段集合、值类型。
2. UUID、日历日期、财务流水必填的 HH:mm 业务时间、UTC 审计时间、金额整数字符串、币种精度与枚举。
3. 每类 ID 唯一、规范化名称约束、日期餐次唯一、必要的兜底分类。
4. 方式、分类、钱包、转账和退款引用；检查类型、scope 和币种，不只检查 ID 存在。
5. plannedAtHome 条件、无方式不得有分类、转账双金额、单笔金额及初始余额的存储范围、退款累计。
6. 主题枚举、HEX、导入凭据；系统版本不支持动态颜色是运行时回退情形，不是拒绝整个备份的理由。
7. 校验全部成功后才展示可恢复摘要并进入恢复前备份与事务阶段。

### 6.2 版本政策

- Room schemaVersion 与 JSON formatVersion 独立递增，更新应用版本不自动要求更新两者。
- 2026-11-17 在尚未发布的 v1 设计中加入财务 time 和 ledger-v1 的 time 列；早期文档示例不是已发布格式。缺失时间应报错，不能自动填 00:00 或由 createdAt 推导。
- 首版目标是数据库 v1、JSON v1。当前没有历史已发布格式，不能声称兼容一个未存在的 v0 或旧 isCooked 数据。
- 已发布格式增加字段、模块或改变语义时提升 formatVersion，提供旧版本到当前版本的明确迁移和回归样例。
- 迁移在暂存数据上完成，全量校验成功后才写入业务库；不对源文件原地改写。
- 高于支持范围的版本、未知模块或无法迁移的数据应说明原因并拒绝，不能导入一半数据。
- 旧 CSV 模板支持情况独立记录；更换表头或语义后保留已发布版本的识别与转换，不靠猜列位置兼容。

## 7. 后续必须验证的格式场景

本节是实现验收清单，不表示测试已运行：

- [ ] 四种流水的 time 必填，00:00／23:59 有效，24:00、12:60、带秒或缺失值被拒绝；补录／编辑不覆盖发生时间，同分钟稳定排序，跨时区显示和跨月统计符合业务日期规则。
- [ ] JSON／CSV 时间往返一致，相同 ID 不同 time 报冲突，不静默补齐旧设计缺失值。
- [ ] JSON 示例解析、引用与余额结果正确；导出→恢复→导出保持业务数据等价。
- [ ] 超出 JavaScript 安全整数范围但在 Long 范围内的金额字符串往返不丢精度。
- [ ] CNY、JPY 和其他支持精度的金额往返正确，CSV 超精度被拒绝。
- [ ] 单笔存储边界和派生余额分开验证。删除抵消支出后，`9223372036854775807` 加 1 可精确派生并展示为 `9223372036854775808` 个最小单位。
- [ ] 跨月或跨钱包退款按自身日期及到账钱包筛选，并使用原支出分类。
- [ ] 分类名称中的引号、逗号、中文与多行备注正确经过 JSON 数组和 CSV 双层转义。
- [ ] plannedAtHome 缺失／null、是但方式为空、方式为空但有分类等错误被拦截；否且方式为空合法。
- [ ] 方式关闭分类开关、方式或分类归档后，历史引用仍能完整恢复。
- [ ] 转账两侧不完整、币种不符、退款悬空或超额整批失败，原数据库不变。
- [ ] 重复 ID、日期餐次冲突、同一无 ID 文件重复导入与同批退款引用行为符合规定。
- [ ] 连续提交可合并待备份状态，输出的完整 JSON 与标注的修订一致，恢复后包含期间的新增、修改和删除。
- [ ] 新 AUTO 发布后同目录、同数据集只保留最新一份，发布失败时上一份仍可恢复；MANUAL、PRE_RESTORE 和其他数据集文件可独立保留。
- [ ] 同一 UTC 秒重复生成自动备份时序号递增。验证换机复用目录、设备时钟回拨、同名创建冲突均可安全发布。
- [ ] 全新安装可直接恢复。有用户数据的设备通过单次导出生成 PRE_RESTORE 后才替换业务数据，且 GitHub 暂停至用户另行确认。
- [ ] 针对其他设备更新、远端缺失、数据集替换或目标切换，核对基准和显式确认是否生效。不同设备的 revision 不用于覆盖优先级。
- [ ] 模拟上传响应丢失，只有目标、数据集、快照标识和字节摘要都一致时认领成功。恢复后，尚未发出请求的旧任务不能继续发布，已发出请求的结果不能回写新代次。
- [ ] 云端备份的元数据和下载内容对应同一提交。文件字节数或 Git blob SHA 不符时拒绝生成恢复摘要，手动下载的历史 JSON 按本地文件恢复流程校验。
- [ ] 覆盖字段字节数、累计数组元素数、解析深度的上限与超限情况，包含中文和转义字符。超限导出、解析中止或暂存空间不足时，已有备份及原业务库保持可用。
- [ ] 同一字段在直接录入、CSV 导入和 JSON 导出使用一致的字节限制。计数纳入事务中新建的分类、关联和导入凭据，整批超限时不新增数据、修订或备份任务。验证云端容量预警与实际发布大小的区分。
- [ ] Token、目录 URI、Keystore 密钥与任务队列不出现在导出中；主题与导入凭据能迁移。
- [ ] 已发布版本迁移和较新版本拒绝行为有回归测试，缺失字段不会被不安全地默认成 false 或 0。
