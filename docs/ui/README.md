# KitsuneApp UI 设计稿

保存日期：2026-11-17（Asia/Hong_Kong）。这是本轮讨论后的设计快照，供后续 Android 开发对照。

保留两套视觉：默认展示“杏橙手账”，“清爽日常”作为备选。两套方案使用相同的业务交互。业务、设计规则继续集中维护在 [PLAN.md](../PLAN.md)，数据契约以 [DATA_FORMAT.md](../DATA_FORMAT.md) 为准；本目录提供可查看、可编辑的视觉依据。

## 查看与维护

直接用现代 Edge 或 Chrome 打开 [index.html](index.html)，可离线查看，无需启动服务或安装 Android 工具链。预览底部可切换两套方案，手机界面内可切换首页、用餐、记账、设置。

| 文件 | 用途 |
| --- | --- |
| [index.html](index.html) | 独立预览，样式、交互和图标均已内嵌，可单独复制查看 |
| [kitsune-ui.fragment.html](kitsune-ui.fragment.html) | 当前对话设计稿的原始片段，后续修改界面的主要入口 |
| [preview.template.html](preview.template.html) | 独立预览外壳，来自 Visualize 1.0.103 导出；不含具体产品界面 |
| [build_preview.py](build_preview.py) | 使用 Python 标准库将片段和本地图标重新打包为独立预览 |
| [manifest.json](manifest.json) | 源稿、生成预览和图标库的 SHA-256，便于核对版本 |
| [vendor/lucide-1.8.0.min.js](vendor/lucide-1.8.0.min.js) | 离线图标库；原始许可证见 [LUCIDE-LICENSE](vendor/LUCIDE-LICENSE) |
| [screenshots](screenshots/) | 17 张关键页面截图，含新增的系统壁纸配色示意 |

修改源片段后重新生成预览；命令和已验证的工具版本统一记录在[项目 README](../../README.md#ui-设计预览)。不要只修改生成的 index.html，否则下次打包会覆盖修改。截图是设计快照，重新调整界面后也应更新对应截图。

原始片段保留对话中的可选设计调参接口；普通浏览器没有该侧栏，可通过应用内设置预览调色板与明暗。独立导出将长文本的悬停提示转换为浏览器原生提示，避免依赖在线定位库；点击记录始终可查看完整内容。

## 视觉与页面索引

当前源稿的手机画布最大宽度为 390 CSS px。“杏橙手账”的卡片圆角为 17 px，“清爽日常”为 13 px；紧凑筛选结果默认每行约 70 px。它们是视觉参考比例，Android 实现需要转换为合适的 dp／sp，并适配字体缩放和不同屏幕，不应直接把网页像素当作设备像素。

颜色和组件的实现以 PLAN 的 Material 3 主题规则为准。网页使用了近似的颜色混合，尚未采用 Android Material Color Utilities 的完整 Tonal Spot 色阶。

| 页面 | 当前快照 |
| --- | --- |
| 首页，浅色／深色／备选方案 | [杏橙首页](screenshots/home.png)、[杏橙深色](screenshots/home-dark.png)、[清爽首页](screenshots/home-simple.png) |
| 用餐按日记录 | [三餐记录](screenshots/meals-daily.png) |
| 用餐组合筛选与紧凑结果 | [筛选表单](screenshots/meal-filter.png)、[紧凑列表](screenshots/meal-results.png) |
| 用餐月度与全部记录统计 | [统计条形](screenshots/meal-statistics.png) |
| 记一笔与独立计算器弹层 | [紧凑表单](screenshots/entry.png)、[计算器](screenshots/calculator.png) |
| 账目详情与删除确认 | [详情](screenshots/entry-detail.png)、[删除确认](screenshots/delete-confirm.png) |
| 流水、统计与钱包范围 | [流水](screenshots/ledger-flows.png)、[按币种统计](screenshots/ledger-statistics.png)、[钱包选择](screenshots/wallet-filter.png) |
| 设置 | [设置页面](screenshots/settings.png) |
| 系统壁纸配色示意 | [配色来源与预览](screenshots/theme-system.png)、[应用后的首页](screenshots/theme-system-home.png) |

截图主要采用杏橙方案，固定演示日期为 2026-11-17；首页截图使用晚餐时段。完整的两套界面及当前时段推荐可以在交互预览中查看。

## 与正式开发的边界

| 范围 | 当前状态 |
| --- | --- |
| 四页导航、日期／月份入口 | 已在网页演示 |
| 用餐录入、编辑、删除确认、组合筛选、紧凑列表及统计条形 | 已在网页演示，数据只在当前页面内变化 |
| 钱包范围单选／多选、按币种统计、流水钱包标签 | 已在网页演示，钱包是固定的虚构样例 |
| 记一笔、计算器回填、账目详情和删除 | 已在网页演示，非正式财务运行时 |
| 预设／自选／系统壁纸配色与明暗 | 已在网页演示来源选择、草稿预览、应用／取消；系统壁纸色板为示意，尚未接入 Android 系统动态颜色 |
| 分类管理、钱包管理、转账、退款、导入导出、备份与恢复 | 尚无完整交互或真实业务实现；设置中多数条目仅展示布局 |
| 原创狐狸图标 | 已保留在 [artwork](../../artwork/README.md)；网页首页仍为 emoji 示意，新版接入与设备验收待完成 |
| Android／Room／WorkManager／APK | 尚未初始化或实现 |

演示没有登录功能，也不会连接 GitHub 或读写个人数据。演示记录、钱包余额及所有金额均为虚构样例；刷新后业务记录重新初始化，部分界面偏好可能由浏览器本地存储保留。

“跟随系统壁纸”在网页中使用固定示意色板，不读取电脑或手机壁纸；正式应用将使用 Android 12 及以上系统提供的 Material 动态颜色。配色来源和明暗设置独立，切换来源保留上次预设／自选色，只有应用配色后才更新全局颜色。具体规则仍以 PLAN 第 6 节为准。

日期、分类、钱包、ID 与金额的演示结构不是正式 JSON／CSV 数据格式。正式开发需按 DATA_FORMAT 建模，按 PLAN 完成转账原子性、退款保护、金额范围和精度、备份、分类生命周期等全部规则，不能把网页的演示限额、固定选项或简单数组当作完整实现。

## 实际验证

本次在无头 Edge 中以离线模式打开保存到项目内的 index.html，确认：

- 两套方案、图标及主要页面可显示，过程中没有 HTTP／HTTPS 请求，也没有浏览器脚本错误。
- 用餐筛选可应用并返回匹配结果，钱包范围可切换，计算器 `12+8×2` 回填 `28.00`。
- 演示账目可保存、查看和确认删除；明暗切换和方案切换正常。
- 新增主题入口后，离线检查两套方案的三种配色来源、预览／应用／取消、预设及自选色保留、HEX 校验，以及系统配色下的独立浅色／深色与跨页面应用；320／390 CSS px 下控件未溢出。
- 17 张截图来自保存的独立文件，已抽查首页、紧凑结果、计算器及系统壁纸配色入口等布局。
- 项目内源片段与本轮最终对话源稿逐字节一致；连续两次打包输出相同，校验清单中的哈希有效，文档使用 UTF-8 且相对链接已检查。

设计迭代时还检查过 320／390 CSS px 下的布局、筛选组合、按币种汇总及删除目标一致性。当前验证属于网页设计预览检查，不代表 Android 编译、单元测试、无障碍验收、大字体／键盘、真机或数据集成验证。
