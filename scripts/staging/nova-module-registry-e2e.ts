/**
 * Authenticated staging E2E test for Nova Guard publisher-key ownership and lifecycle.
 *
 * Required environment:
 *   NOVA_GUARD_FUNCTION_URL  Full Edge Function URL
 *   SUPABASE_PUBLISHABLE_KEY Supabase publishable/anon key for NOVA-STAGING
 *   NOVA_TEST_USER_A_JWT     Valid session JWT for staging user A (owner)
 *   NOVA_TEST_USER_B_JWT     Valid session JWT for a different staging user B
 *   NOVA_E2E_ALLOW_MUTATIONS=true (explicit guard; creates and revokes temporary test keys)
 *
 * Run:
 *   deno run --allow-env --allow-net scripts/staging/nova-module-registry-e2e.ts
 *
 * Never pass service-role credentials. Tokens and key material are never printed.
 */
type JsonObject = Record<string, unknown>;

const functionUrl = Deno.env.get("NOVA_GUARD_FUNCTION_URL") ?? "";
const publishableKey = Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? "";
const userA = Deno.env.get("NOVA_TEST_USER_A_JWT") ?? "";
const userB = Deno.env.get("NOVA_TEST_USER_B_JWT") ?? "";

function requireEnv(name: string, value: string): void {
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
}

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function toBase64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

async function publicKeyBase64(): Promise<string> {
  const pair = await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"]) as CryptoKeyPair;
  return toBase64(new Uint8Array(await crypto.subtle.exportKey("spki", pair.publicKey)));
}

async function invoke(token: string, body: JsonObject): Promise<{ status: number; payload: JsonObject }> {
  const response = await fetch(functionUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      apikey: publishableKey,
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });
  let payload: JsonObject = {};
  try {
    const parsed: unknown = await response.json();
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      payload = parsed as JsonObject;
    }
  } catch {
    // Keep an empty payload; assertions below report only status/action, never credentials.
  }
  return { status: response.status, payload };
}

