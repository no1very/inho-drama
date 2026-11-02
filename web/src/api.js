// ===== 公共请求层：服务器地址、登录态、fetch 封装、URL 拼接助手 =====
// 接口契约见 ../docs/api.md，路径与字段名严格按契约来。

const SERVER_KEY = 'nasdrama.server'
const TOKEN_KEY = 'nasdrama.token'
const USER_KEY = 'nasdrama.user'

// 服务器地址（默认同源；开发模式下走 vite 代理）
export function serverBase() {
  const saved = localStorage.getItem(SERVER_KEY)
  if (saved && saved.trim()) return saved.trim().replace(/\/+$/, '')
  return window.location.origin
}

export function setServerBase(url) {
  localStorage.setItem(SERVER_KEY, String(url || '').trim().replace(/\/+$/, ''))
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getSavedUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

export function saveAuth(token, user) {
  localStorage.setItem(TOKEN_KEY, token || '')
  localStorage.setItem(USER_KEY, JSON.stringify(user || null))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

// 登录/注册接口的 401 不做清态跳转，否则会吞掉后端的错误文案（如「用户名或密码错误」）
function isAuthFreePath(path) {
  return path.indexOf('/auth/login') === 0 || path.indexOf('/auth/register') === 0
}

// 通用请求封装：request(path, { method, body, raw })
// - 自动拼 serverBase() + '/api/v1' + path
// - 携带 Authorization: Bearer <token>；GET 额外附 ?token=
// - 401 时清除本地登录态并跳 #/login
// - 非 2xx 抛出含后端 error 字段的消息
// - raw=true 时返回原始 Response（由调用方自行处理）
export async function request(path, options) {
  const opts = options || {}
  const method = (opts.method || 'GET').toUpperCase()
  const token = getToken()
  const isGet = method === 'GET'

  let url = serverBase() + '/api/v1' + path
  if (isGet && token) {
    url += (url.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(token)
  }

  const headers = {}
  if (token) headers['Authorization'] = 'Bearer ' + token
  if (opts.body != null) headers['Content-Type'] = 'application/json'

  let resp
  try {
    resp = await fetch(url, {
      method: method,
      headers: headers,
      body: opts.body != null ? JSON.stringify(opts.body) : undefined
    })
  } catch (e) {
    throw new Error('网络错误，请检查服务器地址')
  }

  // 401：登录态失效，清除并回登录页
  if (resp.status === 401 && !isAuthFreePath(path)) {
    clearAuth()
    if (window.location.hash !== '#/login') window.location.hash = '#/login'
    throw new Error('登录已过期，请重新登录')
  }

  if (opts.raw) return resp

  if (!resp.ok) {
    let msg = '请求失败（' + resp.status + '）'
    try {
      const data = await resp.json()
      if (data && data.error) msg = data.error
    } catch (e) {
      // 非 JSON 响应，忽略
    }
    throw new Error(msg)
  }

  const text = await resp.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch (e) {
    return text
  }
}

// ===== URL 拼接助手 =====

// 封面地址：契约中 series.cover 形如 /api/v1/cover/123
export function coverUrl(coverPath) {
  if (!coverPath) return ''
  const sep = coverPath.indexOf('?') >= 0 ? '&' : '?'
  return serverBase() + coverPath + sep + 'token=' + encodeURIComponent(getToken())
}

// 视频流地址（hls=true 时加 &hls=1 兜底参数）
export function streamUrl(episodeId, hls) {
  let url = serverBase() + '/api/v1/stream/' + episodeId + '?token=' + encodeURIComponent(getToken())
  if (hls) url += '&hls=1'
  return url
}

// 字幕地址（后端默认返回 WebVTT 文本）
export function subtitleUrl(episodeId) {
  return serverBase() + '/api/v1/subtitle/' + episodeId + '?token=' + encodeURIComponent(getToken())
}

// 播放进度上报：鉴权走 ?token=（便于 fetch keepalive 场景），失败静默
export function reportProgress(payload, keepalive) {
  const token = getToken()
  if (!token) return Promise.resolve(null)
  return fetch(serverBase() + '/api/v1/progress?token=' + encodeURIComponent(token), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + token
    },
    body: JSON.stringify(payload),
    keepalive: !!keepalive
  }).catch(function () {
    return null
  })
}

// ===== 展示格式化助手 =====

// 秒 → mm:ss（超过 1 小时为 h:mm:ss）
export function fmtDur(sec) {
  sec = Math.max(0, Math.floor(Number(sec) || 0))
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  const p2 = function (n) {
    return n < 10 ? '0' + n : '' + n
  }
  return h > 0 ? h + ':' + p2(m) + ':' + p2(s) : p2(m) + ':' + p2(s)
}

// Unix 秒 → 'YYYY-MM-DD HH:mm'（0 视为从未）
export function fmtTime(ts) {
  if (!ts) return '从未'
  const d = new Date(ts * 1000)
  const p2 = function (n) {
    return n < 10 ? '0' + n : '' + n
  }
  return (
    d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) +
    ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes())
  )
}
