tag @s add fgv.record
scoreboard players operation @s fgv_shot = #session fgv_shot
$function feato_gun_valhalla:transport/send_entity {u0:$(u0),u1:$(u1),u2:$(u2),u3:$(u3),headshot:$(headshot),source:$(source)}
# Cleanup also runs when the send macro cannot be instantiated.
scoreboard players reset @s fgv_shot
kill @s
