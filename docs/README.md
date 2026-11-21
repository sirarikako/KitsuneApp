# KitsuneApp 设计文档导航

设计文档采用「功能规则 + 数据契约 + UI 源稿 + 开发交接」四层结构。每一条详细规则只在所属模块维护一次；顶层 PLAN / DATA_FORMAT 是可向后兼容的导航入口。

## 功能设计

| 功能 | 维护位置 |
| --- | --- |
| 导航、共用组件、动效 | [公共规范](features/01-common.md) |
| 首页 | [首页](features/02-home.md) |
| 用餐（含用餐方式和制作分类） | [用餐](features/03-meals.md) |
| 钱包、独立核算 | [钱包](features/04-wallets.md) |
| 收支、计算器、转账、退款、统计 | [记账](features/05-ledger.md) |
| 通用和钱包专属收入／支出分类 | [分类管理](features/06-categories.md) |
| 外观、色板、应用图标 | [外观](features/07-appearance.md) |
| JSON 恢复、CSV 与设备迁移 | [数据管理](features/08-data-management.md) |
| 本地及 GitHub 自动备份、冲突和容量提醒 | [备份](features/09-backup.md) |

## 数据契约

- [通用约定、实体模型](data/01-model.md)
- [JSON v1](data/02-json.md)
- [CSV v1](data/03-csv.md)
- [校验、版本迁移及格式验收](data/04-compatibility.md)

## 其他设计

- [Android 架构及事务约束](architecture/ANDROID.md)
- [跨模块验收计划](testing/ACCEPTANCE.md)
- [网页 UI 设计与预览](ui/README.md)
- [产品边界及开发阶段](PLAN.md)
- [数据契约旧链接入口](DATA_FORMAT.md)

## 修改约定

1. 只改具体功能所属源文档；关联处添加相对链接，不复制大段规则。
2. 修改实体或导入导出字段时，同步更新相应数据契约与对应验收场景。
3. 修改 UI 时编辑 `ui/src/`，再运行 `python docs/ui/build_preview.py`；生成的 `ui/kitsune-ui.fragment.html`、`ui/index.html`、`ui/manifest.json` 不手工编辑。
4. 文档跨文件引用采用相对链接；历史章节索引只负责导航，业务规范以模块正文为准。
5. 没有 Android 工程，不能把网页预览静态检查称作 APK 或设备测试。
