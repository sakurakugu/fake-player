# Fake Player

[English](README_EN.md) | 简体中文

适用于 Minecraft 26.1.2 / NeoForge 26.1.2.87 的 Carpet 风格假人模组。

## 开发原因

1. Carpet 没有 NoeForge 版本，新版本信雅互联等又不更新，然后假人有些功能我又想要，比如GUI页面、区块加载功能，就顺手写了一个mod。

## 简介

- `/fakeplayer`：在当前位置生成假人。
- `/fakeplayer <操作> <名称>` 或 `/player <名称> <操作>`：操控假人 （`/player` 用法和 Carpet 一样）。
- `/fakeplayer gui` 或 `/fakeplayer gui <名称>`：打开对应的图形页面。

## 图例

> 暂无

## 使用

> 详细使用方法

## 构建

```powershell
.\gradlew.bat build
```

Gradle 会通过 Foojay 自动获取 Minecraft 26.1 所需的 Java 25 工具链。构建产物位于 `build/libs/`。

## 功能

- `/fakeplayer`：以自动名称在当前位置生成假人。
- `/fakeplayer <名称>` 或 `/fakeplayer spawn <名称>`：生成指定名称的假人。
- `/fakeplayer list`：列出当前假人。
- `/fakeplayer gui` 或 `/fakeplayer setting`：打开全局设置界面，可从中进入假人列表；在后面添加假人名称可直接打开对应假人的控制界面。
- `/fakeplayer kill <名称>`：移除假人。
- `/player <名称> <操作>`：以名字在前的 Carpet 风格写法，支持 `spawn`、`kill`、`shadow`、`attack`、`use`、`jump`、`stop`、`turn_left`、`turn_right` 和 `sneak`。`shadow` 会踢出在线真玩家，并在其原位置生成同名假人。
- 真玩家上线时，会自动移除同 UUID 或同名的假玩家并正常登录。
- 按 `G`：打开假人全局设置（可在按键设置中修改）。
- 右键假人：打开控制界面，可切换持续攻击/使用、跳跃、停止、转向、潜行或移除。

命令默认需要游戏管理员权限（与原版 `/gamemode` 同级）。名称只能使用 1-16 个字母、数字、下划线或连字符。

生成命令使用执行来源的维度、位置和朝向。玩家执行时在玩家位置生成，命令方块执行时在命令方块位置生成。

## 配置文件

> 是否要有？
