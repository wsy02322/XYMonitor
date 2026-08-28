# XYMonitor

闲鱼指定卖家上新监控。填写 `userId`，按 A～B 秒之间的随机间隔拉取卖家主页第一页，**只比较普通列表第一件 `item_id`**：

- 第一次成功：只记第一件 ID，不响铃
- 之后第一件 ID 变了：播放你选择的提示音（未选则用默认）
- 巡检失败：滴一声（可关）+ 通知 + 弹窗

忽略置顶。重新上架并顶到列表第一件时，只要 ID 和上次第一件不同就会提醒。

## 下载

Debug APK：

https://github.com/wsy02322/XYMonitor/raw/cursor/xianyu-monitor-5624/dist/xymonitor-debug.apk

## 使用

1. 用 Android Studio 打开本仓库，或执行 `./gradlew assembleDebug`
2. 安装 `app/build/outputs/apk/debug/app-debug.apk`
3. 填写卖家 `userId`（卖家主页链接里的数字）
4. 可选：选择上新提示音、设置出错是否发声、填写间隔 A/B 秒
5. 点「开始监控」，允许通知，并忽略电池优化

卖家主页示例：`https://www.goofish.com/personal?userId=1666703902`

## 说明

- 只监控 **一个** userId，只看 **第一页列表第一件**
- 每次等待在 A～B 秒之间按毫秒随机，避免固定节奏
- 前台服务保活，适配国产机常见后台限制
- 通过闲鱼 H5 接口取数，不登录、不打开闲鱼 App
