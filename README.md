# Fake Player

适用于 Minecraft 26.1.2 / NeoForge 26.1.2.87 的 Carpet 风格假玩家模组。

## 简介

Fake Player 提供可通过命令和图形界面控制的服务端假玩家，支持生成、背包管理、移动、交互和持续动作。

- `/fakeplayer`：生成、移除、列出假玩家或打开设置界面。
- `/player <名称> <操作>`：使用 Carpet 风格语法控制指定假玩家。
- `/bot <操作>`：保存、加载和分组管理假玩家预设。
- `/chunkloader <操作>`：管理持久化的固定半径区块加载点。
- 按 `G`：打开全局设置界面，可在按键设置中修改快捷键。
- 按 `M`：打开以所在区块为中心的加载范围地图；按 `B`：切换加载状态 HUD。
- 全局设置界面可即时切换恢复假人、恢复动作和四项自动化功能，修改会写入当前世界的服务端配置。
- 右键假玩家：打开对应的控制界面。
- 真玩家上线时，自动移除同 UUID 或同名的假玩家，并由真玩家恢复该身份。
- 可选自动补货、潜影盒补货、低耐久工具替换和原版自动钓鱼。

命令和图形界面默认需要游戏管理员权限，与原版 `/gamemode` 权限等级相同，可在服务端配置中调整。假玩家名称只能包含
1-16 个字母、数字、下划线或连字符。

## 命令

命令中的 `<名称>` 表示必填参数，`[选项]` 表示可选参数。

### `/fakeplayer`

| 命令                         | 说明                                                                      |
| ---------------------------- | ------------------------------------------------------------------------- |
| `/fakeplayer`                | 在执行位置以自动名称生成假玩家。                                          |
| `/fakeplayer <名称>`         | 在执行位置生成指定名称的假玩家。                                          |
| `/fakeplayer spawn <名称>`   | 与上一条命令相同。                                                        |
| `/fakeplayer kill <名称>`    | 移除假玩家。生存模式且 `keepInventory=false` 时会掉落物品，否则保留背包。 |
| `/fakeplayer list`           | 列出当前所有假玩家。                                                      |
| `/fakeplayer gui [名称]`     | 打开全局设置界面，指定名称时打开该假玩家的控制界面。                      |
| `/fakeplayer setting [名称]` | `gui` 的别名。                                                            |

生成位置、维度和朝向取自命令执行来源。游戏模式继承执行命令的玩家，控制台执行时默认为创造模式。

### `/player`

所有 `/player` 命令均使用 `/player <名称> <操作>` 的顺序。

#### 生成与清除

| 操作     | 说明                                                                                       |
| -------- | ------------------------------------------------------------------------------------------ |
| `spawn`  | 在命令执行位置生成指定名称的假玩家，也可指定位置、朝向、维度和游戏模式。                   |
| `kill`   | 移除假玩家，并遵循 `keepInventory` 规则。                                                  |
| `shadow` | 踢出同名在线真玩家，在其位置生成继承状态的同名假玩家；非管理员只能替换自己。               |

`spawn` 与 Carpet 兼容的完整语法为：

```text
/player <名称> spawn
/player <名称> spawn in <游戏模式>
/player <名称> spawn at <位置>
/player <名称> spawn at <位置> facing <旋转>
/player <名称> spawn at <位置> facing <旋转> in <维度>
/player <名称> spawn at <位置> facing <旋转> in <维度> in <游戏模式>
```

例如：

```text
/player Steve spawn at ~ ~1 ~ facing 0 90 in minecraft:the_nether in creative
```

省略参数时使用命令源的位置、朝向和维度。目标游戏模式默认继承执行命令的玩家，控制台默认为创造模式；旁观模式强制
飞行，生存模式强制关闭飞行。显式指定游戏模式或其他维度需要游戏管理员权限。生成前会检查目标维度的世界范围和
世界边界。

## 服务端配置

NeoForge 会在世界目录的 `serverconfig/fakeplayer-server.toml` 中生成配置文件：

