# ZomboidDS: user guide (draft)

Play Project Zomboid on the top screen of your AYN Thor and manage your inventory, health and
vehicle on the bottom screen.

> Draft: written while developing. Screenshots and a download link come with the first release.

## What you need

- An **AYN Thor** (other dual-screen handhelds may work, but aren't tested).
- **[Zomdroid](https://github.com/udarmolota/zomdroid)** with **Project Zomboid Build 42** installed
  and working. Use this version of Zomdroid (udarmolota's): it's the one that's still maintained and
  the one ZomboidDS is built and tested with. The original Zomdroid stopped development in 2025.
- The **ZomboidDS Companion** app (`ZomboidDS.apk`). It contains the ZomboidDS mod; you don't need to
  download the mod separately.

## 1. Install the companion app

Install `ZomboidDS.apk` on the Thor (open the downloaded file and allow installing apps if Android
asks), then open **ZomboidDS**. A setup checklist guides you through the rest; every step shows ✓
when it's done.

## 2. Follow the setup checklist

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

- **Inventory**: tap an item for quick actions (equip, wear, drop) and the game's own menu for it
  (read, eat, apply, craft, ...). Switch between **Grid** and **List** at the top.
- **Status**: health, hunger, thirst, fatigue and endurance.
- **Vehicle**: appears automatically when you get into a vehicle: speed, fuel and engine.

The gamepad keeps controlling the game while you use the bottom screen.

## Troubleshooting

- **"Waiting for the game" while I'm playing.** Your save doesn't have ZomboidDS enabled. Every save
  has its own mod list: saves made before installing ZomboidDS need it added. In the game's main
  menu go to **Load**, select the save, open its **Mods** list and enable **Zomboid DS** (and
  ZombieBuddy).
- **An orange message in the game says ZomboidDS can't connect.** ZombieBuddy isn't active: check
  the ZombieBuddy step in the checklist, and that ZombieBuddy is switched on in Zomdroid's
  Optimization settings.
- **"The game is paused"** when tapping an item: unpause first. The game doesn't allow item actions
  while paused, on either screen.
- **Some options open a window on the top screen** (renaming, crafting, maps): use the gamepad
  there, then continue on the bottom screen.
- **After updating the app**, the checklist may offer **Update** for the mod: close the game first,
  tap Update, then start the game again.
