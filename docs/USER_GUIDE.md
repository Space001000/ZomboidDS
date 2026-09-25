# ZomboidDS: user guide

Play Project Zomboid on the top screen of your AYN Thor and manage your inventory, health, crafting
and vehicle on the bottom screen.

## What you need

- An **AYN Thor** (other dual-screen handhelds may work, but aren't tested).
- **Your own copy of Project Zomboid** (Build 42): ZomboidDS is a mod, it doesn't include the game.
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
  floor, ...). In **Split** view yours are on top and the ones around you below; **Single** shows one
  container at a time. The open container's tab shows its name and weight; the others show the
  game's icon (tap one to open it). Opening a container's tab outlines it in the game.
  - Tap an item for **Take** / **Put in …** / **Move to…**, quick actions (equip, wear, drop) and
    the game's own menu for it (read, eat, apply, craft, ...).
  - Your worn clothes are folded into one **Worn** tile: tap it to show them, tap again to fold.
  - A thin bar under an item means it's damaged (green, yellow, red); undamaged items have none.
    Food has a dot: green fresh, yellow stale, red rotten.
  - **Take all** and **Put all** move everything, so they ask first: tap once (the button turns red
    and asks), tap again within 3 seconds to do it.
  - The two small buttons at the top right switch the layout and between grid and list.
- **Deck**: the game's speed buttons (pause, play, fast forward, wait), and **Here**: what you can do
  where you stand (open a door, sit, drink, ...), updating by itself as you walk. Tap an action to do
  it; greyed-out ones tell you why when tapped.
- **Status**: your moodles along the top, like in the game (hungry, thirsty, bleeding, ...; tap one
  for what it means). Below, your body with injured parts in colour, what's wrong with each (as your
  First Aid skill lets you see it), and **Treat ›** for the game's own treatments (bandage,
  disinfect, ...). While the game is paused, the treatments wait: tap **Unpause** and they appear.
- **Craft**: the game's recipes, by default only the ones you can make now with what's in reach
  (your bags and the containers around you); categories along the top. Tap one to see what it needs
  (✓ have, ✗ missing), what it makes and how long it takes, then **Craft** (− / + for several). Your
  character fetches the ingredients and does it, like with the game's crafting window.
- **Vehicle**: appears automatically when you get into a vehicle: speed, fuel and engine.

The **Loot / Inventory button (Y)** opens the container on the bottom screen instead of the game's
windows on the top screen (without the app, Y works as usual). The gamepad keeps controlling the game
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