| 配置项 | 默认值 | 说明 |
| ------ | ------ | ---- |
| `commands.permissionLevel` | `2` | 使用命令、快捷键和右键控制界面的最低原版权限等级，范围为 `0` 至 `4`。 |
| `profiles.allowOfflineProfiles` | `true` | 在线查询或缓存没有档案时，是否允许生成稳定的离线 UUID。 |
| `profiles.strategy` | `ONLINE_PREFERRED` | 玩家档案解析策略，见下表。 |
| `persistence.restoreFakePlayers` | `true` | 服务器启动后恢复上次仍在线的假玩家。 |
| `persistence.restoreActions` | `true` | 恢复驻留假玩家时同时恢复持续动作。 |
| `automation.autoReplenishment` | `false` | 手中可堆叠物品剩余不超过一组的 1/8 时，从 36 格主背包补到半组。 |
| `automation.autoReplenishmentFromShulkerBoxes` | `false` | 补货时也搜索主背包内的潜影盒；需同时开启普通补货。 |
| `automation.autoReplaceTools` | `false` | 主手或副手工具剩余耐久不超过 10 时，换上背包中剩余耐久最高的同种物品。 |
| `automation.autoFishing` | `false` | 原版浮漂咬钩后自动收杆，10 刻后使用同一只手再次抛竿。 |
| `chunkloading.maxRadius` | `8` | 单个加载点的最大区块半径，范围为 `0` 至 `32`。 |

档案策略：

| 策略 | 行为 |
| ---- | ---- |
| `ONLINE_PREFERRED` | 在线服务器优先使用缓存或 Mojang 档案；查询失败时根据 `allowOfflineProfiles` 决定是否回退。 |
| `CACHE_ONLY` | 不发起在线查询，只使用服务器缓存；未命中时根据 `allowOfflineProfiles` 决定是否回退。 |
| `OFFLINE_ONLY` | 始终按名称生成稳定的离线 UUID。 |

档案解析完成后还会检查同名或同 UUID 在线玩家、封禁列表和白名单。在线查询在后台执行，不会阻塞服务器主线程。

### `/bot`

预设保存名称、UUID、维度、位置、朝向、游戏模式、飞行状态、持续动作和可选描述。预设本身不会上线，只有执行
`load` 后才会生成假玩家。

| 命令 | 说明 |
| ---- | ---- |
| `/bot list` | 列出全部预设。 |
| `/bot add <预设> <在线假人> [描述]` | 新建或覆盖预设。 |
| `/bot load <预设>` | 加载单个预设。 |
| `/bot remove <预设>` | 删除预设，并从所有分组移除该成员。 |
| `/bot group create <组>` | 创建空分组。 |
| `/bot group list` | 列出全部分组及成员数。 |
| `/bot group add <组> <预设>` | 将预设加入分组。 |
| `/bot group load <组>` | 批量加载，分别统计成功和失败数。 |
| `/bot group unload <组>` | 批量移除由组内预设对应的在线假人。 |
| `/bot group info <组>` | 查看分组成员。 |
| `/bot group remove <组>` | 删除分组，不删除其中的预设。 |

驻留清单、预设和分组均保存在世界 `data/fakeplayer/fake_players.dat` 中。正常移除、死亡或真玩家登录接管身份时，
假人会从驻留清单删除；服务器异常退出时则使用最近一次自动保存的状态恢复。每次启动读取前还会将现有存档复制为
带时间戳的 `fake_players.*.dat.bak`，避免解析失败后的空存档覆盖唯一的排查副本。

### `/chunkloader`

加载点以执行命令时的维度和坐标为中心；可配合原版 `/execute in ... positioned ... run ...`
在任意维度和坐标创建。半径 `0` 只加载中心区块，半径 `r` 加载 `(2r+1)^2` 个区块。

| 命令 | 说明 |
| ---- | ---- |
| `/chunkloader list` | 列出所有加载点。 |
| `/chunkloader backup` | 立即创建一份加载点 JSON 备份。 |
| `/chunkloader restore confirm` | 从最新可读备份恢复配置并重建本模组区块票。 |
| `/chunkloader info <名称>` | 查看维度、坐标、半径、区块数、模式和启用状态。 |
| `/chunkloader add <名称> <半径> [ticking]` | 在当前位置创建并启用加载点。 |
| `/chunkloader disable <名称>` | 撤销该点的票据，但保留配置。 |
| `/chunkloader enable <名称>` | 根据已保存配置重新添加票据。 |
| `/chunkloader configure <名称> <半径> [ticking]` | 更改半径和票据模式。 |
| `/chunkloader remove <名称>` | 撤销票据并删除配置。 |

