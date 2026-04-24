# Wildcard Pattern

面向 Minecraft 1.7.10 / GTNH 的 AE2 扩展模组。

本项目提供一个可直接右键配置的 `通配样板` 物品，可根据名称或矿辞规则批量展开 AE2 加工样板，适合 GTNH 中大量同类材料的自动化配置。

## 当前版本

- Mod ID: `wildcardpattern`
- Minecraft: `1.7.10`
- Forge: `10.13.4.1614`
- 版本号: `1.0.4`
- gtnh版本: `2.8.4`

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

## 使用说明

1. 基本使用
   - 左侧填写输入条件
   - 右侧填写输出条件
   - 选择名称模式或矿辞模式
   - 设置数量
2. 如有需要，可为单条规则设置排除项，或在总排除区域统一屏蔽结果。
3. 点击预览，检查当前规则将展开出哪些加工样板。
4. 如存在多个相同矿辞来源，可进入去重页面选择最终保留哪个模组的物品。
5. 保存后，样板即可参与 AE2 / GTNH 相关加工逻辑。

## 主要目录

- `src/main/java/com//wildcardpattern/`：模组源码
- `src/main/resources/assets/wildcardpattern/lang/`：中英文语言文件
- `src/main/resources/mixins.wildcardpattern.json`：Mixin 配置
- `src/main/resources/mcmod.info`：模组元数据

## 兼容性说明

- 目标环境：GTNH 2.8.4 / AE2 Unofficial / Forge 1.7.10
- 目前仅测试过 `2.8.4` 版本，不确定其他版本能否正常运行
- 依赖模组：
  - `appliedenergistics2`
  - `gregtech`
  - `modularui`

## 致谢

- 原始灵感与功能参考：`LeoDreamer2004/Wildcard-Pattern`,`Ch4oooooooLL/AE2PatternGen`
