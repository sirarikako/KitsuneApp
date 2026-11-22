# JSON v1 完整备份契约

## 4. JSON v1

### 4.1 顶层结构

| 字段 | JSON 类型 | 规则 |
| --- | --- | --- |
| format | string | `kitsune-backup` |
| formatVersion | integer | `1` |
| datasetId | string | UUID |
| snapshotId | string | UUID，每份快照的唯一身份 |
| revision | string | 非负 64 位十进制整数，本机数据修订，不能用于跨设备比较新旧 |
| exportedAt | string | 快照生成时刻，导出为固定 UTC+8 的 ISO 时间 |
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
- 所有 AUTO、MANUAL 和 PRE_RESTORE 导出均以固定 UTC+8 写入 exportedAt、createdAt、updatedAt、importedAt，例如 `2026-11-18T09:30:15.000+08:00`。业务日期与 HH:mm 为用户选择的值。导入接受 `Z` 或 `+08:00`，审计时间和凭据时间按同一实际时刻恢复为库内 UTC。恢复摘要按 UTC+8 显示 exportedAt。
- 导出不含 normalizedName，导入重建；Category 包含 isDefault 和 walletId；Wallet 包含 currencyScale、separateAccounting、incomeCategoryMode 和 expenseCategoryMode。完整 JSON 总是导出所有通用／专属分类、钱包和流水，与当前页的默认筛选或类别模式无关；CSV 全量导出同样不得套用页面默认钱包范围。
- backupKind=AUTO 用于更新各目标的最新完整备份，包括设置页的“立即备份”。MANUAL 用于手动导出，PRE_RESTORE 用于恢复前的独立保护文件，两者由用户自行保管。
- 全部实体数组按 id 升序输出；关联 ID 数组去重并按 ID 升序输出；凭据按 kind、sha256 排序。字段按本节示例的固定顺序序列化，便于比较历史。
- 输出 UTF-8、无 BOM、LF 换行和两空格缩进；读取允许 UTF-8 BOM 与 CRLF。拒绝重复 JSON 对象键、非法数字和非标准 JSON。
- v1 使用明确字段集合：缺失必需字段、多余未知字段、未知枚举或模块均报错，不静默丢弃。例外为本版首发前已保存的 v1 旧快照：Wallet 缺少 separateAccounting 时迁移为 false；缺少两个 CategoryMode 时各迁移为 DEFAULT；Category 缺少 walletId 时迁移为 null。只有这组已知旧字段享受缺省值，不容忍其他缺失或未知字段。首版正式发布后新增字段须提升 formatVersion 并编写迁移。

### 4.2 完整且可校验的虚构示例

此兼容示例采用旧版的 `Z` 时间，包含下厨晚餐、CNY／JPY 钱包、支出、跨币种转账及部分退款。为减少重复，数组对象采用紧凑排版；正式导出按上节统一缩进。

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
      {"id":"20000000-0000-4000-8000-000000000001","scope":"PREPARATION","walletId":null,"name":"炒菜","sortOrder":0,"archived":false,"isDefault":false,"createdAt":"2026-11-16T10:00:00.000Z","updatedAt":"2026-11-16T10:00:00.000Z"},
      {"id":"20000000-0000-4000-8000-000000000002","scope":"INCOME","walletId":null,"name":"未分类","sortOrder":0,"archived":false,"isDefault":true,"createdAt":"2026-11-16T10:00:00.000Z","updatedAt":"2026-11-16T10:00:00.000Z"},
      {"id":"20000000-0000-4000-8000-000000000003","scope":"EXPENSE","walletId":null,"name":"未分类","sortOrder":0,"archived":false,"isDefault":true,"createdAt":"2026-11-16T10:00:00.000Z","updatedAt":"2026-11-16T10:00:00.000Z"}
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
        {"id":"40000000-0000-4000-8000-000000000001","name":"日常钱包","currencyCode":"CNY","currencyScale":2,"openingBalanceMinor":"10000","sortOrder":0,"archived":false,"separateAccounting":false,"incomeCategoryMode":"DEFAULT","expenseCategoryMode":"DEFAULT","createdAt":"2026-11-16T10:00:00.000Z","updatedAt":"2026-11-16T10:00:00.000Z"},
        {"id":"40000000-0000-4000-8000-000000000002","name":"旅行钱包","currencyCode":"JPY","currencyScale":0,"openingBalanceMinor":"0","sortOrder":1,"archived":false,"separateAccounting":true,"incomeCategoryMode":"DEFAULT","expenseCategoryMode":"DEFAULT","createdAt":"2026-11-16T10:00:00.000Z","updatedAt":"2026-11-16T10:00:00.000Z"}
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

校验后的预期结果：CNY 钱包余额 80.00（100.00−12.80−10.00＋2.80），JPY 钱包余额 200；当月常规钱包范围仅统计 CNY 收入 0、净支出 10.00。JPY 旅行钱包标记独立核算，余额仍显示，手动选择后才进入流水和统计。示例转账金额仅说明双金额字段，不代表真实汇率。

### 4.3 导出、恢复与幂等性

