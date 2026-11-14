const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type Caller = { id: string };
type RequestBody = {
  action?: string;
  limit?: number;
  publisherId?: string;
  publisherKeyId?: string;
  oldPublisherKeyId?: string;
  publicKeyBase64?: string;
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8" },
  });
}

function namedKey(raw: string | undefined, keyName = "default"): string | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    const value = parsed?.[keyName];
    return typeof value === "string" && value.length > 0 ? value : null;
  } catch {
    return null;
  }
}

function decodeBase64(value: string): Uint8Array {
  if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(value)) {
    throw new Error("invalid_base64");
  }
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

async function validateEd25519PublicKey(encoded: string): Promise<void> {
  if (encoded.length < 40 || encoded.length > 512) throw new Error("invalid_public_key");
  const der = decodeBase64(encoded);
  await crypto.subtle.importKey("spki", der, { name: "Ed25519" }, false, ["verify"]);
}

function validIdentifier(value: unknown): value is string {
  return typeof value === "string" && /^[A-Za-z0-9_.-]{1,80}$/.test(value);
}

function dbHeaders(secretKey: string, profile = "private"): Record<string, string> {
  const headers: Record<string, string> = {
    apikey: secretKey,
    "Content-Type": "application/json",
    "Accept-Profile": profile,
    "Content-Profile": profile,
  };
  if (secretKey.startsWith("eyJ")) headers.Authorization = `Bearer ${secretKey}`;
  return headers;
}

