"""Talks to the ZomboidDS bridge in a running game, for development and testing without the app.

Forward the port first:  adb forward tcp:7786 tcp:7786   (needs: pip install websockets)

  python tools/bridge-client.py                          list containers and their items
  python tools/bridge-client.py take <item> [container]  move an item into the inventory
  python tools/bridge-client.py put <item> <container>   move an item from the inventory into a container
  python tools/bridge-client.py takeall <container>      everything from a container into the inventory

Items match by name prefix, containers by name or id (c3).
"""
import asyncio
import json
import time
import sys

import websockets


async def state(ws, seconds=3):
    seen = {}
    end = time.time() + seconds
    while time.time() < end:
        try:
            msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=max(0.1, end - time.time())))
            seen[msg["type"]] = msg["data"]
        except asyncio.TimeoutError:
            pass
    return seen


async def command(ws, name, args):
    await ws.send(json.dumps({"v": 1, "type": "command", "id": "t-1", "name": name, "args": args}))
    end = time.time() + 8
    while time.time() < end:
        try:
            msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=1))
        except asyncio.TimeoutError:
            continue
        if msg["type"] == "command_result":
            return msg["data"]
    return "NO RESULT within 8 s"


def find_container(containers, name):
    for c in containers:
        if c["name"].lower() == name.lower() or c["id"] == name:
            return c
    raise SystemExit(f"no container named {name!r}: {[c['name'] for c in containers]}")


async def main():
    async with websockets.connect("ws://127.0.0.1:7786/ws", max_size=None) as ws:
        seen = await state(ws)
        containers = seen["containers"]["containers"]
        inventory = seen["inventory"]["items"]
        args = sys.argv[1:]
        if not args:
            print("inventory:", [i["name"] for i in inventory])
            for c in containers:
                items = c.get("items")
                print(f'  {c["id"]:4} {c["kind"]:9} {c["name"]!r:30} {c.get("weight")}/{c.get("capacity")}'
                      f' {"LOCKED" if c.get("locked") else ""} {[i["name"] for i in items] if items else ""}')
            return
        inv = next(c for c in containers if c["kind"] == "inventory")
        if args[0] == "take":
            pool = containers if len(args) < 3 else [find_container(containers, args[2])]
            item = next((i for c in pool for i in c.get("items") or [] if i["name"].lower().startswith(args[1].lower())), None)
            if item is None:
                raise SystemExit("item not found in reachable containers")
            print("move", item["name"], "->", inv["name"], ":", await command(ws, "transfer", {"itemId": item["id"], "to": inv["id"]}))
        elif args[0] == "put":
            item = next((i for i in inventory if i["name"].lower().startswith(args[1].lower())), None)
            target = find_container(containers, args[2])
            print("move", item["name"], "->", target["name"], ":", await command(ws, "transfer", {"itemId": item["id"], "to": target["id"]}))
        elif args[0] == "takeall":
            source = find_container(containers, args[1])
            print("take all from", source["name"], ":", await command(ws, "transfer_all", {"from": source["id"], "to": inv["id"]}))


asyncio.run(main())
