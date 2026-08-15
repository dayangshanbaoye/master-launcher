# Master Launcher — 工程约束

在写任何代码之前，先读完 `docs/` 下的四份文档。下面是不可违反的红线。

## 架构红线

1. **Surface 层不许有业务逻辑。**
   `ui/` 包下不允许出现任何排序、过滤、匹配、分类判断的代码。没有 `sortedBy`，没有 `filter { it.packageName == ... }`，没有针对特定 app 的 `if`。Surface 唯一被允许做的事是构造一个 `Query` 交给 `Resolver`，然后渲染返回的 `List<Target>`。
   如果你发现自己想在 Composable 里写排序，说明该逻辑属于 `Ranker`。

2. **三个入口共用一个 Resolver。**
   搜索、浏览、手势不允许各自实现一套解析。它们的差别只在构造的 `Query` 不同。任何"手势专用查找路径"都是错的。

3. **Engine 层不许 import Compose、Context 之外的 Android UI 类。**
   `engine/` 必须能在纯 JVM 单元测试里跑起来，不需要 Robolectric。

4. **不引入 SQLite / Room 做索引。**
   App 量级 200–500，全部索引常驻内存。持久化只用 Proto DataStore 存快照，冷启动反序列化。

5. **没有"设置页"这个概念。**
   所有可调项要么由引擎自己决定，要么通过条目长按的上下文菜单就地调整。不做集中式设置界面。如果一个功能必须靠用户去设置里配才能用，重新设计这个功能。

## 技术选型（已定，不要替换）

- Kotlin + Jetpack Compose，minSdk 26，targetSdk 35
- 协程 + `Flow`，索引更新走 `StateFlow`
- `LauncherApps` + `ShortcutManager` 取数据源，不用 `PackageManager.getInstalledApplications`
- Proto DataStore 做索引快照持久化
- 语音：`SpeechRecognizer`；无 Google 服务的设备预留厂商 SDK 适配接口，不硬编码

## 性能预算（写完每个模块自测）

| 指标 | 预算 |
|---|---|
| 冷启动到首帧可交互 | < 150ms |
| 索引反序列化 | < 20ms |
| 单次 query 到结果返回 | < 8ms（500 条目） |
| 语音 partial result 刷新间隔 | 200ms |
| 手势识别判定 | 阈值 48dp / 上限 300ms |

query 超过 8ms 就是实现有问题，不要靠加 loading 态掩盖。

## 提交约定

- 一个模块一个 commit，带对应的单元测试
- 引擎层测试覆盖率不低于 80%，UI 层不做覆盖率要求
- 每个 P0 条目完成后停下来等人验收，不要连着往下做

## 常见错误

- 把拼音匹配写进 `Ranker`——拼音属于 `PinyinEngine`，`Ranker` 只消费匹配得分
- 在 `Target` 里存 `Drawable`——只存 URI，图标走独立的 `IconCache`
- 用 `ApplicationInfo.category` 之外的方式硬编码分类——分类必须可回退、可覆盖，但不可硬编码
- 为了"看起来快"加骨架屏——预算内就不需要，超预算就去修
