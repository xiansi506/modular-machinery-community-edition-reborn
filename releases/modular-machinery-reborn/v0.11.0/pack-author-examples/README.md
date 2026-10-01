# 整合包作者示例

三份可直接照抄的最小例子，演示「自定义机器」与「专属控制器」的三种组合方式。

**前提**：0.11.0 起 mod 本身不含任何机器与配方。这里假设你**没有**安装 `example-datapack`，完全自己做一台机器。

## 场景 1：定义放配置目录 —— 机器与控制器一起得到

把 `config/` 复制进实例根目录并**重启**：

- `config/modular_machinery_reborn/machinery/example_machine.json` 是一份**完整定义**（含 `parts`）。
- 因为它在配置目录里，启动时会注册 `example_machine_controller`，绑定 `modular_machinery_reborn:example_machine`，
  且**只校验这一台机器**。

适合：自己写的机器、想立刻拿到专属控制器。

## 场景 2：定义放数据包 + 配置目录只放「声明」

想让结构可以 `/reload` 热改，就把定义放进数据包，配置目录只留一份**声明**——只写 `registryname`，不写 `parts`：

```json
{ "registryname": "example_machine" }
```

- `datapack/` 是数据包，含 `pack.mcmeta` 与 `data/example_pack/machinery/example_machine.json`（完整定义）。
- 把 `claim-example.json` 复制成 `config/modular_machinery_reborn/machinery/example_machine.json`（**替换掉**场景 1 的那份完整定义）。
- 重启后同样得到 `example_machine_controller`；此后改 `datapack/` 里的结构只需 `/reload`，不必重启。

适合：反复调整结构的开发过程，或把内容作为数据包分发。

**判定规则很简单：文件含 `parts` 就是定义，不含就是声明。** 有声明但没有任何来源提供定义时，加载器会告警说明该方块永远无法成型。

## 场景 3：配方放配置目录

`config/modular_machinery_reborn/recipes/example_machine_demo.json`：

- `recipes/a/b.json` → 配方 id `modular_machinery_reborn:a/b`。
- 改文件后 `/reload` 即生效，**不需要重启**。

配方也可以放数据包（`datapack/data/example_pack/recipes/example_machine_demo.json` 是同一份内容）。两种来源同 id 时会按常规数据包优先级竞争，**建议使用不同 id**。

## 目录对照

| 文件 | 作用 |
|---|---|
| `config/modular_machinery_reborn/machinery/example_machine.json` | 场景 1：完整定义（会得到控制器） |
| `claim-example.json` | 场景 2：声明文件，复制过去替换上面那份 |
| `config/modular_machinery_reborn/recipes/example_machine_demo.json` | 场景 3：配置目录配方 |
| `datapack/pack.mcmeta` + `datapack/data/example_pack/...` | 场景 2/3：数据包形式的定义与配方 |

## 结构长什么样

`example_machine.json` 声明的是控制器脚下那层 3×3 机箱（`y = -1`）：

```
y = -1（控制器在 y = 0 的原点）
  ■ ■ ■
  ■ □ ■     □ = 控制器所在位置，不参与匹配
  ■ ■ ■     所以这个结构共 9 个位置
```

材料：任意 `modular_machinery_reborn:blockcasing`（`elements` 只写方块 id 时匹配该方块的**任意** `casing` 变体）。

## 一个提醒

**必须重启，不能只 `/reload`。** 方块只能在 mod 构造阶段注册，而配置目录在构造期被扫描。启动之后才放进目录的机器，`/reload` 能加载它的定义（可用通用 `machine_controller` 成型），但要**下次启动**才有专属控制器。日志会明确告诉你哪些机器处于这种状态。
