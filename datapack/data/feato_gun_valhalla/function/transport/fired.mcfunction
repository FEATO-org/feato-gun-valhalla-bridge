# Freeze event arguments before spawning the record. No latest-event polling.
$execute summon minecraft:marker run function feato_gun_valhalla:transport/record_fired {u0:$(u0),u1:$(u1),u2:$(u2),u3:$(u3),weapon:"$(weapon)",original:$(original),source:$(source)}
