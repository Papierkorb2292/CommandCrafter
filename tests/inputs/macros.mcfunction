$tp @s @e[type=text_display,tag=$(name),limit=1]
$execute if items entity @s hotbar.$(slot) *[minecraft:container,!minecraft:container=[]]
$execute \
    if items entity @s armor.* $(id)[$(component)={"$(tag)": $(value)},minecraft:custom_name={text: "Key"}] \
    run say Step aside
$give @s $(id)[$(component)={"$(tag)": $(value)}, minecraft:custom_name={text: "Broken Key"}]
$execute as @a[limit=1,$(condition),sort=nearest,level=10] run say Hi!
$execute if data storage test {$(tag):"Still analyzed", $(interesting), "huh":true}
$execute unless data storage the_orb {$(name):{assimilated: true}} if score @s present matches 1.. run attribute @s movement_speed modifier add influence $(speed) add_value
$execute if items entity @s player.cursor *[custom_data=$(data)] run item modify entity @s player.cursor $(modifier)
$execute if predicate {"condition":"minecraft:random_chance","chance":$(probability_that_the_singularity_awakens)} run return fail
$execute as @e[type=item_display,tag=scores_$(team),limit=1] run function rgb:init_$(team)_score_display with entity @s item.components.minecraft:custom_data
$particle dust{color:$(my_color),scale:0.65} ^-0.01 ^0.99 ^0 0 0 0 0 1 force @a
$$(execute) entity @a run execute run say Assimilate!
$$(e)$(x)$(e)$(c)$(u)$(t)$(e) run execute if entity @a[distance=..$(radius)] $(run) say Where's social distancing when you need it?
$execute if score $(condition) $(objective) matches 0 run function $(success_function)
$scoreboard players display name §$(game)_team1 $(player) ["Info: ",{"translate":"$(game).score.name.$(info)"},"$(value)"]
$execute as @a at @s positioned $(Offset) positioned ^ ^ ^1 unless entity @e[tag=$(anchor),distance=..0.1] if entity @e[tag=$(anchor),distance=..10] run function thing:fix
$summon minecraft:item_display ~ ~$(y) ~ {data: {ref: $(uuid)}, transformation: {scale: [50,50,0.1]}, item: {id: "minecraft:paper"}, Tags: ["sponsor"]}
$execute if entity @e[tag=spawn,distance=..$(area_radius)] positioned ~-$(vertical) ~ ~ unless block ~ ~ ~ air run function thingy:do_the_thing with storage stuff_that_is_needed_to_do_the_thing
$execute store result storage host:data map_$(id).x$(num) int 1 run scoreboard players get x host.pos
$execute store result storage host:data map_$(id).y$(num) int 1 run scoreboard players get y host.pos
$execute store result storage host:data map_$(id).z$(num) int 1 run scoreboard players get z host.pos
$scoreboard players set $random_max $(objective) 10
$scoreboard players set $random_min $(objective) $(min)
$scoreboard players operation $random $(objective) += $random_min $(objective)
$execute unless data storage host:data participants.$(entry1)$(entry2)$(entry3)$(entry4).id run return 0
$execute $(condition)run function host:maps/$(name)/setup
$execute in $(dimension) positioned $(x) $(y) $(z) if loaded ~ ~ ~ run data modify storage use_the_force_luke:chunks $(dimension).loaded[$(index)] set value true
$execute in $(dimension) positioned over $(target) run setblock ~ ~ ~ minecraft:grass_block
$tellraw @a [\
    {\
        translate: "death.attack\
        .arrow",\
        with: ["$(target)" ,"$(pla\
        yer)"],\
    }\
]
$execute \
    $(if) data storage game:instance_$(instance) {thing1:{value:$(val)}} \
    $(if) data storage game:instance_$(instance) {thing2:{value:$(val)}} \
    $(if) data storage game:instance_$(instance) {thing3:{value:$(val)}} \
    run execute run advancement grant @a everything