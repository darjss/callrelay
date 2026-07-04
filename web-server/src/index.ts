import { Hono } from "hono";

// A Call Event stored by the Relay. `seq` is assigned by the DO; `outcome`
// is only present on Call Ended Events.
interface StoredEvent {
  seq: number;
  callId: string;
  type: "started" | "ended";
  direction: "incoming" | "outgoing";
  callerName: string;
  callerNumber: string;
  outcome?: "answered" | "missed" | "declined" | "error";
  timestamp: number;
}

// Payload sent by the Android App, before `seq` is assigned.
interface CallEventInput {
  callId: string;
  type: "started" | "ended";
  direction: "incoming" | "outgoing";
  callerName: string;
  callerNumber: string;
  outcome?: "answered" | "missed" | "declined" | "error";
  timestamp: number;
}

interface Env {
  POST_TOKEN: string;
  GET_TOKEN: string;
  CALLRELAY_DO: DurableObjectNamespace;
}

const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;

// Single global instance; all Call Events share one strongly-consistent log.
const RELAY_NAME = "global";

export class CallRelayDO implements DurableObject {
  private sql: SqlStorage;

  constructor(state: DurableObjectState, _env: Env) {
    this.sql = state.storage.sql;
    this.sql.exec(`CREATE TABLE IF NOT EXISTS events (
      seq INTEGER PRIMARY KEY AUTOINCREMENT,
      callId TEXT,
      type TEXT,
      direction TEXT,
      callerName TEXT,
      callerNumber TEXT,
      outcome TEXT,
      timestamp INTEGER,
      createdAt INTEGER
    )`);
  }

  async putEvent(event: CallEventInput): Promise<number> {
    this.sql.exec(
      `INSERT INTO events (callId, type, direction, callerName, callerNumber, outcome, timestamp, createdAt)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      event.callId,
      event.type,
      event.direction,
      event.callerName,
      event.callerNumber,
      event.outcome ?? null,
      event.timestamp,
      Date.now(),
    );
    const seq = this.sql.exec(`SELECT last_insert_rowid() AS seq`).one().seq as number;
    // 7-day TTL cleanup on each POST.
    this.sql.exec(`DELETE FROM events WHERE createdAt < ?`, Date.now() - SEVEN_DAYS_MS);
    return seq;
  }

  async getEvents(since: number): Promise<{ events: StoredEvent[]; nextCursor: number }> {
    const rows = [
      ...this.sql.exec(
        `SELECT seq, callId, type, direction, callerName, callerNumber, outcome, timestamp
         FROM events WHERE seq > ? ORDER BY seq ASC`,
        since,
      ),
    ];
    const events: StoredEvent[] = rows.map((r) => {
      const e: StoredEvent = {
        seq: r.seq as number,
        callId: r.callId as string,
        type: r.type as "started" | "ended",
        direction: r.direction as "incoming" | "outgoing",
        callerName: (r.callerName as string) ?? "",
        callerNumber: r.callerNumber as string,
        timestamp: r.timestamp as number,
      };
      if (r.outcome !== null) {
        e.outcome = r.outcome as "answered" | "missed" | "declined" | "error";
      }
      return e;
    });
    const nextCursor =
      events.length > 0 ? events[events.length - 1].seq : since;
    return { events, nextCursor };
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "POST" && url.pathname === "/events") {
      const event = (await request.json()) as CallEventInput;
      const seq = await this.putEvent(event);
      return Response.json({ seq }, { status: 202 });
    }
    if (request.method === "GET" && url.pathname === "/events") {
      const since = Number(url.searchParams.get("since") ?? "0");
      const result = await this.getEvents(since);
      return Response.json(result, { status: 200 });
    }
    return new Response("Not Found", { status: 404 });
  }
}

const app = new Hono<{ Bindings: Env }>();

app.get("/", (c) => c.text("Hello Hono!"));
app.get("/health", (c) => c.text("ok", 200));

function bearerToken(request: Request): string | null {
  const auth = request.headers.get("Authorization");
  if (!auth || !auth.startsWith("Bearer ")) return null;
  return auth.slice(7);
}

app.post("/events", async (c) => {
  const token = bearerToken(c.req.raw);
  if (token === null || token !== c.env.POST_TOKEN) {
    return c.json({ error: "unauthorized" }, 401);
  }

  let body: CallEventInput;
  try {
    body = (await c.req.json()) as CallEventInput;
  } catch {
    return c.json({ error: "invalid json" }, 400);
  }

  const required: (keyof CallEventInput)[] = [
    "callId",
    "type",
    "direction",
    "callerNumber",
    "timestamp",
  ];
  for (const field of required) {
    if (body[field] === undefined || body[field] === null) {
      return c.json({ error: `missing field: ${field}` }, 400);
    }
  }
  if (body.type !== "started" && body.type !== "ended") {
    return c.json({ error: "invalid type" }, 400);
  }
  if (body.type === "ended" && !body.outcome) {
    return c.json({ error: "outcome required for ended events" }, 400);
  }
  if (typeof body.timestamp !== "number") {
    return c.json({ error: "timestamp must be a number" }, 400);
  }

  const id = c.env.CALLRELAY_DO.idFromName(RELAY_NAME);
  const stub = c.env.CALLRELAY_DO.get(id);
  const resp = await stub.fetch("https://relay/events", {
    method: "POST",
    body: JSON.stringify(body),
    headers: { "Content-Type": "application/json" },
  });
  const data = (await resp.json()) as { seq: number };
  return c.json({ seq: data.seq }, 202);
});

app.get("/events", async (c) => {
  const token = bearerToken(c.req.raw);
  if (token === null || token !== c.env.GET_TOKEN) {
    return c.json({ error: "unauthorized" }, 401);
  }

  const sinceParam = c.req.query("since") ?? "0";
  const since = Number(sinceParam);
  if (!Number.isFinite(since)) {
    return c.json({ error: "invalid since cursor" }, 400);
  }

  const id = c.env.CALLRELAY_DO.idFromName(RELAY_NAME);
  const stub = c.env.CALLRELAY_DO.get(id);
  const resp = await stub.fetch(`https://relay/events?since=${since}`);
  const data = (await resp.json()) as { events: StoredEvent[]; nextCursor: number };
  return c.json(data, 200);
});

export default app;
