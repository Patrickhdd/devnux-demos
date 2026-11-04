import type Anthropic from "@anthropic-ai/sdk";
import { PostgreSqlContainer, type StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { createPool, migrate, type Db } from "../src/db.js";
import { seedDemoData } from "../src/seed.js";
import type { ModelClient, StreamParams } from "../src/agent.js";

type Message = Anthropic.Beta.BetaMessage;
type Block = Anthropic.Beta.BetaContentBlock;

export const NOW = new Date("2026-11-04T15:00:00Z");

export async function startDatabase(): Promise<{ container: StartedPostgreSqlContainer; db: Db }> {
  const container = await new PostgreSqlContainer("postgres:16-alpine").start();
  const db = createPool(container.getConnectionUri());
  await migrate(db);
  await seedDemoData(db, NOW);
  return { container, db };
}

export function message(content: Block[], stop_reason: Message["stop_reason"] = "end_turn"): Message {
  return {
    id: `msg_${Math.random().toString(36).slice(2)}`,
    type: "message",
    role: "assistant",
    model: "claude-opus-5-5",
    content,
    stop_reason,
    stop_sequence: null,
    usage: { input_tokens: 100, output_tokens: 20, cache_creation_input_tokens: 0, cache_read_input_tokens: 0 },
  } as unknown as Message;
}

export const text = (t: string): Block => ({ type: "text", text: t, citations: null }) as Block;

let n = 0;
export const toolUse = (name: string, input: unknown): Block =>
  ({ type: "tool_use", id: `toolu_${++n}`, name, input }) as Block;

/** A model that plays back scripted replies and records every request it receives. */
export function scriptedModel(steps: Array<Message | ((p: StreamParams) => Message)>) {
  const requests: StreamParams[] = [];
  const errors: unknown[] = [];
  const model: ModelClient = {
    async stream(params, onText) {
      // Snapshot: the agent keeps appending to the same array.
      requests.push(structuredClone(params));
      const step = steps.shift();
      try {
        if (!step) throw new Error("scripted model ran out of replies");
        const m = typeof step === "function" ? step(params) : step;
        for (const b of m.content) if (b.type === "text") onText(b.text);
        return m;
      } catch (err) {
        // The agent would swallow this as a model failure, so keep it for the test to rethrow.
        errors.push(err);
        throw err;
      }
    },
  };
  return {
    model,
    requests,
    /** Fails the test if any scripted assertion failed or a reply was never used. */
    assertDone() {
      if (errors.length) throw errors[0];
      if (steps.length) throw new Error(`${steps.length} scripted replies were not used`);
    },
  };
}

/** The tool_result blocks the agent sent in the last request. */
export function lastToolResults(params: StreamParams) {
  const last = params.messages[params.messages.length - 1];
  if (!Array.isArray(last.content)) return [];
  return last.content.filter((b) => b.type === "tool_result") as Anthropic.Beta.BetaToolResultBlockParam[];
}

export function parseResult(r: Anthropic.Beta.BetaToolResultBlockParam): any {
  return JSON.parse(r.content as string);
}