省略 `ticking` 时票据只保持区块加载；指定后则允许完整区块刻和自然生成。普通在线假人仍使用
玩家的模拟距离语义。加载点配置与 NeoForge 所有者隔离票据一同持久化，不会影响原版 `/forceload`
或其他模组的票据。

每次成功修改都会在世界目录 `fakeplayer/backups` 写入独立 JSON 备份，原子替换临时文件并最多
保留 5 份。主 SavedData 无法解析时会自动从最新可读备份恢复；手动恢复也会从新到旧跳过损坏文件。
`restore confirm` 会先撤销当前配置拥有的票据，
再载入备份并重新对齐票据。

#### 界面与背包

| 操作               | 说明                                     |
| ------------------ | ---------------------------------------- |
| `gui` / `setting`  | 打开假玩家控制界面。                     |
| `bag` / `backpack` | 打开假玩家的 36 格背包，可直接存取物品。 |

#### 物品操作

| 操作               | 说明                                |
| ------------------ | ----------------------------------- |
| `drop [选项]`      | 丢出一个物品，默认使用主手。        |
| `dropStack [选项]` | 丢出整组物品，默认使用主手。        |
| `hotbar <槽位>`    | 切换快捷栏，槽位范围为 `1` 至 `9`。 |
| `swapHands`        | 交换主手与副手物品。                |

`drop` 和 `dropStack` 支持以下目标选项：

- `mainhand`：主手物品。
- `offhand`：副手物品。
- `0` 至 `35`：指定背包槽位，其中 `0` 是快捷栏第一格。
- `all`：处理背包内所有物品；`drop` 每个槽位丢出一个，`dropStack` 丢出全部。

目标选项后可添加动作模式：

- `continuous`：每刻持续执行。
- `interval <刻数>`：按指定间隔执行，刻数必须大于 `0`。
- `once`：取消该丢弃动作原有的持续或间隔计划，并执行一次。

例如：

```text
/player robot-1 drop offhand
/player robot-1 dropStack 0
/player robot-1 drop all interval 20
```

#### 骑乘

| 操作               | 说明                                                 |
| ------------------ | ---------------------------------------------------- |
| `mount`            | 骑乘附近的载具或可骑乘生物。                         |
| `mount <任意内容>` | 强制骑乘最近的任意实体，可通过反复执行尝试驯服生物。 |
| `dismount`         | 离开当前骑乘实体。                                   |

#### 移动与视角

| 操作                       | 说明                                                    |
| -------------------------- | ------------------------------------------------------- |
| `look <方向>`              | 看向 `north`、`south`、`west`、`east`、`up` 或 `down`。 |
| `look at <x> <y> <z>`      | 看向指定坐标。                                          |
| `move <方向>`              | 持续向 `forward`、`backward`、`left` 或 `right` 移动。  |
| `jump [模式]`              | 跳跃一次，或按指定模式重复跳跃。                        |
| `sneak` / `unsneak`        | 开始或停止潜行。                                        |
| `sprint` / `unsprint`      | 开始或停止疾跑。                                        |
| `turn back`                | 转身 180 度。                                           |
| `turn left` / `turn right` | 向左或向右旋转 90 度。                                  |
| `turn <俯仰角> <水平角>`   | 设置绝对视角；俯仰角范围为 `-90` 至 `90`。              |

#### 攻击与使用

| 操作            | 说明                                                 |
| --------------- | ---------------------------------------------------- |
| `attack [模式]` | 攻击实体或破坏视线内方块，相当于左键。               |
| `use [模式]`    | 与实体、方块或手中物品交互，相当于右键。             |
| `stop`          | 停止攻击、使用、丢弃、移动和跳跃，并取消潜行与疾跑。 |

`attack`、`use` 和 `jump` 的模式为 `continuous`、`interval <刻数>` 或 `once`。省略模式时执行一次；`once` 还会取消该动作原有的持续或间隔计划。

## 构建

项目需要 Java 25：

```powershell
$env:JAVA_HOME = '你的java地址'
.\gradlew.bat build
```

Gradle 会通过 Foojay 自动获取 Minecraft 26.1 所需的 Java 25 工具链。构建产物位于 `build/libs/`。

## 开发原因

Carpet 没有 NeoForge 版本，而部分新版本假玩家模组不再维护。这个项目用于补充图形界面、背包管理和完整命令控制等功能。
