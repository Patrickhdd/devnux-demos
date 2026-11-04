import { loadConfig } from "./config.js";
import { createPool, migrate } from "./db.js";
import { seedDemoData } from "./seed.js";
import { anthropicModel } from "./agent.js";
import { buildApp } from "./app.js";

const config = loadConfig();
const db = createPool(config.databaseUrl);

for (let attempt = 1; ; attempt++) {
  try {
    await migrate(db);
    break;
  } catch (err) {
    if (attempt >= 20) throw err;
    console.log(`Waiting for the database (attempt ${attempt})...`);
    await new Promise((r) => setTimeout(r, 1500));
  }
}
if (config.seedDemoData) await seedDemoData(db);

const model = config.anthropicApiKey ? anthropicModel(config.anthropicApiKey) : undefined;
if (!model) console.warn("ANTHROPIC_API_KEY is not set: the chat will explain how to enable the assistant.");

const app = buildApp({ db, config, model });
await app.listen({ host: "0.0.0.0", port: config.port });

for (const signal of ["SIGINT", "SIGTERM"] as const) {
  process.on(signal, async () => {
    await app.close();
    await db.end();
    process.exit(0);
  });
}
