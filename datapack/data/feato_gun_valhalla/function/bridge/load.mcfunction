# Existing objective on reload is harmless; markers are refreshed every tick.
scoreboard objectives add fgv_bridge dummy
function feato_gun_valhalla:bridge/marker
scoreboard players add #heartbeat fgv_bridge 1