- 导出从一个一致的数据库修订生成，不能分别读取模块导致用餐、钱包和流水来自不同时间。
- 本地自动备份写完后读回并校验与预期内容一致，再按 [本地目录规则](../features/09-backup.md#93-本地目录)发布。内容摘要等发布标记独立于 JSON，备份文件保持 v1 字段集合。
- 本地自动备份的文件名由 exportedAt 对应的 UTC+8 日期时间和同秒递增序号组成，例如 `kitsune-auto-20261118T093015+0800-0001.json`。旧 `Z` 名称仍可识别。snapshotId 在 JSON 中标识备份。每个数据集在同一配置目录保留最新成功发布的一份 AUTO。清理前验证归属与发布状态，并以明确确认的新发布备份作为保留对象。发布失败时保留上一份有效备份。
- 云端恢复从 GitHub 的 `backups/latest.json` 读取当前完整备份。按 [GitHub 私有库规则](../features/09-backup.md#94-github-私有库)锁定同一提交读取元数据和原始字节，经字节数和 Git blob SHA 校验后生成恢复摘要。Git blob SHA 用于远端版本校验，上传尝试另用原始 JSON 字节的内容 SHA-256。每个目标独立确认实际完成的任务代次及修订，期间产生的新修订仍待处理。
- 完整恢复会整体替换业务数据。先全量校验并展示摘要；有用户数据或已修改设置时，在恢复事务前生成并校验 PRE_RESTORE 文件，可写入已授权目录或通过系统文件选择器单次导出。新装空库及只有未修改初始默认数据的库可直接恢复。
- 恢复保留输入的业务 ID、归档标志、钱包独立核算标志、审计时间和导入凭据；不重新生成默认分类或改写历史金额精度。
- 本机修订继续递增，导入快照的 revision 仅作来源信息。恢复后保留导入的 datasetId，后续完整备份生成新的 snapshotId。
- 恢复事务为已绑定的 GitHub 目标持久化暂停上传状态。成功恢复云端快照时记录该快照的文件 SHA；用户另行确认更新远端并发布成功后才恢复自动上传。覆盖或重建仅适用于用户确认的远端 SHA 或文件缺失状态。
- 写入结果不明时先按[本机状态契约](01-model.md#36-仅本机内部状态)的尝试信息核对。目标、数据集、snapshotId 及内容 SHA-256 一致才确认该次实际发布的修订，并用远端文件 blob SHA 更新该代次的基准。远端仍符合原写入条件时，可退避后从最新修订重新生成完整快照并重试。其他远端状态变化按冲突处理。旧代次的结果仅用于核对远端，不更新当前代次的备份状态。

### 4.4 完整 JSON 的解析与暂存

| 首版解析限制 | 计量方式 |
| --- | --- |
| 64 KiB 单个字段 | JSON 键和标量 token 的原始 UTF-8 字节数，不含字符串两侧引号。扫描时累计，在创建完整字符串或转换数值前拒绝超限 token。 |
| 200,000 个数组元素 | 全文件累计，包含各实体数组及关联 ID 等嵌套数组的元素。添加元素前检查。 |
| 16 层嵌套 | 根对象为第 1 层，进入子对象或数组时检查。 |

- 本地与云端的完整 JSON 恢复均采用上述结构限制。本地文件的读取受可用暂存空间约束，可处理超过云端上传及恢复字节上限的文件。导出同样校验结构限制，超限时报告原因并保留上次成功的备份，避免生成无法恢复的文件。
- 流式解析时按记录或关联逐项读取，分批写入独立于业务库的磁盘暂存库。跨记录的唯一性、关联关系及累计金额用暂存库查询完成。校验前不得在内存中构建完整文档或实体图。解析循环响应取消，超限、空间不足或校验出错均不展示可确认的恢复摘要。
- 所有校验及恢复前保护完成后，才在业务库事务中从暂存库分批写入。提交前取消或写入失败时回滚事务。暂存文件及暂存库按 [Worker 超时与停止规则](../features/09-backup.md#worker-超时与停止)清理。

### 4.5 完整 JSON 的保存约束

- 录入、更新、CSV 导入和恢复写库使用与完整 JSON 导出相同的编码规则。提交前按序列化后的 UTF-8 字节数校验参与导出的字段，包含引号、反斜线、换行等转义产生的字节增量，字段计数仍不含字符串两端的引号。JSON 结构由格式定义并遵守[JSON 解析与暂存](#44-完整-json-的解析与暂存)中的深度限制。
- 在业务事务内按新增、编辑或删除对导出数组元素的实际增减，校验提交后的累计总数。计入同一操作新增的分类、关联 ID 和导入凭据。总数和数据同事务更新，缺少可信计数时从一致的业务数据重算。字段或累计总数超限导致事务回滚，修订号及备份状态不变。
- 单份云端备份文件的大小预警按同一序列化规则估算，计入快照信封、分隔符、转义和所有导出的数据。由受影响的实体及关联更新统计，全量恢复或统计缺失时重算。最终大小在生成快照时计量，并按 [完整数据可靠性规则](../features/08-data-management.md#92-最新完整数据的可靠性)处理。仓库整体容量则来自 GitHub 元数据。两种容量提示均独立于业务提交和本地文件的结构校验。
- 若版本迁移时已有数据超出当前结构限制，保留原数据并提示无法为当前修订生成完整备份。允许查看及逐步降低超限项的修改，修改不得新增或加重其他超限项。恢复到限制内后按正常规则备份。

