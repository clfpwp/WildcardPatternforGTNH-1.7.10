# Wildcard Pattern 1.0.0

首个正式版本，面向 GTNH 2.8.4 / Minecraft 1.7.10 / AE2 Unofficial。

## Highlights

- 新增可右键配置的 `通配样板`，无需再与普通 AE2 样板合成后使用。
- 支持按规则配置输入与输出，并按一一对应关系展开 AE2 加工样板。
- 支持名称模式与矿辞模式，规则可使用 `*` 进行通配匹配，例如 `*锭`、`ingot*`。
- 支持从 NEI 拖入物品，也支持手动输入名称或矿辞。
- 支持每条规则独立预览，并提供单独预览页面进行搜索与分页查看。
- 支持总排除与单规则排除，便于屏蔽不需要的材料。
- 支持重复矿辞去重页面，显示候选物品所属 mod，默认优先 GregTech 物品。
- 兼容 GTNH / AE2 的 ME 接口与 GT 样板输入总成相关样板扩展逻辑。
- 兼容 ME 接口样板翻倍功能，规则数量也可单独倍增或减半。
- 通配样板不可堆叠，避免多个不同配置样板互相覆盖。

## Files

- 正式使用：`wildcardpattern-1.0.0.jar`
- 开发环境：`wildcardpattern-1.0.0-dev.jar`
- 源码包：`wildcardpattern-1.0.0-sources.jar`

## Requirements

- Minecraft `1.7.10`
- Forge `10.13.4.1614`
- GTNH `2.8.4`
- Applied Energistics 2 Unofficial
- GregTech 5 Unofficial
- ModularUI
