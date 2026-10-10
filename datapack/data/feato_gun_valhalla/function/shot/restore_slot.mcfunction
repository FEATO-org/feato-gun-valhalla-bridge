data modify storage feato_gun_valhalla:adapt empty set value {}
data modify storage feato_gun_valhalla:adapt original set value 1
$execute if items entity @s $(slot) *[minecraft:custom_data~{fgv:{original:1},gbg:{projectile_type:201}}] run item modify entity @s $(slot) feato_gun_valhalla:shot/restore
data modify storage feato_gun_valhalla:adapt original set value 3
$execute if items entity @s $(slot) *[minecraft:custom_data~{fgv:{original:3},gbg:{projectile_type:203}}] run item modify entity @s $(slot) feato_gun_valhalla:shot/restore