async function dbRequest(
  url: string,
  secretKey: string,
  path: string,
  method = "GET",
  body?: unknown,
  profile = "private",
): Promise<Response> {
  return await fetch(`${url}/rest/v1/${path}`, {
    method,
    headers: {
      ...dbHeaders(secretKey, profile),
      ...(method === "POST" || method === "PATCH" ? { Prefer: "return=representation" } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
}

async function audit(
  url: string,
  secretKey: string,
  caller: Caller,
  action: string,
  outcome: "success" | "denied" | "failure",
  publisherId?: string,
  keyId?: string,
): Promise<void> {
  await dbRequest(url, secretKey, "nova_module_audit", "POST", {
    actor_user_id: caller.id,
    actor_role: "publisher_owner",
    // Publisher key events are not module lifecycle events; keep module columns null.
    module_id: null,
    module_version: null,
    action,
    outcome,
    metadata: {
      publisherId: publisherId ?? null,
      publisherKeyId: keyId ?? null,
    },
  }).catch(() => undefined);
}

async function ownedKey(
  url: string,
  secretKey: string,
  caller: Caller,
  publisherId: string,
  keyId: string,
): Promise<boolean> {
  const query = new URLSearchParams({
    select: "publisher_id",
    publisher_id: `eq.${publisherId}`,
    publisher_key_id: `eq.${keyId}`,
    owner_user_id: `eq.${caller.id}`,
    status: "eq.active",
    limit: "1",
  });
  const response = await dbRequest(url, secretKey, `nova_module_publishers?${query}`);
  if (!response.ok) return false;
  const rows = await response.json();
  return Array.isArray(rows) && rows.length === 1;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const publishableKey =
    namedKey(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS")) ?? Deno.env.get("SUPABASE_ANON_KEY");
  const secretKey =
    namedKey(Deno.env.get("SUPABASE_SECRET_KEYS")) ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !publishableKey || !secretKey) {
    return json({ error: "server_configuration_incomplete" }, 500);
  }

  const authorization = req.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ") || authorization.length > 8192) {
    return json({ error: "authentication_required" }, 401);
  }

  let authResponse: Response;
  try {
    authResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
      method: "GET",
      headers: { apikey: publishableKey, Authorization: authorization },
    });
  } catch {
    return json({ error: "authentication_service_unavailable" }, 503);
  }
  if (!authResponse.ok) return json({ error: "invalid_or_expired_session" }, 401);

  let caller: Caller;
  try {
    const user = await authResponse.json();
    if (typeof user?.id !== "string" || !/^[0-9a-f-]{36}$/i.test(user.id)) {
      return json({ error: "authentication_check_failed" }, 401);
    }
    caller = { id: user.id };
  } catch {
    return json({ error: "authentication_check_failed" }, 401);
  }

  let body: RequestBody;
  try {
    const raw = await req.text();
    if (raw.length > 4096) return json({ error: "request_too_large" }, 413);
    body = JSON.parse(raw);
    if (!body || typeof body !== "object" || Array.isArray(body)) return json({ error: "invalid_json" }, 400);
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  if (body.action === "catalog.list") {
    const limit = Number.isInteger(body.limit) ? Math.max(1, Math.min(body.limit as number, 100)) : 50;
    const response = await fetch(`${supabaseUrl}/rest/v1/rpc/nova_registry_catalog_list`, {
      method: "POST",
      headers: dbHeaders(secretKey, "public"),
      body: JSON.stringify({ p_limit: limit }),
    });
    if (!response.ok) return json({ error: "catalog_unavailable" }, 503);
    const catalog = await response.json();
    return json({
      ok: true,
      action: "catalog.list",
      count: Array.isArray(catalog) ? catalog.length : 0,
      modules: Array.isArray(catalog) ? catalog : [],
    });
  }


  if (body.action === "publisher.keys.list") {
    if (body.publisherId !== undefined && !validIdentifier(body.publisherId)) {
      return json({ error: "invalid_publisher_id" }, 400);
    }
    const filters = new URLSearchParams({
      select: "publisher_id,publisher_key_id,public_key_base64,algorithm,status,created_at,revoked_at,supersedes_key_id",
      owner_user_id: `eq.${caller.id}`,
      order: "created_at.asc",
      limit: "200",
    });
    if (body.publisherId) filters.set("publisher_id", `eq.${body.publisherId}`);
    const response = await dbRequest(supabaseUrl, secretKey, `nova_module_publishers?${filters}`);
    if (!response.ok) return json({ error: "publisher_keys_unavailable" }, 503);
    const rows = await response.json();
    if (!Array.isArray(rows)) return json({ error: "publisher_keys_unavailable" }, 503);
    return json({
      ok: true,
      action: body.action,
      publishers: rows.map((row) => ({
        publisherId: row.publisher_id,
        publisherKeyId: row.publisher_key_id,
        publicKeyBase64: row.public_key_base64,
        algorithm: row.algorithm,
        status: row.status,
        createdAt: row.created_at,
        revokedAt: row.revoked_at,
        supersedesKeyId: row.supersedes_key_id,
      })),
    });
  }

  if (body.action === "publisher.register") {
    if (!validIdentifier(body.publisherId) || !validIdentifier(body.publisherKeyId) ||
        typeof body.publicKeyBase64 !== "string") {
      return json({ error: "invalid_publisher_registration" }, 400);
    }
    try {
      await validateEd25519PublicKey(body.publicKeyBase64);
    } catch {
      return json({ error: "invalid_ed25519_public_key" }, 400);
    }
    const existingQuery = new URLSearchParams({
      select: "publisher_id",
      publisher_id: `eq.${body.publisherId}`,
      limit: "1",
    });
    const existing = await dbRequest(supabaseUrl, secretKey, `nova_module_publishers?${existingQuery}`);
    if (!existing.ok) return json({ error: "publisher_registry_unavailable" }, 503);
    if ((await existing.json()).length > 0) return json({ error: "publisher_id_already_registered" }, 409);

    const inserted = await dbRequest(supabaseUrl, secretKey, "nova_module_publishers", "POST", {
      publisher_id: body.publisherId,
      publisher_key_id: body.publisherKeyId,
      owner_user_id: caller.id,
      created_by: caller.id,
      public_key_base64: body.publicKeyBase64,
      algorithm: "Ed25519",
      status: "active",
    });
    if (!inserted.ok) return json({ error: "publisher_registration_failed" }, inserted.status === 409 ? 409 : 503);
    await audit(supabaseUrl, secretKey, caller, "publisher.register", "success", body.publisherId, body.publisherKeyId);
    return json({ ok: true, action: body.action, publisherId: body.publisherId, publisherKeyId: body.publisherKeyId }, 201);
  }

  if (body.action === "publisher.rotate") {
    if (!validIdentifier(body.publisherId) || !validIdentifier(body.oldPublisherKeyId) ||
        !validIdentifier(body.publisherKeyId) || typeof body.publicKeyBase64 !== "string" ||
        body.oldPublisherKeyId === body.publisherKeyId) {
      return json({ error: "invalid_key_rotation" }, 400);
    }
    try {
      await validateEd25519PublicKey(body.publicKeyBase64);
    } catch {
      return json({ error: "invalid_ed25519_public_key" }, 400);
    }
    if (!await ownedKey(supabaseUrl, secretKey, caller, body.publisherId, body.oldPublisherKeyId)) {
      await audit(supabaseUrl, secretKey, caller, "publisher.rotate", "denied", body.publisherId, body.oldPublisherKeyId);
      return json({ error: "active_owned_key_not_found" }, 404);
    }
    const inserted = await dbRequest(supabaseUrl, secretKey, "nova_module_publishers", "POST", {
      publisher_id: body.publisherId,
      publisher_key_id: body.publisherKeyId,
      owner_user_id: caller.id,
      created_by: caller.id,
      public_key_base64: body.publicKeyBase64,
      algorithm: "Ed25519",
      status: "active",
      supersedes_key_id: body.oldPublisherKeyId,
    });
    if (!inserted.ok) return json({ error: "new_publisher_key_registration_failed" }, inserted.status === 409 ? 409 : 503);

    const filter = new URLSearchParams({
      publisher_id: `eq.${body.publisherId}`,
      publisher_key_id: `eq.${body.oldPublisherKeyId}`,
      owner_user_id: `eq.${caller.id}`,
      status: "eq.active",
    });
    const revoked = await dbRequest(supabaseUrl, secretKey, `nova_module_publishers?${filter}`, "PATCH", {
      status: "revoked",
      revoked_at: new Date().toISOString(),
    });
    if (!revoked.ok || (await revoked.json()).length !== 1) {
      const cleanup = new URLSearchParams({
        publisher_id: `eq.${body.publisherId}`,
        publisher_key_id: `eq.${body.publisherKeyId}`,
        owner_user_id: `eq.${caller.id}`,
        status: "eq.active",
      });
      await dbRequest(supabaseUrl, secretKey, `nova_module_publishers?${cleanup}`, "DELETE")
        .catch(() => undefined);
      return json({ error: "key_rotation_requires_reconciliation" }, 503);
    }
    await audit(supabaseUrl, secretKey, caller, "publisher.rotate", "success", body.publisherId, body.publisherKeyId);
    return json({ ok: true, action: body.action, publisherId: body.publisherId,
      activePublisherKeyId: body.publisherKeyId, revokedPublisherKeyId: body.oldPublisherKeyId });
  }

  if (body.action === "publisher.revoke") {
    if (!validIdentifier(body.publisherId) || !validIdentifier(body.publisherKeyId)) {
      return json({ error: "invalid_key_revocation" }, 400);
    }
    const filter = new URLSearchParams({
      publisher_id: `eq.${body.publisherId}`,
      publisher_key_id: `eq.${body.publisherKeyId}`,
      owner_user_id: `eq.${caller.id}`,
      status: "eq.active",
    });
    const response = await dbRequest(supabaseUrl, secretKey, `nova_module_publishers?${filter}`, "PATCH", {
      status: "revoked",
      revoked_at: new Date().toISOString(),
    });
    if (!response.ok) return json({ error: "publisher_revocation_failed" }, 503);
    const rows = await response.json();
    if (!Array.isArray(rows) || rows.length !== 1) {
      await audit(supabaseUrl, secretKey, caller, "publisher.revoke", "denied", body.publisherId, body.publisherKeyId);
      return json({ error: "active_owned_key_not_found" }, 404);
    }
    await audit(supabaseUrl, secretKey, caller, "publisher.revoke", "success", body.publisherId, body.publisherKeyId);
    return json({ ok: true, action: body.action, publisherId: body.publisherId, revokedPublisherKeyId: body.publisherKeyId });
  }

  return json({ error: "unsupported_action" }, 400);
});
