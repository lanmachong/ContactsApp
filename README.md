# 本地加密通讯录（ContactsApp）

一个**纯本地、无网络、无账号**的安卓应用。把手机通讯录、短信、通话记录搬进一个加密的本地库，
需要时再导回系统；配套来电识别悬浮窗，陌生号码也能看出是谁。

> 目标很朴素：**别让流氓软件读到你的联系人。** 系统通讯录、短信、通话记录对任何拿到权限的
> App 都是敞开的；搬进自己加密的库之后，那些 App 读到的只是一片空。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 功能

| 功能 | 说明 |
| --- | --- |
| 通讯录备份 | 导入系统通讯录、加密入库、清空系统、可导出回系统、CSV 备份 |
| 短信备份 | 导入系统短信、加密入库、查看全文、导出 CSV、可清空系统 |
| 通话记录备份 | 导入系统通话记录、加密入库、查看号码时长、导出 CSV、可清空系统 |
| 加密存储 | 整条记录 AES-256-GCM 加密，密钥由 Android Keystore 保管 |
| 来电识别 | 振铃时弹悬浮窗显示来电人姓名，只识别不拦截 |
| 重复标记 | 重复号码自动打「重复」标签，搜号码即可定位 |
| 纯本地 | 没有账号、没有服务器、没有埋点、不联网 |

## 技术栈

| 项 | 选型 |
| --- | --- |
| 语言 / UI | Kotlin + Jetpack Compose (Material 3) |
| 存储 | SQLite，逐行存密文 BLOB |
| 加密 | AES-256-GCM，密钥在 Android Keystore（不落盘、不可导出） |
| 最低版本 | Android 8.0（API 26），目标 API 34 |
| 许可 | MIT |

## 加密设计

数据库 `contacts.db` 里每张表都只有两列：

```sql
contacts(id INTEGER PRIMARY KEY, payload BLOB)
sms(id      INTEGER PRIMARY KEY, payload BLOB)
call_log(id INTEGER PRIMARY KEY, payload BLOB)
```

`payload` 是整条记录 JSON 的密文：

```
[1 字节版本][12 字节随机 IV][密文 + 16 字节 GCM Tag]
```

也就是说，即使拿到 db 文件，也看不到任何姓名、号码、短信内容、备注，
**连字段名都没有**。解密密钥由系统 Keystore 生成，绑定设备，无法导出。

> 没有用 SQLCipher：它的 API 与系统 SQLite 不兼容、要求更高 compileSdk，且换来的
> 安全强度与「整行加密」相当（两者都保护数据文件，都不防已 root 且能调用 Keystore 的攻击者）。

### 两级密钥

- **联系人表**直接用 Keystore 里的主密钥加密。几百条数据，这个开销可以接受。
- **短信 / 通话记录表**走一层「会话密钥」：随机生成 32 字节软件密钥，用主密钥包裹后落盘，
  进程内解开放内存。原因是这两张表动辄几千上万条，逐条走硬件密钥会慢到十几秒。
  根信任始终在 Keystore —— 主密钥没了，落盘的会话密钥也解不开。

## 导入与清理的边界

- 枚举以 `Contacts` 表为驱动，`Phone` 表只用来补号码 —— 否则「只有姓名/邮箱、没有号码」的联系人会被整条漏掉，导致导入条数比系统里少、清理后还剩下这些人
- 导入时记录每个联系人名下**全部** `rawContactId`（一个人常由本机 + 云账号 + SIM 多条聚合而成，只记一条删不干净）
- 去重依据是 `rawContactId` 而不是号码，同号但是两条真实记录的不会被吞掉
- **重复导入不是跳过，而是比对更新**：系统里改了姓名或补了号码，再导入一次就能同步进库；以前直接跳过，导致「号码从姓名栏挪到电话栏」后库里那条永远是空的
- 同一 `rawContactId` 在库里出现多条（多次导入产生的副本）时，自动合并保留最新一条
- 姓名栏里误填成号码的脏数据，导入时自动把号码救回电话字段，否则按号码永远搜不到
- 号码搜索做了数字归一化：`+86 138-0013-8000`、`138 0013 8000`、`13800138000` 互相能搜到，后 7 位一致也算命中
- **清理 = 系统全量删除**：重复的、没有号码的、导入后新增的，一次清干净；删完回读实际人数，有残留自动再删一轮
- 删除前强制弹出风险确认：云端账号会同步删除、系统回收站只留 30 天
- 提供 CSV 备份导出（明文，需自行保管）

### 系统权限的现实限制

Android 对这几个权限有硬性约束，代码里如实反映，不假装能删：

| 操作 | 权限 | 实际能否做到 |
| --- | --- | --- |
| 读通讯录 | `READ_CONTACTS` | ✅ 运行时授权即可 |
| 删通讯录 | `WRITE_CONTACTS` | ✅ 普通权限，授权即可 |
| 读短信 | `READ_SMS` | ✅ 运行时授权即可 |
| 删短信 | `WRITE_SMS` | ⚠️ signature 级权限，普通应用拿不到；**设为默认短信应用**后才可删 |
| 读通话记录 | `READ_CALL_LOG` | ✅ 运行时授权即可 |
| 删通话记录 | `WRITE_CALL_LOG` | ❌ signature\|privileged 权限，普通应用永远拿不到，只能在自带电话 App 里手动清 |

## 重复联系人标记

- 判定同时覆盖两个来源：**系统通讯录里同一个号码出现在两个不同联系人上**，以及**本应用里同一号码出现多次**
- 命中的联系人自动打上「重复」标签（标签是普通文本，和你自己写的标签混在一起，用中文逗号分隔）
- 搜号码、搜「重复」都能定位到；系统通讯录里的重复卡片需要用户自己去系统里删 —— 本应用不替他改系统数据
- 入口：右上角菜单「检测重复联系人」，或「导入与清理」页的「检测重复」按钮；每次导入结束也会自动跑一遍

