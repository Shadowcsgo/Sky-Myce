// Posts Sky-Myce release notes and the feature list to Discord as the User Bot, with plain REST calls (no Gateway
// connection, so it never competes with the production Gateway). Channels are found by name in the Sky-Myce guild:
// creating #changelog and #features (the bot needs View, Send Messages and Embed Links there) is all the setup.
//
//   node relay/scripts/discord-posts.mjs <env file with USER_BOT_TOKEN> changelog <version>
//   node relay/scripts/discord-posts.mjs <env file with USER_BOT_TOKEN> features FEATURES.md
//
// Both are idempotent: a version already posted is edited in place when its text changed, and the feature list edits
// its earlier messages in place. FEATURES.md is the polished copy of `./gradlew featureList` (build/feature-list.md).
import { readFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const guild = "1552053091493486594";
const repository = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const [envPath, action, argument] = process.argv.slice(2);
const fail = message => { console.error(message); process.exit(1); };
if (envPath === "--check") {
  // Offline self-check of the changelog parser (no token or network).
  const parsed = changelogSection("# T\n## 1.0.0-beta-002 (2026-11-02)\n- One\n  more\n- Two\n## 1.0.0-beta-001\n- Old\n", "1.0.0-beta-002");
  if (JSON.stringify(parsed) !== JSON.stringify({ date: "2026-11-02", changes: ["One more", "Two"] })) fail("changelog parser check failed");
  console.log("discord-posts self-check passed"); process.exit(0);
}
if (!envPath || !["changelog", "features"].includes(action) || !argument) fail("Usage: discord-posts.mjs <env file> changelog <version> | features <feature-list.md>");

const env = Object.fromEntries((await readFile(envPath, "utf8")).split(/\r?\n/).filter(line => /^[A-Z_]+=/.test(line))
  .map(line => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1).trim()]));
const token = env.USER_BOT_TOKEN;
if (!/^[A-Za-z0-9._-]{50,100}$/.test(token ?? "")) fail("The env file has no USER_BOT_TOKEN");

async function api(path, init = {}) {
  for (let attempt = 0; attempt < 5; attempt++) {
    const response = await fetch(`https://discord.com/api/v10${path}`, { ...init,
      headers: { Authorization: `Bot ${token}`, "Content-Type": "application/json" } });
    if (response.status === 429) {
      const wait = ((await response.json()).retry_after ?? 1) * 1000 + 100;
      await new Promise(done => setTimeout(done, wait)); continue;
    }
    // Never echo the request or response headers: they carry the token.
    if (!response.ok) fail(`${init.method ?? "GET"} ${path.replace(/\d{17,20}/g, "<id>")} failed: ${response.status} ${(await response.text()).slice(0, 200)}`);
    return response.status === 204 ? null : response.json();
  }
  fail(`Discord kept rate limiting ${path.replace(/\d{17,20}/g, "<id>")}`);
}

const channels = await api(`/guilds/${guild}/channels`);
const me = await api("/users/@me");
const member = await api(`/guilds/${guild}/members/${me.id}`), roles = await api(`/guilds/${guild}/roles`);
const NEEDED = { "View Channel": 1n << 10n, "Send Messages": 1n << 11n, "Embed Links": 1n << 14n, "Read Message History": 1n << 16n };
/** The bot's effective permissions in a channel (Discord's role + overwrite order); administrators get everything. */
function permissionsIn(target) {
  let bits = roles.filter(r => r.id === guild || member.roles.includes(r.id)).reduce((all, r) => all | BigInt(r.permissions), 0n);
  if (bits & 8n) return ~0n;
  const overwrites = target.permission_overwrites ?? [], everyone = overwrites.find(o => o.id === guild);
  if (everyone) bits = (bits & ~BigInt(everyone.deny)) | BigInt(everyone.allow);
  const own = overwrites.filter(o => o.type === 0 && member.roles.includes(o.id));
  bits = (bits & ~own.reduce((d, o) => d | BigInt(o.deny), 0n)) | own.reduce((a, o) => a | BigInt(o.allow), 0n);
  const user = overwrites.find(o => o.type === 1 && o.id === me.id);
  return user ? (bits & ~BigInt(user.deny)) | BigInt(user.allow) : bits;
}
// Without Read Message History the channel looks empty, so the script could not find (and would duplicate) its own posts.
const channel = name => {
  const target = channels.find(c => c.type === 0 && c.name === name)
    ?? fail(`No #${name} text channel yet. Create it in the Sky-Myce server and let MouseBot Frontend send messages there.`);
  const bits = permissionsIn(target), missing = Object.entries(NEEDED).filter(([, bit]) => !(bits & bit)).map(([label]) => label);
  if (missing.length) fail(`MouseBot Frontend needs ${missing.join(", ")} in #${name}`);
  return target;
};
const ownMessages = async id => (await api(`/channels/${id}/messages?limit=100`)).filter(m => m.author.id === me.id).reverse();
const ACCENT = 0x4fc3f7;

