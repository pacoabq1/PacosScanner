/**
 * Paco's Scanner upload service for pacosworld.com.
 *
 * Runs on Cloudflare (not on the home PC), so uploads work while Pacos-Main is off.
 * PDFs are kept in an R2 bucket bound as SCANS.
 *
 * Cloudflare Access sits in front of this Worker. The phone app passes Access with a
 * service token (CF-Access-Client-Id / CF-Access-Client-Secret headers); a browser passes
 * with Mark's own sign-in. Access then adds a signed Cf-Access-Jwt-Assertion header, and
 * this Worker checks that signature itself, so a request that somehow skips Access is
 * still refused. Only ALLOWED_EMAIL (signed in) or the service token ALLOWED_TOKEN_ID (its Client ID)
 * gets in. If TEAM_DOMAIN or POLICY_AUD isn't set, every request is refused.
 *
 * Endpoints (all under /api/scans):
 *   GET  /api/scans/ping                  -> {"ok":true,"who":...}   used by the app's Test button
 *   GET  /api/scans/files                 -> {"files":[{key,size,uploaded}]}
 *   GET  /api/scans/files/<folder>/<name> -> the PDF
 *   PUT  /api/scans/files/<folder>/<name> -> stores the PDF; answers {"ok":true,"key":...}
 *        An existing name is never overwritten: "Name (2).pdf" and so on is used instead.
 */

const PREFIX = "/api/scans";
const MAX_BYTES = 50 * 1024 * 1024; // 50 MB per PDF

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!url.pathname.startsWith(PREFIX)) return json({ error: "not found" }, 404);

    const who = await verifyAccess(request, env);
    if (!who) return json({ error: "not allowed" }, 403);

    const rest = url.pathname.slice(PREFIX.length);
    try {
      if (rest === "/ping" && request.method === "GET") {
        return json({ ok: true, who: who.email || (who.common_name ? "service token" : "signed in") });
      }
      if ((rest === "/files" || rest === "/files/") && request.method === "GET") {
        return await listFiles(env);
      }
      if (rest.startsWith("/files/")) {
        const key = keyFromPath(rest.slice("/files/".length));
        if (!key) return json({ error: "bad file name" }, 400);
        if (request.method === "GET") return await getFile(env, key);
        if (request.method === "PUT") return await putFile(request, env, key);
      }
      return json({ error: "not found" }, 404);
    } catch (e) {
      return json({ error: "server error" }, 500);
    }
  },
};

// ---------- files ----------

