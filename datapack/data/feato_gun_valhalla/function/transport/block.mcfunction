# Freeze event arguments before spawning the record. No latest-event polling.
$execute summon minecraft:marker run function feato_gun_valhalla:transport/record_block {kind:"$(kind)"}
