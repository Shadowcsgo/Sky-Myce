// Adds the admin-only `/skyhawk users` subcommand to the Backend Bot's existing /skyhawk command, leaving every other
// subcommand untouched. Usage: node scripts/register-skyhawk-users.mjs <file containing the Backend Bot token>
import { readFile } from "node:fs/promises";

const application = "1552590804810342400", guild = "1552053091493486594";
const token = (await readFile(process.argv[2], "utf8")).trim();
if (!/^[A-Za-z0-9._-]{50,100}$/.test(token)) throw new Error("Expected a Discord bot token file");
const headers = { Authorization: `Bot ${token}`, "Content-Type": "application/json" };
const users = { type: 1, name: "users", description: "Discord accounts that use the mod, most recently seen first",
  options: [{ type: 4, name: "page", description: "Page (8 accounts each)", min_value: 1, max_value: 100, required: false }] };

for (const scope of [`/applications/${application}/guilds/${guild}/commands`, `/applications/${application}/commands`]) {
  const response = await fetch(`https://discord.com/api/v10${scope}`, { headers });
  if (!response.ok) throw new Error(`Listing commands failed: ${response.status}`);
  const skyhawk = (await response.json()).find(command => command.name === "skyhawk");
  if (!skyhawk) continue;
  const options = (skyhawk.options ?? []).filter(option => option.name !== "users");
  const patched = await fetch(`https://discord.com/api/v10${scope}/${skyhawk.id}`, { method: "PATCH", headers,
    body: JSON.stringify({ options: [...options, users] }) });
  if (!patched.ok) throw new Error(`Updating /skyhawk failed: ${patched.status} ${await patched.text()}`);
  const subs = (await patched.json()).options.map(option => option.name);
  console.log(`/skyhawk updated (${scope.includes("guilds") ? "guild" : "global"}): ${subs.join(", ")}`);
  process.exit(0);
}
throw new Error("No /skyhawk command found for the Backend Bot");
