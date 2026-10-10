execute unless score #ready fgv_shot matches 1 run return fail
execute unless score #in_scope fgv_shot matches 1 unless score #observe fgv_shot matches 1 run return fail
data modify storage feato_gun_valhalla:events input.kind set value "block"
function feato_gun_valhalla:transport/block with storage feato_gun_valhalla:events input
