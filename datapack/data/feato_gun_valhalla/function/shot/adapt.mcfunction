# Explicit administrator opt-in only. No tick-time inventory mutation.
execute unless score #ready fgv_shot matches 1 run return fail
execute unless entity @s[type=minecraft:player] run return fail
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/aa12",projectile_type:3,projectile_speed:1,damage_type:2}}] run function feato_gun_valhalla:shot/adapt_3
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/ak47",projectile_type:1,projectile_speed:1,damage_type:2}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/barrett_m82",projectile_type:1,projectile_speed:1,damage_type:3}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/beretta_486",projectile_type:3,projectile_speed:1,damage_type:2}}] run function feato_gun_valhalla:shot/adapt_3
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/beretta_686",projectile_type:3,projectile_speed:1,damage_type:2}}] run function feato_gun_valhalla:shot/adapt_3
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/colt_python",projectile_type:1,projectile_speed:1,damage_type:3}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/desert_eagle",projectile_type:1,projectile_speed:1,damage_type:1}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/fn_scar",projectile_type:1,projectile_speed:1,damage_type:2}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/glock_17",projectile_type:1,projectile_speed:1,damage_type:1}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/heckler_and_koch_mp5",projectile_type:1,projectile_speed:1,damage_type:2}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/luger_p08",projectile_type:1,projectile_speed:1,damage_type:1}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/m1911",projectile_type:1,projectile_speed:1,damage_type:1}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/m4a1",projectile_type:1,projectile_speed:1,damage_type:2}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/p90",projectile_type:1,projectile_speed:1,damage_type:2}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/remington_870",projectile_type:3,projectile_speed:1,damage_type:2}}] run function feato_gun_valhalla:shot/adapt_3
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/sig_p320",projectile_type:1,projectile_speed:1,damage_type:1}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/smith_and_wesson_model_29",projectile_type:1,projectile_speed:1,damage_type:3}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/spas_12",projectile_type:3,projectile_speed:1,damage_type:2}}] run function feato_gun_valhalla:shot/adapt_3
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/springfield_m1a",projectile_type:1,projectile_speed:1,damage_type:3}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/thompson_submachine_gun",projectile_type:1,projectile_speed:1,damage_type:2}}] run function feato_gun_valhalla:shot/adapt_1
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:"modern_guns:gun/winchester_model_70",projectile_type:1,projectile_speed:1,damage_type:3}}] run function feato_gun_valhalla:shot/adapt_1
