# Trae Watch

TRAE 手机客户端的第三方手表端实现。为 **410x502 方屏 / Android 8.1 / 低性能设备** 设计。

## 它能做什么

- 查看 Agent 会话列表与设备在线状态
- 查看会话消息流（只显示文本与工具调用摘要）
- 接收任务状态通知
- 审批 Agent 的工具调用请求（同意 / 拒绝）
- 三档文字输入：预设短语、历史复用、手输

不做：语音、代码预览、新建会话、完整设置页。

## 技术选型

| 项 | 选择 | 理由 |
|---|---|---|
| UI | Compose + material3，自建 M3E 令牌层 | Wear Compose M3 的圆形组件在方屏上违和，只复用 M3E 的 token 语言 |
| 存储 | 手写 SQLiteOpenHelper | 只有两张表，避开 Room 的注解处理与启动开销 |
| 网络 | OkHttp | SSE 只需读流，800KB 依赖可接受 |
| Token | Keystore AES-GCM + 串行刷新 | refresh_token 是轮换制，并发刷新会导致失效 |
| 后台 | 前台服务 + JobScheduler 兜底 | 国产 ROM 杀后台激进，双保险 |

## 性能约束（写进代码）

- 单屏 Composable 节点 ≤ 150
- 无循环动画，只有页面转场与按压反馈
- LazyColumn 固定 key，消息分页 40 条
- 无图片资源，头像用纯色圆 + 首字母
- 消息文本超 800 字符截断
- 启动路径不碰数据库、不发网络请求

## 构建

推送到 GitHub 后由 Actions 自动构建，产物在 Actions 页面的 Artifacts 里。

本地构建需要 JDK 17 + Android SDK 34：

```bash
./gradlew :app:assembleRelease
```

## 配对

手表上无法登录（手机验证码链路带滑块风控），凭证从手机端导入。

```bash
# 从 GameViewer 导出的 Keva 文件生成配对信息
python pair/pair.py --zip "default.zip"

# 或从已解压目录
python pair/pair.py --dir ./keva
```

脚本会启动一个局域网服务并打印 6 位配对码，在手表上输入 IP + 端口 + 配对码即可。

二维码可选，需要 `pip install qrcode`。

## 接口来源

全部来自对 `com.bytedance.trae.cn` v0.0.25 的静态分析与实测验证：

```
POST https://api.trae.cn/cloudide/api/v3/trae/oauth/ExchangeToken
     {"RefreshToken","ClientID":"ono9krqynydwx5","ClientSecret":"-"}
     -> Token(JWT, 15天) + 新 RefreshToken

GET  https://trae-api-cn.mchost.guru/api/solo_hub/v1/clis
GET  https://trae-api-cn.mchost.guru/api/solo_hub/v1/conversations/messages/anchor
GET  https://trae-api-cn.mchost.guru/api/solo_hub/v1/chat_sessions/{id}/events   (SSE)
POST https://trae-api-cn.mchost.guru/api/solo_hub/v1/conversations/{id}/messages
POST https://trae-api-cn.mchost.guru/api/solo_hub/v1/conversations/user_decision
```

鉴权头：`Authorization: Cloud-IDE-JWT <token>`

## 风险

- 非官方客户端，接口随时可能变更
- 凭证等同账号密码，勿泄露
- 手表端 token 存 Keystore 加密，但 rooted 设备仍可被 dump
