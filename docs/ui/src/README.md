# KitsuneApp UI 分模块源稿

此处文件由原 `kitsune-ui.fragment.html` **逐行原样拆出**，不改变任何 CSS、HTML 或 JavaScript 的实际执行内容。

- `00-shell.html`：应用容器。
- `styles/`：全局、公共组件、页面样式；样式声明的原有先后顺序保持不变。
- `pages/`：首页、用餐、账本、设置及表单的页面渲染片段。
- `app/`：状态／共享方法、动效、总渲染和交互事件。

这些 `.inc` 是按顺序拼接的**片段**，不是可单独执行的 ES Modules。引用了前后片段中定义的符号，修改时必须维持顺序；不要独立在浏览器加载。后续如果需要进一步模块化，应单独进行有行为回归保障的重构。

编辑 `src/` 后运行 `python docs/ui/build_preview.py`；它将按 `SOURCE_PARTS` 顺序生成兼容的 `kitsune-ui.fragment.html`、独立 `index.html` 与 `manifest.json`。不要手工编辑生成文件。

13 个 UI 片段按原始顺序组合，生成的 `kitsune-ui.fragment.html` 与拆分前内容一致。执行 `python docs/ui/build_preview.py` 后，兼容源稿、离线预览及 `manifest.json` 的 SHA-256 校验通过。浏览器视觉与交互回归仍未执行。
