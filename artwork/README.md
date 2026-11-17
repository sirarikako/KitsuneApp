# Kitsune 狐狸图标

2026-11-17 使用内置 imagegen 生成原创透明前景，原始 PNG 保存在 `fox-foreground.png`，运行时副本为 `app/src/main/res/drawable-nodpi/fox_artwork.png`。原图未裁切、未后处理。Android layer-list 负责缩放与留白，背景是独立奶油色层。单色层为手绘 VectorDrawable，使用负空间保留耳朵、脸颊、眼睛、鼻子与微笑。

彩色与单色 adaptive icon 均为 108dp，关键轮廓置于中央 66dp 安全区。实际圆形、圆角方形遮罩和启动器主题图标仍需设备验收，不能仅凭源图断言已验证。

生成提示词：

> Create the original Android app icon foreground for KitsuneApp: a single friendly front-facing warm orange cartoon fox head, rounded face, pointed ears, cream cheeks and muzzle, small dark nose, friendly curved eyes and slight smile. Flat vector-like illustration with crisp edges, no gradients, text, letters, watermark, props, shadows, border or rounded-square container. Transparent background. Square 1024x1024 canvas; the ENTIRE fox including ears must fit within the central 60 percent of the canvas (roughly x=205..819 and y=205..819), leaving ample fully transparent margins on every side for adaptive icon masks. Symmetrical simple silhouette, recognisable at small size, warm orange #DD8545, cream #FFF3E2 and dark brown facial features. Deliver a high resolution transparent PNG foreground layer.

生成结果为 1254×1254 PNG。模型结果带有轻微色彩层次，最终接入图层用额外留白适配安全区。所有图形为 KitsuneApp 自有原创资产。
