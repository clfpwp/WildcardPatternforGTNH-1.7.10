# Wildcard Pattern

面向 Minecraft 1.7.10 / GTNH 的 AE2 扩展模组。

本项目提供一个可直接右键配置的 `通配样板` 物品，可以生成制作一个包含大量配方的样板。

## 当前版本

- Mod ID: `wildcardpattern`
- Minecraft: `1.7.10`
- Forge: `10.13.4.1614`
- 版本号: `1.0.0`

## 核心功能

- 右键打开独立配置界面，不需要再与普通 AE2 样板额外合成
- 支持按规则配置输入 / 输出，一一对应生成加工样板
- 支持两种匹配模式：
  - 名称模式：支持通配匹配，例如 `*锭`
  - 矿辞模式：支持通配匹配，例如 `ingot*`
- 支持从 NEI 直接拖入物品，也支持手动输入名称或矿辞
- 支持每条规则独立预览，并提供单独的预览页面浏览所有展开结果
- 支持总排除与每条规则独立排除
- 支持重复矿辞结果去重，并在去重页面显示物品所属 `mod`
- 去重默认优先 `gregtech` 物品，同时允许玩家手动切换保留项
- 兼容 GTNH / AE2 的样板扩展逻辑
- 兼容 ME 接口样板翻倍；规则也可单独进行倍增或减半

## 使用说明

 在每一行中填写一条规则：
   - 左侧填写输入条件
   - 右侧填写输出条件
   - 选择名称模式或矿辞模式
   - 设置数量
 如有需要，可为单条规则设置排除项，或在总排除区域统一屏蔽结果。
 点击预览，检查当前规则将展开出哪些加工样板。
 如存在多个相同矿辞来源，可进入去重页面选择最终保留哪个模组的物品。
 保存后，样板即可参与 AE2 / GTNH 相关加工逻辑。

## 界面说明

- 主页面：编辑规则、切换模式、调整数量、进入预览 / 去重页面
- 预览页面：按规则查看生成结果，支持搜索与分页
- 去重页面：处理相同矿辞下的重复候选项，并显示来源模组

## 开发与构建

首次构建需要联网下载 Gradle 依赖与 GTNH 构建脚本。

```powershell
.\gradlew.bat assemble --no-configuration-cache
```

常用命令：

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat assemble
```

## 主要目录

- `src/main/java/com/myname/wildcardpattern/`：模组源码
- `src/main/resources/assets/wildcardpattern/lang/`：中英文语言文件
- `src/main/resources/mixins.wildcardpattern.json`：Mixin 配置
- `src/main/resources/mcmod.info`：模组元数据

## 兼容性说明

- 目标环境：GTNH 2.8.4 / AE2 Unofficial / Forge 1.7.10
- 依赖模组：
  - `appliedenergistics2`
  - `gregtech`
  - `modularui`

## 致谢

- 原始灵感与功能参考：`LeoDreamer2004/Wildcard-Pattern`,`Ch4oooooooLL/AE2PatternGen`