/** Turns "Inbox/My%20Scan.pdf" into a safe R2 key, or null if it isn't one. */
export function keyFromPath(path) {
  let parts;
  try {
    parts = path.split("/").map((p) => decodeURIComponent(p).trim());
  } catch {
    return null;
  }
  if (parts.length === 0 || parts.length > 8) return null;
  for (const p of parts) {
    if (!p || p === "." || p === ".." || p.length > 120) return null;
    if (/[\\/:*?"<>|\u0000-\u001f\u007f]/.test(p)) return null;
  }
  const key = parts.join("/");
  if (!/\.pdf$/i.test(key) || key.length > 600) return null;
  return key;
}

async function listFiles(env) {
  const files = [];
  let cursor;
  do {
    const page = await env.SCANS.list({ cursor, limit: 1000 });
    for (const o of page.objects) files.push({ key: o.key, size: o.size, uploaded: o.uploaded });
    cursor = page.truncated ? page.cursor : undefined;
  } while (cursor && files.length < 5000);
  files.sort((a, b) => (a.uploaded < b.uploaded ? 1 : -1));
  return json({ files });
}

async function getFile(env, key) {
  const obj = await env.SCANS.get(key);
  if (!obj) return json({ error: "not found" }, 404);
  const name = key.split("/").pop();
  return new Response(obj.body, {
    headers: {
      "content-type": "application/pdf",
      "content-disposition": `inline; filename*=UTF-8''${encodeURIComponent(name)}`,
      "cache-control": "private, no-store",
    },
  });
}

async function putFile(request, env, key) {
  const declared = Number(request.headers.get("content-length") || "0");
  if (declared > MAX_BYTES) return json({ error: "file too large" }, 413);
  const bytes = new Uint8Array(await request.arrayBuffer());
  if (bytes.length === 0) return json({ error: "empty file" }, 400);
  if (bytes.length > MAX_BYTES) return json({ error: "file too large" }, 413);
  // Every PDF starts with "%PDF-".
  if (!(bytes[0] === 0x25 && bytes[1] === 0x50 && bytes[2] === 0x44 && bytes[3] === 0x46)) {
    return json({ error: "not a PDF" }, 415);
  }
  const finalKey = await freeKey(env, key);
  await env.SCANS.put(finalKey, bytes, { httpMetadata: { contentType: "application/pdf" } });
  return json({ ok: true, key: finalKey, size: bytes.length }, 201);
}

/** "Inbox/Bill.pdf", or "Inbox/Bill (2).pdf" if that one is taken, and so on. */
async function freeKey(env, key) {
  if (!(await env.SCANS.head(key))) return key;
  const base = key.replace(/\.pdf$/i, "");
  for (let n = 2; n < 1000; n++) {
    const candidate = `${base} (${n}).pdf`;
    if (!(await env.SCANS.head(candidate))) return candidate;
  }
  return `${base} (${Date.now()}).pdf`;
}

// ---------- Cloudflare Access check ----------

let cachedKeys = null;
let cachedAt = 0;

async function accessKeys(teamDomain) {
  if (cachedKeys && Date.now() - cachedAt < 60 * 60 * 1000) return cachedKeys;
  const res = await fetch(`https://${teamDomain}/cdn-cgi/access/certs`);
  if (!res.ok) throw new Error("certs");
  cachedKeys = (await res.json()).keys || [];
  cachedAt = Date.now();
  return cachedKeys;
}

/** Returns the Access token's claims when it is valid for this app, otherwise null. */
export async function verifyAccess(request, env) {
  const token = request.headers.get("cf-access-jwt-assertion");
  if (!token || !env.TEAM_DOMAIN || !env.POLICY_AUD) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const header = JSON.parse(new TextDecoder().decode(b64url(parts[0])));
    const claims = JSON.parse(new TextDecoder().decode(b64url(parts[1])));
    if (header.alg !== "RS256") return null;
    let jwk = (await accessKeys(env.TEAM_DOMAIN)).find((k) => k.kid === header.kid);
    if (!jwk) {
      cachedKeys = null; // keys may have rotated
      jwk = (await accessKeys(env.TEAM_DOMAIN)).find((k) => k.kid === header.kid);
      if (!jwk) return null;
    }
    const key = await crypto.subtle.importKey(
      "jwk",
      { kty: jwk.kty, n: jwk.n, e: jwk.e },
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"],
    );
    const ok = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      key,
      b64url(parts[2]),
      new TextEncoder().encode(`${parts[0]}.${parts[1]}`),
    );
    if (!ok) return null;
    const now = Math.floor(Date.now() / 1000);
    if (typeof claims.exp !== "number" || claims.exp < now) return null;
    if (typeof claims.nbf === "number" && claims.nbf > now + 60) return null;
    if (claims.iss !== `https://${env.TEAM_DOMAIN}`) return null;
    const aud = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
    if (!aud.includes(env.POLICY_AUD)) return null;
    // Only Mark (signed in) or the phone's own service token, even if a policy is loosened later.
    if (claims.email) {
      if (!env.ALLOWED_EMAIL || claims.email.toLowerCase() !== env.ALLOWED_EMAIL.toLowerCase()) return null;
    } else if (!env.ALLOWED_TOKEN_ID || claims.common_name !== env.ALLOWED_TOKEN_ID) {
      return null;
    }
    return claims;
  } catch {
    return null;
  }
}

function b64url(s) {
  const b64 = s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4);
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store" },
  });
}
