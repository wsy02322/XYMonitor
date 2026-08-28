# XYMonitor

闲鱼指定卖家上新监控。填写 `userId`，每 3 分钟拉取卖家主页第一页，对比 `item_id`：

- 第一次成功：只记当前商品，不响铃
- 之后出现新 ID：播放提示音
- 巡检失败：播放另一种提示音

## 使用

1. 用 Android Studio 打开本仓库，或执行 `./gradlew assembleDebug`
2. 安装 `app/build/outputs/apk/debug/app-debug.apk`
3. 填写卖家 `userId`（卖家主页链接里的数字）
4. 点「开始监控」，允许通知，并忽略电池优化

卖家主页示例：`https://www.goofish.com/personal?userId=1666703902`

## 说明

- 只监控 **一个** userId，只看 **第一页**
- 前台服务保活，适配国产机常见后台限制
- 通过闲鱼 H5 接口取数，不登录、不打开闲鱼 App
