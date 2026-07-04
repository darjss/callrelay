// Verification script for the CallRelay Worker deployed at calls.darjs.dev.
//
// Reads POST_TOKEN and GET_TOKEN from env vars, falling back to .dev.vars.
// Run with: npx tsx scripts/test-relay.ts
//
//   export POST_TOKEN=... GET_TOKEN=...
//   npx tsx scripts/test-relay.ts

import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const BASE_URL = process.env.RELAY_URL ?? "http://localhost:8787";

function loadDotVars(): Record<string, string> {
  const path = resolve(import.meta.dirname, "..", ".dev.vars");
  let text: string;
  try {
    text = readFileSync(path, "utf8");
  } catch {
    return {};
  }
  const out: Record<string, string> = {};
  for (const line of text.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq === -1) continue;
    out[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim();
  }
  return out;
}

const dotVars = loadDotVars();
const POST_TOKEN = process.env.POST_TOKEN ?? dotVars.POST_TOKEN ?? "";
const GET_TOKEN = process.env.GET_TOKEN ?? dotVars.GET_TOKEN ?? "";

if (!POST_TOKEN || !GET_TOKEN) {
  console.error(
    "POST_TOKEN and GET_TOKEN must be set (env vars or .dev.vars).",
  );
  process.exit(1);
}

let passed = 0;
let failed = 0;

function assert(condition: boolean, message: string): void {
  if (condition) {
    passed += 1;
    console.log(`  PASS: ${message}`);
  } else {
    failed += 1;
    console.error(`  FAIL: ${message}`);
  }
}

async function postEvents(
  body: unknown,
  token: string | null,
): Promise<Response> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (token !== null) headers["Authorization"] = `Bearer ${token}`;
  return fetch(`${BASE_URL}/events`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
}

async function getEvents(
  since: number,
  token: string | null,
): Promise<Response> {
  const headers: Record<string, string> = {};
  if (token !== null) headers["Authorization"] = `Bearer ${token}`;
  return fetch(`${BASE_URL}/events?since=${since}`, { headers });
}

async function main(): Promise<void> {
  console.log(`Relay verification against ${BASE_URL}`);

  // 1. Auth rejection.
  console.log("\n[1] Auth rejection");
  {
    const r = await postEvents({ callId: "x" }, null);
    assert(r.status === 401, `POST without token → 401 (got ${r.status})`);
  }
  {
    const r = await getEvents(0, "wrong-token");
    assert(r.status === 401, `GET with wrong token → 401 (got ${r.status})`);
  }

  const callId = crypto.randomUUID();
  const startedTimestamp = Date.now();

  // 2. Post a Call Started Event.
  console.log("\n[2] Post Call Started Event");
  let startedSeq = 0;
  {
    const r = await postEvents(
      {
        callId,
        type: "started",
        direction: "incoming",
        callerName: "Mom",
        callerNumber: "+15551234567",
        timestamp: startedTimestamp,
      },
      POST_TOKEN,
    );
    assert(r.status === 202, `POST started → 202 (got ${r.status})`);
    const data = (await r.json()) as { seq: number };
    assert(
      typeof data.seq === "number" && data.seq > 0,
      `response contains seq > 0 (got ${data.seq})`,
    );
    startedSeq = data.seq;
  }

  // 3. GET since=0, confirm the Started event appears.
  console.log("\n[3] GET /events?since=0");
  {
    const r = await getEvents(0, GET_TOKEN);
    assert(r.status === 200, `GET → 200 (got ${r.status})`);
    const data = (await r.json()) as {
      events: { seq: number; callId: string; type: string }[];
      nextCursor: number;
    };
    assert(
      Array.isArray(data.events),
      `events is an array (got ${typeof data.events})`,
    );
    assert(
      data.events.some((e) => e.seq === startedSeq && e.callId === callId),
      "Started event appears in results",
    );
    assert(
      data.nextCursor >= startedSeq,
      `nextCursor >= startedSeq (got ${data.nextCursor})`,
    );
  }

  // 4. Post a Call Ended Event with the same callId.
  console.log("\n[4] Post Call Ended Event");
  let endedSeq = 0;
  {
    const r = await postEvents(
      {
        callId,
        type: "ended",
        direction: "incoming",
        callerName: "Mom",
        callerNumber: "+15551234567",
        outcome: "answered",
        timestamp: Date.now(),
      },
      POST_TOKEN,
    );
    assert(r.status === 202, `POST ended → 202 (got ${r.status})`);
    const data = (await r.json()) as { seq: number };
    assert(
      typeof data.seq === "number" && data.seq > startedSeq,
      `ended seq > started seq (got ${data.seq})`,
    );
    endedSeq = data.seq;
  }

  // 5. GET since=startedSeq, confirm only the Ended event appears.
  console.log("\n[5] GET /events?since=<startedSeq>");
  {
    const r = await getEvents(startedSeq, GET_TOKEN);
    assert(r.status === 200, `GET → 200 (got ${r.status})`);
    const data = (await r.json()) as {
      events: { seq: number; callId: string; type: string; outcome?: string }[];
      nextCursor: number;
    };
    assert(
      data.events.every((e) => e.seq > startedSeq),
      "all returned events have seq > startedSeq",
    );
    const ended = data.events.find((e) => e.seq === endedSeq);
    assert(ended !== undefined, "Ended event appears in results");
    assert(
      ended?.outcome === "answered",
      `Ended event carries outcome (got ${ended?.outcome})`,
    );
    assert(
      !data.events.some((e) => e.seq === startedSeq),
      "Started event is NOT in the since=startedSeq result",
    );
    assert(
      data.nextCursor >= endedSeq,
      `nextCursor >= endedSeq (got ${data.nextCursor})`,
    );
  }

  // 6. Validation failure: missing required field.
  console.log("\n[6] Validation failure");
  {
    const r = await postEvents(
      { type: "started", direction: "incoming", callerNumber: "+1", timestamp: 1 },
      POST_TOKEN,
    );
    assert(r.status === 400, `POST missing callId → 400 (got ${r.status})`);
  }
  {
    const r = await postEvents(
      {
        callId: "x",
        type: "ended",
        direction: "incoming",
        callerNumber: "+1",
        timestamp: 1,
      },
      POST_TOKEN,
    );
    assert(
      r.status === 400,
      `POST ended without outcome → 400 (got ${r.status})`,
    );
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

await main();
