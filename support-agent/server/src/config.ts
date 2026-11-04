export interface Config {
  port: number;
  databaseUrl: string;
  /** Anthropic API key. Without it the chat endpoint explains how to enable the agent. */
  anthropicApiKey?: string;
  model: string;
  effort: "low" | "medium" | "high" | "xhigh" | "max";
  /** Secret used to sign demo customer sessions. */
  sessionSecret: string;
  /** Shared key for the staff console. */
  staffKey: string;
  seedDemoData: boolean;
  /** Model turns allowed for one customer message before the agent stops. */
  maxTurns: number;
}

const efforts = ["low", "medium", "high", "xhigh", "max"] as const;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const effort = (env.AGENT_EFFORT ?? "medium") as Config["effort"];
  if (!efforts.includes(effort)) throw new Error(`AGENT_EFFORT must be one of ${efforts.join(", ")}`);
  return {
    port: Number(env.PORT ?? 8080),
    databaseUrl: env.DATABASE_URL ?? "postgres://support:support@localhost:5432/support",
    anthropicApiKey: env.ANTHROPIC_API_KEY || undefined,
    model: env.AGENT_MODEL ?? "claude-opus-5-5",
    effort,
    sessionSecret: env.SESSION_SECRET ?? "local-demo-session-secret-change-me",
    staffKey: env.STAFF_KEY ?? "demo-staff",
    seedDemoData: (env.SEED_DEMO_DATA ?? "true") === "true",
    maxTurns: Number(env.AGENT_MAX_TURNS ?? 8),
  };
}
