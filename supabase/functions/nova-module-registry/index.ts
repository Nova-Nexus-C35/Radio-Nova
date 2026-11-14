const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
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

  const authResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
    method: "GET",
    headers: { apikey: publishableKey, Authorization: authorization },
  });
  if (!authResponse.ok) return json({ error: "invalid_or_expired_session" }, 401);

  let caller: { id?: string };
  try {
    caller = await authResponse.json();
  } catch {
    return json({ error: "authentication_check_failed" }, 401);
  }
  if (!caller.id) return json({ error: "authentication_check_failed" }, 401);

  let body: { action?: string; limit?: number };
  try {
    const raw = await req.text();
    if (raw.length > 4096) return json({ error: "request_too_large" }, 413);
    body = JSON.parse(raw);
  } catch {
    return json({ error: "invalid_json" }, 400);
  }
  if (body.action !== "catalog.list") return json({ error: "unsupported_action" }, 400);

  const limit = Number.isInteger(body.limit) ? Math.max(1, Math.min(body.limit as number, 100)) : 50;
  const headers: Record<string, string> = {
    apikey: secretKey,
    "Content-Type": "application/json",
  };
  if (secretKey.startsWith("eyJ")) headers.Authorization = `Bearer ${secretKey}`;

  const response = await fetch(`${supabaseUrl}/rest/v1/rpc/nova_registry_catalog_list`, {
    method: "POST",
    headers,
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
});
