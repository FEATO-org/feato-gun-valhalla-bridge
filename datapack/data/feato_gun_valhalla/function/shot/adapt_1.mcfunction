data modify storage feato_gun_valhalla:adapt original set value 1
data modify storage feato_gun_valhalla:adapt adapter set value 201
execute store result storage feato_gun_valhalla:adapt session int 1 run scoreboard players get #session fgv_shot
item modify entity @s weapon.mainhand feato_gun_valhalla:shot/adapt
scoreboard players set #adapted fgv_shot 1
