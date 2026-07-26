# Fake Player

[English](README_EN.md) | 简体中文

适用于 Minecraft 26.1.2 / NeoForge 26.1.2.87 的 Carpet 风格假人模组。

## 开发原因

1. Carpet 没有 NoeForge 版本，新版本信雅互联等又不更新，然后假人有些功能我又想要，比如GUI页面、区块加载功能，就顺手写了一个mod。

## 简介

- `/fakeplayer`：以自动名称在当前位置生成假人。
- `/fakeplayer <名称>` 或 `/fakeplayer spawn <名称>`：生成指定名称的假人。
- `/fakeplayer list`：列出当前假人。
- `/fakeplayer remove <名称>`：移除假人。
- 右键假人：打开控制界面，可切换持续攻击/使用、跳跃、停止、转向、潜行或移除。

命令默认需要游戏管理员权限（与原版 `/gamemode` 同级）。名称遵循玩家名称规则，只能使用 1-16 个英文字母、数字或下划线。

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

> 详情

## 配置文件

> 是否要有？
