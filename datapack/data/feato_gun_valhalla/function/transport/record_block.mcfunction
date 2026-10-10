tag @s add fgv.record
scoreboard players operation @s fgv_shot = #session fgv_shot
$function feato_gun_valhalla:transport/send_block {kind:"$(kind)"}
# Cleanup also runs when the send macro cannot be instantiated.
scoreboard players reset @s fgv_shot
kill @s
