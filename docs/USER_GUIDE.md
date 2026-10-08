# ZomboidDS: user guide

Play Project Zomboid on the top screen of your AYN Thor and manage your inventory, health, crafting
and vehicle on the bottom screen.

## What you need

- An **AYN Thor** (other dual-screen handhelds may work, but aren't tested).
- **Your own copy of Project Zomboid** (Build 42): ZomboidDS is a mod, it doesn't include the game.
  Tested on 42.20. On 42.21, ZombieBuddy needs a fix first, see
  [Troubleshooting](#troubleshooting).
- **[Zomdroid](https://github.com/udarmolota/zomdroid)** with **Project Zomboid Build 42** installed
  and working. Use this version of Zomdroid (udarmolota's): it's the one that's still maintained and
  the one ZomboidDS is built and tested with. The original Zomdroid stopped development in 2025.
- The **ZomboidDS Companion** app: `ZomboidDS-<version>.apk` from the
  [latest release](https://github.com/Space001000/ZomboidDS/releases/latest). It contains the
  ZomboidDS mod; you don't need to download the mod separately.
- Recommended: the **Mr. Purple Turnip** graphics driver, see
  [Better graphics: the Turnip driver](#better-graphics-the-turnip-driver).

## 1. Install the companion app

Install the APK on the Thor (open the downloaded file and allow installing apps if Android asks),
then open **ZomboidDS**. A setup checklist guides you through the rest; every step shows ✓ when it's
done.

## 2. Follow the setup checklist

0. **ZomboidDS app**: shows your version, and when a new one is out: tap **Download**, then
   **Install**. The first time, Android asks to allow installing apps from ZomboidDS: allow it, come
   back and tap **Install** again. After updating the app, the checklist may offer an update of the
   mod too (see below).
1. **Access to Zomdroid**: tap **Grant access**. A folder picker opens in Zomdroid's folder; tap
   **Use this folder**, then **Allow**. This lets the app install the mod into Zomdroid.
2. **ZombieBuddy**: ZomboidDS needs [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy), a
   Project Zomboid mod that lets mods use Java. If it's missing, tap **Download**: the app gets it
   from ZombieBuddy's official GitHub page and saves it to your Downloads folder. Then tap **Open
   Zomdroid**, go to **Optimization → ZombieBuddy**, select `ZombieBuddy-…-full.zip` and install it.
   The checklist turns ✓ by itself when you come back.

   > Why "full"? The download on ZombieBuddy's GitHub page contains only half of ZombieBuddy (the
   > part Zomdroid needs, but not the part the game needs), so mods that require it can't be
   > enabled. The app builds the complete package from the same official files.
3. **ZomboidDS mod**: tap **Install**.
4. **Enabled for new games**: tap **Enable**. New games now start with ZomboidDS and ZombieBuddy.

## 3. Launch both screens together with Cocoon

If you use [Cocoon](https://cocoon-shell.com), let it open the companion on the bottom screen
whenever you start Zomdroid:

1. From Cocoon's app drawer, add **Zomdroid** to the home screen.
2. Select Zomdroid on the home screen, press **Y → Edit → Companion app**, and choose
   **ZomboidDS**.

Starting Zomdroid from Cocoon now opens ZomboidDS on the bottom screen as well. Without Cocoon, start
Zomdroid first, then open ZomboidDS on the bottom screen yourself.

## 4. Play

Start Project Zomboid in Zomdroid and load a game. The bottom screen switches from the checklist to
your character:

- **Inventory**: your inventory and bags, and every container around you (drawers, shelves, the
  floor, ...). Yours are on top and the ones around you below, or side by side (yours on the left),
  or one container at a time. The open container's tab shows its name and weight; the others show
  the game's icon (tap one to open it). Opening a container's tab outlines it in the game.
  - Tap an item: on the left **Move to**, every container it can go to, one tap each (your bags,
    then the ones around you, the open one outlined). On the right the game's own menu for it: its
    main uses as buttons on top (eat, drink, wear, read, apply bandage, reload, turn on, ...; equip
    and attach for weapons), the rest below. Buttons with **›** unfold the game's choices (Eat: all,
    half, quarter).
  - Or **hold an item a moment and drag it** onto a container's tab, or onto the other open
    container's items. The places it can go light up green.
  - **Part of a stack:** in a stack's panel, each place under Move to also has **1** and half (the
    row itself moves all of it). Or tap the stack's **×5** to unfold it: its items follow one by
    one, and each can be tapped, picked or dragged on its own. Tap **×5 ▴** to fold it again.
  - **Several at once:** hold an item and let go without moving to pick it; then tap more to add
    or remove them. Or draw a box from empty space over the items. **N selected ›** opens the panel
    for all of them (Move to, the game's menu for the lot), dragging one of them takes them all,
    and **✕** or a tap on empty space lets go.
  - Your worn clothes are folded into one **Worn** tile: tap it to show them, tap again to fold.
  - A thin bar under an item means it's damaged (green, yellow, red); undamaged items have none.
  - Food is named the way the game names it: "Steak (Fresh, Cooked)". In the list the words are
    coloured (green fresh, yellow stale or uncooked, red rotten or burnt); on a tile a dot shows
    how fresh and the corner says Cooked, Uncooked or Burnt. While food heats in an oven or on a
    stove, a bar along its bottom fills while it cooks and turns red once it's burning.
  - Bottles, pots and other fluid containers show how full they are: "0.3 / 0.6 L" in the list, a
    bar along the tile's bottom, and in the panel what's in them in the game's colour.
  - Read books and magazines, watched tapes and heard CDs get the game's tick. Items you set
    Unwanted in the game (the item's menu, under More) are faded, like the game greys them.
  - **Take all** and **Put all** move everything, so they ask first: tap once (the button turns red
    and asks), tap again within 3 seconds to do it. Take all puts things in the bag you have open in
    your half (your main inventory if none), like the game's Loot all.
  - The two small buttons at the top right pick the layout (top and bottom, side by side, one at a
    time) and switch between grid and list.
- **Here**: what you can do where you stand (open a door, sit, drink, ...), as cards per object,
  updating by itself as you walk. Each object keeps its place; what you face goes on top after a
  second (outlined, "In front"), with the one before right under it. Disassemble and loose actions
  (Sit on ground) are in the row at the bottom; lists open over the cards. Tap an action to do it;
  greyed-out ones tell you why when tapped. While your finger is on the screen, nothing moves.
  **In a vehicle** this tab becomes **Vehicle**: speed, engine and fuel, and the car's own menu (the
  one the controller's radial menu shows) as big buttons: start the engine, headlights, heater,
  horn, windows, doors, sleep, switch seat, get out, ...
- **Deck**, your command deck: the game's speed buttons (pause, play, fast forward, wait), the time
  and your alarm (with a watch, as in the game), and the commands you choose. **Add or edit** lists
  them all: zoom in and out, search mode, flashlight, map, sit, drop bag (asks first), shout,
  **Weapons** (your hotbar: tap an item on your belt or back to draw it; on by default) and **Alarm** (set your
  watch). Switch them on and drag them into your order; a green dot shows a mode that's on.
- **Status**: your moodles along the top, like in the game (hungry, thirsty, bleeding, ...; tap one
  for what it means). Below, your body with injured parts in colour, what's wrong with each (as your
  First Aid skill lets you see it), and **Treat ›** for the game's own treatments (bandage,
  disinfect, ...). While the game is paused, the treatments wait: tap **Unpause** and they appear.
- **Map**, on saves that allow the game's minimap (Sandbox options, Map, Allow Mini-Map): the
  minimap, beside **Here** or on a tab of its own (the button in the map's top-left corner moves it).
  It follows you and only shows what you've explored, like the game's minimap. **+** and **−** zoom,
  dragging looks around (it comes back after a few seconds, or with **⌖**), and the **star** shows
  your map symbols and notes and the game's place names. To have it on every save, tick **Map on
  every save** in the game's Options → Mods → ZomboidDS.
- **Craft**: the game's recipes, by default only the ones you can make now with what's in reach
  (your bags and the containers around you); categories along the top. Tap one to see what it needs
  (✓ have, ✗ missing), what it makes and how long it takes, then **Craft** (− / + for several). Your
  character fetches the ingredients and does it, like with the game's crafting window.
- **Build**: tap the arrow on **Craft ▾** and pick Build for the game's build menu (Craft ▾ switches
  back). Things that come in versions (Wood Chair Shoddy, Poor, Good) are one tile; pick the version
  in its panel. **Place** brings up the game's cursor on the top screen: the d-pad moves it, LB/RB
  turn it, A builds, B stops. The cursor stays up for the next one. While it's up, the bottom screen
  says what you're placing (red when something runs out), with **Stop**.
- **Tailor** (Craft ▾ → Tailor): your clothes, worn ones first, then what's in your bags. Red corners
  count holes; ✕ means the game can't repair it (boots, helmets). The line above shows your needle,
  thread and fabrics (Rag, Denim Strips, Leather Strips). Tap a garment for the game's Inspect view:
  each body part it covers with its Bite and Scratch protection, holes, blood and patches. Tap a part
  for the game's choices there (Patch Hole or Add Padding with a fabric, Remove Patch); your character
  sews it, and the part shows the progress. **Inspect** in a garment's item panel opens the same view.

The **Loot / Inventory button (Y)** opens the container on the bottom screen instead of the game's
windows on the top screen, and so does opening a car's trunk (without the app, both work as usual). The gamepad keeps controlling the game
while you use the bottom screen.

## Better graphics: the Turnip driver

Zomdroid's default graphics driver can make the bottom screen flicker or show garbled patches while
the game runs. The **Mr. Purple Turnip** driver, version **T30**, fixes that on the Thor (tested):

1. Download the T30 driver from
   [MrPurple666/purple-turnip releases](https://github.com/MrPurple666/purple-turnip/releases) and
   extract the `.so` file from the zip.
2. In Zomdroid's menu: **Import/export custom driver → Import**, and select that `.so` file.
   (Zomdroid keeps one custom driver at a time.)
3. In your Zomdroid instance's settings, under **Rendering**, select **Custom driver**.

## Troubleshooting

- **"Waiting for the game" while I'm playing.** Your save doesn't have ZomboidDS enabled. Every save
  has its own mod list: saves made before installing ZomboidDS need it added. In the game's main
  menu go to **Load**, select the save, open its **Mods** list and enable **Zomboid DS** (and
  ZombieBuddy).
- **An orange message in the game says ZomboidDS can't connect.** ZombieBuddy isn't active: check
  the ZombieBuddy step in the checklist, and that ZombieBuddy is switched on in Zomdroid's
  Optimization settings.
- **On Build 42.21 that message shows even though everything is green.** The 42.21 update broke
  ZombieBuddy for every Java mod, not just ZomboidDS (ZombieBuddy
  [issue #53](https://github.com/zed-0xff/ZombieBuddy/issues/53)), and there's no official fix yet.
  The Zomdroid developer posted a workaround with a community fix from the Workshop:
  [ZombieBuddy mods on Build 42.21](https://www.reddit.com/r/zomdroid/comments/1wu7a1l/zombiebuddy_mods_on_build_4221_theres_a_fix_and/).
  On 42.20 ZombieBuddy works as before.
- **"The game is paused"** when tapping an item or Here: unpause first (Treat has its own
  **Unpause** button). The game doesn't offer these menus while paused, on either screen.
- **Speed buttons greyed out**: the game's pause menu (Esc/Start) is open. Close it first.
- **Some options open a window on the top screen** (renaming, crafting, maps): use the gamepad
  there, then continue on the bottom screen.
- **Flickering or garbled graphics on the bottom screen** while the game runs: this comes from
  Zomdroid's default graphics driver, which disturbs other apps' drawing too. Install the Turnip
  driver ([above](#better-graphics-the-turnip-driver)).
- **After updating the app**, the checklist may offer **Update** for the mod: close the game first,
  tap Update, then start the game again.

## About

The setup screen's **About** card has the source code, the open-source licences, and a Ko-fi link
if you'd like to support ZomboidDS (it never unlocks anything: everything is free).