async function main(): Promise<void> {
  requireEnv("NOVA_GUARD_FUNCTION_URL", functionUrl);
  requireEnv("SUPABASE_PUBLISHABLE_KEY", publishableKey);
  requireEnv("NOVA_TEST_USER_A_JWT", userA);
  requireEnv("NOVA_TEST_USER_B_JWT", userB);
  assert(Deno.env.get("NOVA_E2E_ALLOW_MUTATIONS") === "true",
    "Set NOVA_E2E_ALLOW_MUTATIONS=true to authorize temporary staging key mutations.");

  const invalidSession = await invoke("invalid-session-token", { action: "publisher.keys.list" });
  assert(invalidSession.status === 401, "An invalid session token must return HTTP 401.");

  const publisherId = `e2e-${crypto.randomUUID()}`;
  const firstKeyId = `key-a-${crypto.randomUUID()}`;
  const secondKeyId = `key-b-${crypto.randomUUID()}`;
  const thirdKeyId = `key-c-${crypto.randomUUID()}`;
  let publisherRegistered = false;
  console.info("Temporary E2E publisher ID:", publisherId);

  try {
    const registered = await invoke(userA, {
      action: "publisher.register",
      publisherId,
      publisherKeyId: firstKeyId,
      publicKeyBase64: await publicKeyBase64(),
    });
    assert(registered.status === 201 && registered.payload.ok === true,
      "User A must be able to register a publisher key.");
    publisherRegistered = true;

    const ownerList = await invoke(userA, { action: "publisher.keys.list", publisherId });
    assert(ownerList.status === 200 && Array.isArray(ownerList.payload.publishers),
      "User A must be able to list their own publisher keys.");
    const ownerRows = ownerList.payload.publishers as JsonObject[];
    assert(ownerRows.some((row) => row.publisherKeyId === firstKeyId && row.status === "active"),
      "Registered key must appear active for its owner.");

    const otherList = await invoke(userB, { action: "publisher.keys.list", publisherId });
    assert(otherList.status === 200 && Array.isArray(otherList.payload.publishers),
      "User B key listing should succeed.");
    assert(!(otherList.payload.publishers as JsonObject[]).some((row) => row.publisherKeyId === firstKeyId),
      "User B must not see User A's key.");

    const crossUserRotation = await invoke(userB, {
      action: "publisher.rotate",
      publisherId,
      oldPublisherKeyId: firstKeyId,
      publisherKeyId: secondKeyId,
      publicKeyBase64: await publicKeyBase64(),
    });
    assert(crossUserRotation.status === 404,
      "User B must not rotate User A's key; expected HTTP 404.");

    const rotations = await Promise.all([
      invoke(userA, {
        action: "publisher.rotate",
        publisherId,
        oldPublisherKeyId: firstKeyId,
        publisherKeyId: secondKeyId,
        publicKeyBase64: await publicKeyBase64(),
      }),
      invoke(userA, {
        action: "publisher.rotate",
        publisherId,
        oldPublisherKeyId: firstKeyId,
        publisherKeyId: thirdKeyId,
        publicKeyBase64: await publicKeyBase64(),
      }),
    ]);
    assert(rotations.filter((result) => result.status === 200 && result.payload.ok === true).length === 1,
      "Exactly one concurrent rotation of the same active key must succeed.");
    assert(rotations.filter((result) => result.status === 404).length === 1,
      "The competing rotation must observe the old key as revoked/not active.");
    const winnerKeyId = rotations[0].status === 200 ? secondKeyId : thirdKeyId;

    const afterRotation = await invoke(userA, { action: "publisher.keys.list", publisherId });
    assert(afterRotation.status === 200 && Array.isArray(afterRotation.payload.publishers),
      "Owner key listing after concurrent rotation must succeed.");
    const rotatedRows = afterRotation.payload.publishers as JsonObject[];
    assert(rotatedRows.some((row) => row.publisherKeyId === firstKeyId && row.status === "revoked"),
      "Old key must be retained as revoked.");
    assert(rotatedRows.some((row) => row.publisherKeyId === winnerKeyId && row.status === "active"),
      "Exactly one replacement key must be active.");
    assert(!rotatedRows.some((row) => row.publisherKeyId === (winnerKeyId === secondKeyId ? thirdKeyId : secondKeyId)),
      "The losing concurrent rotation must not leave a replacement key.");

    const crossUserRevoke = await invoke(userB, {
      action: "publisher.revoke",
      publisherId,
      publisherKeyId: winnerKeyId,
    });
    assert(crossUserRevoke.status === 404,
      "User B must not revoke User A's key; expected HTTP 404.");

    const revoked = await invoke(userA, {
      action: "publisher.revoke",
      publisherId,
      publisherKeyId: winnerKeyId,
    });
    assert(revoked.status === 200 && revoked.payload.ok === true,
      "User A must be able to revoke the winning active key.");

    const finalList = await invoke(userA, { action: "publisher.keys.list", publisherId });
    assert(finalList.status === 200 && Array.isArray(finalList.payload.publishers),
      "Final owner key listing must succeed.");
    const finalRows = finalList.payload.publishers as JsonObject[];
    assert(finalRows.some((row) => row.publisherKeyId === firstKeyId && row.status === "revoked")
      && finalRows.some((row) => row.publisherKeyId === winnerKeyId && row.status === "revoked"),
      "The original and winning replacement keys must be retained as revoked.");
    console.log("PASS: invalid-session rejection, owner scoping, cross-user denial, register, concurrent rotation serialization, revoke.");
  } finally {
    if (publisherRegistered) {
      const current = await invoke(userA, { action: "publisher.keys.list", publisherId });
      if (current.status !== 200 || !Array.isArray(current.payload.publishers)) {
        console.error("CLEANUP_REQUIRED: could not inspect the temporary e2e-* publisher record in NOVA-STAGING.");
      } else {
        const activeRows = (current.payload.publishers as JsonObject[]).filter(
          (row) => row.status === "active" && typeof row.publisherKeyId === "string",
        );
        for (const row of activeRows) {
          const cleanup = await invoke(userA, {
            action: "publisher.revoke",
            publisherId,
            publisherKeyId: row.publisherKeyId as string,
          });
          if (cleanup.status !== 200) {
            console.error("CLEANUP_REQUIRED: an active temporary staging key could not be revoked.");
          }
        }
      }
    }
  }
}

await main();
