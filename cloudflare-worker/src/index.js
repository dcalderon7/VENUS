const BACKEND_KEY = 'BACKEND_URL';
const ADMIN_PATH = '/__admin/register';
const API_PREFIX = '/api/';

function corsHeaders(request) {
  const origin = request.headers.get('Origin');
  const headers = new Headers({
    'Access-Control-Allow-Headers': 'Authorization, Content-Type, Range, X-Requested-With',
    'Access-Control-Allow-Methods': 'GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS',
    'Access-Control-Allow-Credentials': 'true',
    'Access-Control-Expose-Headers': 'Accept-Ranges, Content-Length, Content-Range, Content-Type',
    'Vary': 'Origin',
  });

  if (origin) {
    headers.set('Access-Control-Allow-Origin', origin);
  } else {
    headers.set('Access-Control-Allow-Origin', '*');
  }

  return headers;
}

function jsonResponse(body, status, request) {
  const headers = corsHeaders(request);
  headers.set('Content-Type', 'application/json; charset=utf-8');
  return new Response(JSON.stringify(body), { status, headers });
}

function isValidBackendUrl(value) {
  if (typeof value !== 'string' || value.length > 255) return false;

  let url;
  try {
    url = new URL(value);
  } catch {
    return false;
  }

  return url.protocol === 'https:'
    && /^([a-z0-9-]+\.)+trycloudflare\.com$/i.test(url.hostname)
    && !url.username
    && !url.password
    && !url.search
    && !url.hash
    && (url.pathname === '/' || url.pathname === '');
}

function normalizedBackendUrl(value) {
  return new URL(value).origin;
}

function backendRequest(request, backendUrl) {
  const incomingUrl = new URL(request.url);
  const targetUrl = new URL(`${incomingUrl.pathname}${incomingUrl.search}`, `${backendUrl}/`);
  const headers = new Headers(request.headers);

  headers.delete('Host');
  headers.delete('Connection');
  headers.delete('Content-Length');
  headers.delete('Origin');

  return new Request(targetUrl, {
    method: request.method,
    headers,
    body: request.method === 'GET' || request.method === 'HEAD' ? undefined : request.body,
    redirect: 'manual',
  });
}

async function proxyApi(request, env) {
  const backendUrl = await env.VENUS_CONFIG.get(BACKEND_KEY);
  if (!isValidBackendUrl(backendUrl)) {
    return jsonResponse({
      error: 'Backend de VENUS no configurado.',
      code: 'BACKEND_NOT_CONFIGURED',
    }, 503, request);
  }

  try {
    const response = await fetch(backendRequest(request, normalizedBackendUrl(backendUrl)));
    const headers = new Headers(response.headers);
    const cors = corsHeaders(request);
    cors.forEach((value, key) => headers.set(key, value));
    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers,
    });
  } catch {
    return jsonResponse({
      error: 'No se pudo contactar al backend de VENUS.',
      code: 'BACKEND_UNAVAILABLE',
    }, 502, request);
  }
}

async function registerBackend(request, env) {
  if (request.method !== 'POST') {
    return jsonResponse({ error: 'Método no permitido.' }, 405, request);
  }

  const authorization = request.headers.get('Authorization') || '';
  const expected = env.ADMIN_REGISTRATION_SECRET;
  if (!expected || authorization !== `Bearer ${expected}`) {
    return jsonResponse({ error: 'No autorizado.' }, 401, request);
  }

  let payload;
  try {
    payload = await request.json();
  } catch {
    return jsonResponse({ error: 'El cuerpo debe ser JSON válido.' }, 400, request);
  }

  if (!isValidBackendUrl(payload?.backendUrl)) {
    return jsonResponse({
      error: 'backendUrl debe ser una URL HTTPS válida de trycloudflare.com.',
    }, 400, request);
  }

  await env.VENUS_CONFIG.put(BACKEND_KEY, normalizedBackendUrl(payload.backendUrl));
  return jsonResponse({ ok: true, message: 'Backend registrado correctamente.' }, 200, request);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders(request) });
    }

    if (url.pathname === ADMIN_PATH) {
      return registerBackend(request, env);
    }

    if (url.pathname === '/api' || url.pathname.startsWith(API_PREFIX)) {
      return proxyApi(request, env);
    }

    return env.ASSETS.fetch(request);
  },
};
