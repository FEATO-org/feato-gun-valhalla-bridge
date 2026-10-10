# Actual Gun Core callback, never an input event. Only reserved Bridge types.
execute unless score gbg.projectile_type gbg.temp matches 201 unless score gbg.projectile_type gbg.temp matches 203 run return fail
execute unless entity @s[type=minecraft:player] run return fail
execute unless score #ready fgv_shot matches 1 run return fail
scoreboard players set #item_session fgv_shot 0
execute store result score #item_session fgv_shot run data get entity @s SelectedItem.components."minecraft:custom_data".fgv.session
execute unless score #item_session fgv_shot = #session fgv_shot run return fail
scoreboard players set #original fgv_shot 0
execute store result score #original fgv_shot run data get entity @s SelectedItem.components."minecraft:custom_data".fgv.original
execute unless score #original fgv_shot matches 1 unless score #original fgv_shot matches 3 run return fail
function feato_gun_valhalla:shot/shooter_snapshot
data modify storage feato_gun_valhalla:events input.weapon set from storage gbg:gun_data gbg.idle_model
execute store result storage feato_gun_valhalla:events input.original int 1 run scoreboard players get #original fgv_shot
execute store result storage feato_gun_valhalla:events input.source int 1 run scoreboard players get @s gbg.id
scoreboard players set #accepted fgv_shot 0
function feato_gun_valhalla:transport/fired with storage feato_gun_valhalla:events input
execute unless score #accepted fgv_shot matches 1 run return fail
# Restore original type for native hit / piercing / reset predicates. No custom ray engine.
scoreboard players operation gbg.projectile_type gbg.temp = #original fgv_shot
execute if score #original fgv_shot matches 1 run function gbg:gun/raycast/projectile/bullet
execute if score #original fgv_shot matches 3 run function gbg:gun/raycast/projectile/pellets
function feato_gun_valhalla:shot/shooter_snapshot
function feato_gun_valhalla:transport/complete with storage feato_gun_valhalla:events input
