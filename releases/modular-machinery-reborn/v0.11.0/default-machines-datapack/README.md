# 默认机器与配方（数据包）

**mod 本身不再携带任何机器与配方**。内容都在这里，作为一个普通数据包，你可以安装、修改，也可以完全不装。

## 安装

把 `data/` 与 `pack.mcmeta` 放进 `saves/<存档>/datapacks/default-machines/`，或放进
`<实例>/datapacks/` 以对所有存档生效，然后 `/reload`（或重启）。

内容包括：

- `data/modular_machinery_reborn/machinery/` —— `alloy_furnace`、`iron_centrifuge`、`power_transformer`，
  以及变量集 `casings.var.json`。
- `data/modular_machinery_reborn/recipes/` —— 上述机器使用的 9 份配方。

## 怎么让这些机器拥有专属控制器

数据包**无法创建方块**：方块注册发生在 mod 构造阶段，而数据包的读取在其之后。所以这些机器默认只能用
**通用** `machine_controller`。

想让某台机器有专属控制器，就在 mod 的配置目录里放一份**控制器声明**——只写机器名、不写定义：

```
config/modular_machinery_reborn/machinery/alloy_furnace.json
{
  "registryname": "alloy_furnace"
}
```

`claims/` 目录里就是这三台机器的声明文件，复制进上述目录并**重启**即可。定义仍然留在这个数据包里，
所以你继续用 `/reload` 热改结构，而控制器方块在启动时创建。

| 声明文件 | 得到 | 绑定 |
|---|---|---|
| `claims/alloy_furnace.json` | `alloy_furnace_controller` | `modular_machinery_reborn:alloy_furnace` |
| `claims/iron_centrifuge.json` | `iron_centrifuge_controller` | `modular_machinery_reborn:iron_centrifuge` |
| `claims/transformer.json` | `transformer_controller` | `modular_machinery_reborn:transformer` |

声明若没有对应定义，会留下一个永远无法成型的方块；加载器会在启动时告警，不会让它悄悄存在。

## 内容为什么搬出 mod

把机器留在 mod 里，等于把机器清单冻进 jar。以数据包形式发布的内容可以按整合包替换、扩展或整体去掉，
并且能用 `/reload` 即时修改。代价就是上面那条：光有数据包无法产生方块，这正是「控制器声明」存在的意义。
