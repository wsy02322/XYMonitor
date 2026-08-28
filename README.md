# XYMonitor

闲鱼指定卖家上新监控。**2.0 起闲鱼由 VPS 拉取**，手机不再后台打闲鱼。

规则没变：只监控一个数字 `userId`，忽略置顶，只比普通列表第一件 `item_id`。第一次成功只记 ID 不提醒；之后第一件变了才提醒。

## 推送方案对比和建议

侧载、国产机、要本机循环震动和自选铃声。这几条一起看：

| 方案 | 熄屏送达 | 铃声 / 循环震 | 成本 | 建议 |
|---|---|---|---|---|
| 极光 / 个推 | 国内侧载最稳的真推送 | 唤醒 App 后再走现有 `ChangeAlert` | 要开发者账号和 SDK | **下一步的主推送** |
| 荣耀 / 华为推送 | 这台荣耀最贴系统 | 同上 | 开发者 + 签名，重 | 可后做 |
| FCM | 国内无 GMS 基本废 | — | Google | 不当主通道 |
| 手机常驻 WebSocket 连 VPS | 熄屏同样可能被掐 | 需要连着 | 无第三方 | 不当主通道 |
| 闹钟拉自己的 VPS `/pending` | 闹钟已证实能叫醒；小 JSON 比打闲鱼有希望 | 完全复用本机提醒 | 无第三方 | **现在就做，v1 唯一通道** |
| Telegram Bot | 极稳 | 不能替代 App 铃声循环震 | Bot token | **可选备用**，VPS 顺手发一条 |

**现在采用：VPS 轮询闲鱼 + 手机闹钟问 `/pending` + 可选 Telegram。** 邀请码、极光/个推以后再做。

不在 v1 接厂商推送，是因为那不是「最精简核心」：要注册、AppId、打包。闹钟拉自己的服务器已经够跑通检测和提醒。

## 怎么跑

### 1. VPS

Python 3.10+，标准库，无额外依赖。

```bash
cd server
export XY_TOKEN='自己设一个密钥'
# 可选：Telegram 备用
# export TELEGRAM_BOT_TOKEN=...
# export TELEGRAM_CHAT_ID=...
python3 app.py
```

默认 `http://0.0.0.0:8787`。没设 `XY_TOKEN` 时会生成并写入 `server/.token`。

防火墙放行 8787。安全组只给自己手机 IP 也能用。

### 2. App

1. 安装 Debug APK，或 `./gradlew assembleDebug`
2. 填服务器地址，例如 `http://你的VPS:8787`
3. 填和 VPS 相同的密钥
4. 填卖家 `userId`（主页链接里的数字）
5. 间隔 A～B 是**服务器打闲鱼**的间隔（默认 180～240 秒）
6. 点开始监控，允许通知、精确闹钟、忽略电池优化

手机用闹钟约 30～50 秒问一次服务器有没有待提醒。上新仍是循环震动 + 所选提示音；出错只震不放音乐。

卖家主页示例：`https://www.goofish.com/personal?userId=1666703902`

## 下载

Debug APK：

https://github.com/wsy02322/XYMonitor/raw/cursor/xianyu-monitor-5624/dist/xymonitor-debug.apk

## 说明

- 只监控 **一个** userId，只看 **第一页列表第一件**
- 服务器按 A～B 毫秒随机打闲鱼；手机只问自己的服务器
- 密钥不是邀请码，只是防止接口裸奔。邀请码后续再做
- 通过闲鱼 H5 接口取数，不登录、不打开闲鱼 App
