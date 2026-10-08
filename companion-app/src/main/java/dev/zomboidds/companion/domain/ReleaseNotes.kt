package dev.zomboidds.companion.domain

/**
 * One release as the app's "What's new" shows it: short versions of the GitHub release notes,
 * without the install steps. [notes] may mark words **bold**. [date] is null until released.
 */
data class Release(val version: String, val title: String, val date: String?, val notes: List<String>)

/**
 * Newest first. The next release collects its notes at the top as they're made, undated; when
 * releasing, give it its final version (same as versionName), date and title.
 */
val RELEASES = listOf(
    Release("1.3.2", "Fixes", null, listOf(
        "**Build:** tap **Craft ▾** and pick Build for the game's build menu. Wood Chair (Shoddy, Poor, Good) is one tile; pick the version in its panel. **Place** brings up the game's cursor on the top screen: d-pad moves, LB/RB turn, A builds, B stops.",
        "**Fixed:** food didn't age while you used the app instead of the game's inventory window. Frozen food never thawed, and playing without the mod made everything rot at once. Food ages, freezes and thaws as in the game again (and wet clothes in your bags dry).",
    )),
    Release("1.3.1", "Food, drinks and stacks", "3 Oct 2026", listOf(
        "**Food** shows its name the way the game writes it, \"Steak (Fresh, Cooked)\". In the list the words are coloured, on a tile the corner says Cooked, Uncooked or Burnt.",
        "**In the oven:** a bar on the food fills while it cooks and turns red once it starts burning.",
        "**Bottles and pots** show how full they are: \"0.3 / 0.6 L\" in the list, a bar on the tile, and what's in them.",
        "**Part of a stack:** in an item's panel, each place under Move to has **1** and half. Or tap a stack's **×5** to unfold it and pick, drag or move single items.",
        "**Read books** and watched tapes get the game's tick.",
        "Items you set **Unwanted** in the game are faded, like the game greys them out.",
        "**Fixed:** with Worn unfolded in the list view, swiping on your clothes didn't scroll.",
    )),
    Release("1.3.0", "Map", "30 Sep 2026", listOf(
        "**Map:** the game's minimap on the bottom screen, drawn from the game's own map. It follows you (or your car), and only shows what you've explored.",
        "It sits beside **Here**, left or right, or on a tab of its own: the button in the map's corner moves it.",
        "Zoom with + and −, drag to look around. The star shows your map symbols and notes, and the game's place names.",
        "It shows on saves that allow the minimap. For other saves, tick **Map on every save** in **Options → Mods → ZomboidDS**.",
        "**Inventory layouts:** the button next to Put all picks top and bottom, **side by side**, or one at a time.",
        "**What's new** after an update, with every earlier version. It's also in the About card.",
        "**Fixed:** tapping Worn in list view didn't unfold your clothes in the top-and-bottom layout.",
    )),
    Release("1.2.1", "Pick several items", "28 Sep 2026", listOf(
        "**Hold to pick:** hold an item and let go without moving to pick it. Then tap other items to add or remove them.",
        "**Box select:** draw a box from empty space over items to pick them together, like the game's inventory window.",
        "**N selected ›** opens what you can do with the group: move them all, or the game's own menu (drop, eat, ...). ✕ or a tap on empty space clears the pick.",
        "**Drag** one picked item and the rest come along.",
    )),
    Release("1.2.0", "Drag and drop", "26 Sep 2026", listOf(
        "**Tap an item** and the panel lists everywhere it can go: your bags and key ring, then the containers around you. One tap moves it.",
        "**Hold an item and drag it** onto a container's tab, or onto the other open container. Places it can go light up green.",
        "**An item's main uses are buttons**, from the game's own menu: eat, drink, wear, read, apply bandage, reload, ... Buttons with › unfold the game's choices.",
        "**Deck** moved to the middle of the tabs, and **Weapons** is on it by default.",
    )),
    Release("1.1.0", "Here and the Deck", "26 Sep 2026", listOf(
        "**Here** is its own tab: what you can do where you stand, as cards per object (fridge, sink, door, ...), updating as you walk.",
        "**In a car** it becomes **Vehicle**: speed, engine and fuel, and the car's own menu as big buttons.",
        "**The command deck:** the game's speed buttons, the time and your alarm (with a watch), and the commands you pick under **Add or edit**: zoom, search mode, flashlight, map, sit, shout, drop bag, weapons, alarm.",
        "**Take all** puts things in the bag you have open, like the game's Loot all.",
    )),
    Release("1.0.1", "Take all fix", "26 Sep 2026", listOf(
        "**Take all** puts everything into the container you have selected (a bag, for example), like the game's Loot all.",
        "In the **Single** layout, picking one of your bags makes it where Take all goes.",
    )),
    Release("1.0.0", "First release", "25 Sep 2026", listOf(
        "**Inventory:** your bags and every container around you, with the game's own icons. Move items with a tap, use them through the game's item menu. The controller's Loot button opens the container here.",
        "**Deck:** the game's speed buttons, and what you can do where you stand.",
        "**Status:** the game's moodles, your body with its injuries, and the game's treatments.",
        "**Craft:** the recipes you can make with what's in reach, and crafting them.",
        "**Vehicle:** speed, fuel and engine while you drive.",
    )),
)
