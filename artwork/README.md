# Kitsune 狐狸图标

2026-11-17：旧版应用实现因 UI 重新设计而放弃，仅保留本目录中的狐狸图标资产。使用内置 imagegen 生成的透明源图为 [fox-foreground.png](fox-foreground.png)，Android 运行时副本为 [android/res/drawable-nodpi/fox_artwork.png](android/res/drawable-nodpi/fox_artwork.png)。原图未裁切、未后处理。Android layer-list 负责缩放与留白，背景是独立奶油色层。单色层为手绘 VectorDrawable，使用负空间保留耳朵、脸颊、眼睛、鼻子与微笑。

彩色与单色 adaptive icon 均为 108dp，关键轮廓置于中央 66dp 安全区。实际圆形、圆角方形遮罩和启动器主题图标仍需设备验收，不能仅凭源图断言已验证。

## 文件与接入

| 文件 | 用途 |
| --- | --- |
| [fox-foreground.png](fox-foreground.png) | 1254×1254 透明 PNG 原图，保留生成来源 |
| [android/res/drawable-nodpi/fox_artwork.png](android/res/drawable-nodpi/fox_artwork.png) | 与原图逐字节相同的 Android 资源副本 |
| [android/res/drawable/fox_foreground.xml](android/res/drawable/fox_foreground.xml) | 自适应彩色前景，保留缩放与安全区留白 |
| [android/res/drawable/fox_monochrome.xml](android/res/drawable/fox_monochrome.xml) | 独立单色狐狸，保留五官与负空间 |
| [android/res/values/icon_colors.xml](android/res/values/icon_colors.xml) | 独立奶油色背景 `#FFF3E2` |
| [android/res/mipmap-anydpi/ic_launcher.xml](android/res/mipmap-anydpi/ic_launcher.xml) | API 26+ 自适应图标 |
| [android/res/mipmap-anydpi-v33/ic_launcher.xml](android/res/mipmap-anydpi-v33/ic_launcher.xml) | API 33+ 单色主题图标入口 |
| [SHA256SUMS](SHA256SUMS) | 源图与 Android 资源的内容摘要 |

将 `android/res/` 的内容复制到新工程的 `app/src/main/res/`（或实际模块的资源目录）。在新工程的 `<application>` 中设置：

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher"
```

这些资产沿用 API 26 最低版本的设计；若新工程降低最低版本，需要另行补充旧启动器资源。当前目录是资源包，不包含 Android 工程、旧页面、业务代码或安装包。彩色源图、Android 前景／单色层及图标入口的内容均保留原样；独立背景色从旧主题文件中提取。

验证：已比对保留文件与旧版本提交 `6fdff7163d1ead534d2a279705766dc7eb0f1e43` 的内容摘要，确认 PNG 未重新编码、透明通道存在，XML 可解析且内部资源引用完整。实际新 UI 的图标接入和启动器显示需在新工程中验收。

## 生成提示词


> Create the original Android app icon foreground for KitsuneApp: a single friendly front-facing warm orange cartoon fox head, rounded face, pointed ears, cream cheeks and muzzle, small dark nose, friendly curved eyes and slight smile. Flat vector-like illustration with crisp edges, no gradients, text, letters, watermark, props, shadows, border or rounded-square container. Transparent background. Square 1024x1024 canvas; the ENTIRE fox including ears must fit within the central 60 percent of the canvas (roughly x=205..819 and y=205..819), leaving ample fully transparent margins on every side for adaptive icon masks. Symmetrical simple silhouette, recognisable at small size, warm orange #DD8545, cream #FFF3E2 and dark brown facial features. Deliver a high resolution transparent PNG foreground layer.

生成结果为 1254×1254 PNG。模型结果带有轻微色彩层次，最终接入图层用额外留白适配安全区。所有图形为 KitsuneApp 自有原创资产。
