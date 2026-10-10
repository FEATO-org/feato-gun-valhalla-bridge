execute unless score #ready fgv_shot matches 1 run return fail
execute unless score #in_scope fgv_shot matches 1 unless score #observe fgv_shot matches 1 run return fail
# Executed as the target in the native hit function, while native storage is valid.
function feato_gun_valhalla:shot/shooter_snapshot
data modify storage feato_gun_valhalla:events input.headshot set value 0
execute if entity @s[type=#gbg:humanoid,distance=2.6..] run data modify storage feato_gun_valhalla:events input.headshot set value 1
data modify storage feato_gun_valhalla:events input.source set value -1
data modify storage feato_gun_valhalla:events input.source set from storage gbg:macro input.source
function feato_gun_valhalla:transport/entity with storage feato_gun_valhalla:events input