## 导出回系统

- 「导入与清理」页把本应用里的联系人写回系统通讯录，用 `ContentProviderOperation` 批量提交
- **按号码去重**：系统里已经有这个号码就跳过，不会写出重复卡片
- 短信支持导出回系统（按「地址 + 正文 + 时间」去重）
- 卸载前务必先做这一步：卸载会连同 Keystore 密钥一起清除，库文件就再也解不开了

## 启动性能

加密存储的固有代价：打开 App 必须逐条解密（几百条就是几百次硬件密钥操作）。

- `ContactsRepository` 加了**内存缓存 + 互斥加载**，冷启动只解密一次。原来 Application 的来电索引预热和 ViewModel 的列表刷新各解一遍，等于白等一倍
- 解密**并发执行**（8 路），比串行快 3～4 倍
- 短信 / 通话记录表走会话密钥（软件 AES-GCM），且**分页加载**（每页 200 条），几千条也不会卡住首屏
- Keystore 密钥在 `Application.onCreate` 就预热，和界面初始化并行
- 首屏在解密完成前显示「正在解密本地通讯录…」，而不是空白页
- `release` 变体开启了 R8 + 资源压缩，包体更小、冷启动更快，**建议装 release 包**

## 来电识别实现路径

| Android 版本 | 号码来源 | 需要的授权 |
| --- | --- | --- |
| 10+ | `CallScreeningService` | 授予「来电显示和骚扰拦截」角色 |
| 8.0–9 | `PhoneStateListener` | `READ_PHONE_STATE` |

悬浮窗本身用 `WindowManager` + `TYPE_APPLICATION_OVERLAY`，需要「显示在其他应用上层」权限。
卡片不可获得焦点，不会抢走系统来电界面的触控。挂断后由前台服务撤掉。

本应用**只识别、不拦截**：`onScreenCall` 始终放行，不静默、不拒接、不录音、不上传。

## 构建

需要 JDK 17 + Android SDK 34。用 Android Studio 打开项目根目录即可直接构建。

命令行：

```bash
export JAVA_HOME="<你的 jdk-17 路径>"
export ANDROID_HOME="<你的 android sdk 路径>"

./gradlew assembleRelease   # 推荐：R8 压缩，启动更快
./gradlew assembleDebug
```

产物：

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

> `release` 变体当前沿用 debug 签名，方便直接覆盖安装调试。正式分发前请换成自己的签名配置。

## 首次使用流程

1. 打开 App → 右上角菜单 → **从系统通讯录导入** → 授予通讯录权限 → 开始导入
2. 导入完成后先 **导出 CSV 备份**，确认数据完整；重复联系人会自动带上「重复」标签
3. 需要还原数据时用 **导出到系统通讯录**；确认系统里不需要了，再点 **清空系统通讯录**
4. 右上角菜单 → **短信备份** / **通话记录备份** → 授予对应权限 → 导入
5. 右上角菜单 → **来电识别悬浮窗** → 依次授予通知、悬浮窗、「来电显示和骚扰拦截」
6. 打开「开启来电识别」开关，用「预览悬浮窗效果」验证样式

## 目录

```
app/src/main/java/com/wb/contacts/
├── ContactsApplication.kt      # 启动时预热 Keystore 与来电索引
├── MainActivity.kt             # 单 Activity 承载 Compose
├── data/
│   ├── Crypto.kt               # AES-256-GCM 主密钥加解密
│   ├── SessionCipher.kt        # 会话密钥（主密钥包裹的软件密钥）
│   ├── SecureTable.kt          # 通用加密表：分页 + 并行解密 + 缓存
│   ├── Contact.kt              # 联系人实体 + JSON 序列化
│   ├── SmsMessage.kt           # 短信实体
│   ├── CallRecord.kt           # 通话记录实体
│   ├── ContactsDbHelper.kt     # 三张同构密文表
│   ├── ContactsRepository.kt   # 联系人仓储
│   └── ArchiveRepository.kt    # 短信 / 通话记录仓储
├── system/
│   ├── SystemContacts.kt       # 系统通讯录读 / 删 / 导出回系统 / CSV
│   ├── SystemSms.kt            # 系统短信读 / 删 / 导出回系统 / CSV
│   └── SystemCallLog.kt        # 系统通话记录读 / 删 / CSV
├── call/
│   ├── CallerIndex.kt          # 号码 → 联系人内存索引
│   ├── CallScreeningServiceImpl.kt  # 来电号码来源（Android 10+）
│   ├── CallIdService.kt        # 前台服务 + 通话状态
│   ├── CallOverlay.kt          # 悬浮窗视图
│   └── CallNotifier.kt         # 通知渠道
├── ui/                         # Compose 页面
└── util/                       # 拼音、号码归一化、权限、开关
```

## 隐私声明

- 无网络权限（除来电识别所需的系统组件），不联网上报任何数据
- 无账号体系，无统计 SDK，无埋点
- 所有数据（含密钥）只在这台手机上
- 卸载 App 会清除密钥，**卸载前请先导出**

## 免责声明

本项目按「现状提供」交付。使用者需自行确保数据备份合规、遵守当地法律法规，
以及所在设备厂商 ROM 的相关政策。作者不对数据丢失承担赔偿责任。

## 贡献

欢迎提 issue 和 PR。请保持代码风格一致，新增功能尽量补上边界处理与注释。

## License

[MIT](LICENSE) © 2026 lanmachong（懒码虫）
