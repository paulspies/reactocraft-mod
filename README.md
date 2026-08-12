# ReactoCraft

Radiation gameplay for Paul's Minecraft server, `ReactoCraft`, NeoForge 21.1.248 on Minecraft 1.21.1.

Built because the Radioactive mod's world radiation is unusable on a dedicated server: it sweeps a
cube around every player every tick with no spatial index, which measured **136 ms of a 50 ms tick
budget** on 2026-08-09. Its block radiation is off and stays off. Its **inventory** radiation is
cheap and stays on. Everything else is ours.

## What it adds

**Four effects** — Rad Resistance, Rad Weakness, Radiation, Weak Radiation.

**Eight potions**, brewed on a real brewing stand, ingredients from Create Nuclear:

| From | Add | Result | Time |
|---|---|---|---|
| Awkward | Lead Nugget | Rad Resistance | 3:00 |
| Rad Resistance | Redstone | Rad Resistance | 8:00 |
| Rad Resistance | Glowstone | Rad Resistance II | 1:30 |
| Rad Resistance | Fermented Spider Eye | Rad Weakness | 3:00 |
| Awkward | Uranium Powder | Radiation | 3:00 |
| Radiation | Redstone | Radiation | 8:00 |
| Radiation | Glowstone | Radiation II | 1:30 |
| Radiation | Fermented Spider Eye | Weak Radiation | 1:30 |

Gunpowder and dragon's breath produce the splash and lingering forms. Those are registered
automatically by `addMix`, exactly as they are for vanilla potions.

**Symptom staging.** Exposure builds while you are being dosed and decays when you are not.
Dizziness first, then damage, then blindness. Damage ramps rather than arriving all at once.

**Shielding**, which all adds up and caps at 100%:

- Rad Resistance potion, 50% or 75%
- A full four-piece anti-radiation suit, 100%
- Shielded blocks, 20% per shielded side, six sides checked

**The sealed room test** is the six-direction check: floor, ceiling and four walls. A fully sealed
lead room is 120%, capped at 100%.

**Curing irradiated animals.** Right-click Create Nuclear's Irradiated Cat, Chicken or Wolf with a
milk bucket and it becomes the healthy vanilla animal. It is replaced, never killed, and keeps its
name, age and health fraction.

## Both sides

The jar goes on the **server and every client**. It is not optional. Effects and potions are entries
in synced registries, so a client without the jar cannot decode what the server sends and is
dropped at login. The engine, the curing and the config are server-side code paths that never run
on a client, but they ship in the same jar.

Everyone's jar must match the server's version.

## Tuning without a rebuild

Every number lives in `config/reactocraft-server.toml` on the server. Edit it, restart, done. No
rebuild and no client reinstall. A rebuild is only needed to add a new *thing*, not to change a
number.

## Relationship to the `rad` datapack

The datapack now **places zones and nothing else**. Its tick loop is deliberately unregistered in
`Deploy-Rad.ps1`, because if both ran, every zone would dose the player twice under two different
sets of shielding rules.

Zones are `area_effect_cloud` entities tagged `rad` plus `rad_<strength>`. The mod reads those tags.
Using entities rather than block scans is the whole reason this costs nothing: the game already
indexes entities.

## Building

```
./gradlew build      ->  build/libs/reactocraft-<version>.jar
```

Java 21. The Gradle wrapper handles the rest.