/** One `## <version> (<date>)` section of CHANGELOG.md, or null. */
function changelogSection(text, version) {
  const lines = text.split(/\r?\n/), start = lines.findIndex(line => line.startsWith(`## ${version}`));
  if (start < 0) return null;
  const date = /\(([^)]*)\)/.exec(lines[start])?.[1] ?? "";
  const changes = [];
  for (const line of lines.slice(start + 1)) {
    if (line.startsWith("## ")) break;
    if (line.startsWith("- ")) changes.push(line.slice(2).trim());
    else if (line.startsWith("  ") && line.trim() && changes.length) changes[changes.length - 1] += " " + line.trim();
  }
  return { date, changes };
}

if (action === "changelog") {
  const section = changelogSection(await readFile(resolve(repository, "CHANGELOG.md"), "utf8"), argument);
  if (!section?.changes.length) fail(`CHANGELOG.md has no changes for ${argument}`);
  const target = channel("changelog"), title = `Sky-Myce ${argument}`;
  const embed = { title, color: ACCENT, description: section.changes.map(change => `• ${change}`).join("\n").slice(0, 4000),
    footer: { text: `${section.date ? section.date + " · " : ""}Updates install from the game; see What's New in the Daily Digest` } };
  const copies = (await ownMessages(target.id)).filter(m => m.embeds?.some(e => e.title === title));
  // Keep the newest copy of this version and remove any older duplicates.
  for (const extra of copies.slice(0, -1)) await api(`/channels/${target.id}/messages/${extra.id}`, { method: "DELETE" });
  const posted = copies.at(-1);
  if (posted?.embeds[0].description === embed.description) { console.log(`${title} is already posted`); process.exit(0); }
  // A reworded entry edits the earlier post rather than posting the version twice.
  await api(`/channels/${target.id}/messages${posted ? `/${posted.id}` : ""}`, { method: posted ? "PATCH" : "POST",
    body: JSON.stringify({ allowed_mentions: { parse: [] }, embeds: [embed] }) });
  console.log(`${posted ? "Updated" : "Posted"} ${title} in #changelog`);
} else {
  // One message per `# Category` block keeps every embed under Discord's size limits.
  const blocks = (await readFile(argument, "utf8")).split(/^# /m).filter(block => block.trim()).map(block => {
    const [heading, ...body] = block.split(/\r?\n/);
    return { title: heading.trim(), color: ACCENT, description: body.join("\n").trim().slice(0, 4000) };
  });
  if (blocks.length < 3) fail("The feature list looks empty; run ./gradlew featureList first");
  const target = channel("features"), existing = await ownMessages(target.id);
  const body = embed => JSON.stringify({ allowed_mentions: { parse: [] }, embeds: [embed] });
  if (existing.length === blocks.length) {
    for (const [index, embed] of blocks.entries()) await api(`/channels/${target.id}/messages/${existing[index].id}`, { method: "PATCH", body: body(embed) });
    console.log(`Updated ${blocks.length} feature list messages in #features`);
  } else {
    for (const message of existing) await api(`/channels/${target.id}/messages/${message.id}`, { method: "DELETE" });
    for (const embed of blocks) await api(`/channels/${target.id}/messages`, { method: "POST", body: body(embed) });
    console.log(`Posted ${blocks.length} feature list messages to #features`);
  }
}
