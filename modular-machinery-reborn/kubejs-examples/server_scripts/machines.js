// Modular Machinery: Community Edition Reborn - KubeJS 6 (Forge 1.20.1)
// Copy to <instance>/kubejs/server_scripts/ and run /reload.
//
// Requirements use the same schema as the JSON recipes in
// data/modular_machinery_reborn/recipes/:
//
//   type      'modular_machinery_reborn:item' | ':fluid' | ':energy'
//             ('modularmachinery:item' also works, so original files load unchanged)
//   io-type   'input' | 'output'
//
//   item      item: 'minecraft:coal' | '#forge:ingots/iron' | 'ore:ingotIron'
//             amount, minAmount/maxAmount (outputs only), chance (outputs only)
//   fluid     fluid: 'minecraft:water', amount, chance, perTick
//   energy    energyPerTick
//
// KubeJS writes the root "type": "modular_machinery_reborn:machine" for you. A hand-written
// JSON recipe must carry it itself, or RecipeManager drops the file before the mod sees it.
//
// `machine` resolves to a machine registry name. Machines available in 0.8.0+:
//   alloy_furnace    iron_centrifuge    transformer    (or anything you declare yourself)
ServerEvents.recipes(event => {
  event.recipes.modular_machinery_reborn.machine({
    machine: 'alloy_furnace',
    registryName: 'kubejs_demo',
    recipeTime: 100,
    requirements: [
      { type: 'modular_machinery_reborn:energy', 'io-type': 'input', energyPerTick: 40 },
      { type: 'modular_machinery_reborn:item', 'io-type': 'input', item: 'minecraft:copper_ingot', amount: 2 },
      { type: 'modular_machinery_reborn:item', 'io-type': 'output', item: 'minecraft:iron_ingot', amount: 1, chance: 0.75 },
      { type: 'modular_machinery_reborn:fluid', 'io-type': 'input', fluid: 'minecraft:water', amount: 100 }
    ]
  })
})
