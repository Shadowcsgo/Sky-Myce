# Sky-Myce changelog

Newest first. Every release gets a `## <version> (<date>)` section with one line per change you can see in game. The
Daily Digest shows this whole file, the update prompt shows the section for the new version, and a copy goes to Discord.

## 2.8.0-beta-050 (2026-11-05)
- Pickobulus Commission Spot is now called Pickobulus Helper. While you hold a tool with Pickobulus, it shows exactly which blocks a blast would break at the block you are aiming at (up to 20 blocks away) and how many. The calculation comes from Skyblocker's Pickobulus helper. It works in the Dwarven Mines, Glacite Tunnels and Great Glacite Lake, Crystal Hollows, Mineshafts, Gold Mines and Deep Caverns, and no longer needs an open commission.
- The marker for the best spot for your open block commissions stays, with its arrow and the Pickobulus Direction widget, but it now picks the spot with that same exact calculation, so its block count is exact instead of an estimate.
- The Pickobulus Radius setting is gone; it is no longer needed.
- The helper only shows while Pickobulus is off cooldown. It reads the Pickaxe Ability line in the tab list and falls back on the chat messages if that line is turned off.
- Fixed: XP Per Hour and Pickobulus Direction could not be placed in the HUD editor until they had something to show. They now show an example there when their feature is on.
- Fixed: XP Per Hour now always shows a HOTM XP line on mining islands (a dash until the first reward), and claimed commissions are recognised more reliably.

## 2.8.0-beta-049 (2026-11-04)
- Wool Gemstones: gemstone panes keep their thin pane shape and just use the wool texture instead of being drawn as full blocks. Glass blocks are still full wool.
- Pickobulus Commission Spot only runs while you hold a tool with the Pickobulus ability selected.
- New Pickobulus Direction widget: when the spot is off screen, behind you or anywhere you aren't looking, it shows an arrow toward it, how many blocks a blast would break and how far away it is.
- The Pickobulus marker looks better: the outline glides to a new spot and gently pulses, and the blocks ripple in from the centre with a soft wave. Both follow the Animations setting.
- New Pickobulus Color setting changes the colour of the marker, blocks, arrow and label. Settings now have a colour picker for this.
- New XP Per Hour widget (under General, off by default): estimated HOTM XP and Skill XP per hour over the last 10 minutes. HOTM XP counts rewards shown in chat and commissions you claim.
- Updating no longer keeps old copies of the mod. Once the new version has started, the old one is deleted from the .sky-myce-updates folder in your game directory, along with copies left by earlier updates and unused downloads, which frees up disk space.

## 2.8.0-beta-048 (2026-11-04)
- Wool Gemstones (off by default, under Mining): gemstone glass and glass panes in the Crystal Hollows, Dwarven Mines and Mineshafts are drawn as solid wool of the same colour so they stand out. Only the look changes; a pane still has its thin hitbox.

## 2.8.0-beta-047 (2026-11-02)
- Custom names now wrap in chat and in the NPC dialogue box instead of running off the edge, and the box no longer shows them twice.
- The menu takes up much less space: a slimmer sidebar, smaller Home tiles, each feature on one line with its on/off switch, and each feature's settings as divided rows in a single panel.
- In settings rows the control now sits next to the setting name, Pin is an icon, and Reset only shows on settings you have changed.
- Presets: Best performance, Balanced and Best looks (they set graphics, animations and chat media), plus a choice of NPC lines in the box or in chat. You'll find them on the Settings page or through search.
- Menu search now finds presets and commands too. Pick a command and it is placed in your chat box for you.
- What's New: Home lists what changed in this version, and the Daily Digest carries the full changelog.

## 2.8.0-beta-046 (2026-11-02)
- Commission Highlight: the blocks your open commissions need are highlighted and slayer mobs get an outline, Mineshafts included.
- Spread now only counts blocks that actually broke while you were mining them, only with a pickaxe or drill, and never inside the Catacombs. Its procs are animated too.
- Pickobulus now scans the veins around you and points you to the best one.

## 2.8.0-beta-045 (2026-11-02)
- /sm graphics now tells you your FPS, p95 frame time, how much CPU the menu itself uses and whether background blur is on.

## 2.8.0-beta-044 (2026-11-02)
- The Greenhouse now has its own workspace with Overview, Mutations, Shopping, Unlock guide and History tabs.
- Click a cell on the greenhouse grid to lock it. Plot cards have Plan best and Remove buttons, and Remove asks you to confirm first.

## 2.8.0-beta-043 (2026-11-02)
- Settings show the Basic options first. Advanced and Expert options stay folded away until you open them or search for them.
- Every setting gets a Reset button whenever it is not at its default.

## 2.8.0-beta-042 (2026-11-02)
- There is a new Animations setting, separate from the graphics mode.
- Home now shows search, your tools, favorites and the features you used recently.

## 2.8.0-beta-041 (2026-11-02)
- Text stays sharp in every graphics mode, and repainting the menu now costs about 4.5x less.
- The menu has six categories (Dungeons, Gameplay, Economy, Community, Appearance, Settings), and hovering one opens its submenu.
- /sm help is now searchable.

## 2.8.0-beta-040 (2026-11-01)
- Greenhouse: a Thunderling stops at 16,000 charge (stage 8) until it is discharged, and the reminders show you when.
