# Master Launcher

一个安卓启动器。三个入口指向同一个对象：知道名字就搜，只知道大概就浏览，已成习惯就手势。

## 这个包里有什么

| 文件 | 用途 | 给谁看 |
|---|---|---|
| `CLAUDE.md` | 架构红线与不可违反的约束 | **AI agent 必读，放在仓库根目录** |
| `docs/01-philosophy.md` | 设计哲学、判断依据、不做清单 | 人 + AI |
| `docs/02-requirements.md` | 需求文档，P0/P1/P2 与验收标准 | AI 拆任务用 |
| `docs/03-architecture.md` | 分层、数据模型、模块契约、目录结构 | AI 写代码用 |
| `docs/04-design-system.md` | 颜色、字号、间距、动效参数 | AI 写 UI 用 |
| `design/mockup.html` | 四个界面的静态设计稿，浏览器直接打开 | 人对照验收 |

## 怎么用

1. `git init` 一个新仓库，把 `CLAUDE.md` 放到根目录，`docs/` 和 `design/` 原样放进去。
2. 第一轮对话给 agent 的指令建议是：

   > 读 CLAUDE.md 和 docs/ 下全部文档。先只实现 P0-1 到 P0-4（Target 模型、IndexStore、PinyinEngine、Resolver），不要碰任何 UI。给我一个能跑的单元测试证明 "wxdsh" 能匹配到 "微信读书"。

3. 引擎跑通、测试通过之后，再让它做 Surface 层。**顺序不要颠倒**——这套设计的成败全在搜得准不准，UI 是最后 10%。

## 当前状态

设计冻结，未开始编码。已明确排除的方案见 `docs/01-philosophy.md` 的"不做清单"。
