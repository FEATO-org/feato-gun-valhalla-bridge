# Existing objective on reload is harmless; markers are refreshed every tick.
scoreboard objectives add fgv_bridge dummy
function feato_gun_valhalla:bridge/marker
scoreboard players add #heartbeat fgv_bridge 1

scoreboard objectives add fgv_shot dummy
scoreboard players set #contract fgv_shot 2
scoreboard players set #ready fgv_shot 0
scoreboard players set #session fgv_shot 0
