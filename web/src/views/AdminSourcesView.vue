<script setup>
// 媒体源管理：列表、添加/编辑/删除、单源扫描、扫描全部、扫描状态轮询（2s）
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { request, fmtTime } from '../api'

const router = useRouter()

const sources = ref([])
const loading = ref(true)
const errMsg = ref('')
const scanStatus = ref(null) // GET /scan/status 结果
let pollTimer = null

async function loadSources() {
  try {
    const data = await request('/sources')
    sources.value = data.items || []
  } catch (e) {
    errMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadScanStatus() {
  try {
    scanStatus.value = await request('/scan/status')
  } catch (e) {
    // 轮询失败静默
  }
}

// ===== 添加/编辑弹窗 =====
const showModal = ref(false)
const editingId = ref(null) // null=新增
const saving = ref(false)
const formErr = ref('')

// 表单：按 type 取用字段（与 POST /sources 契约一致）
const form = reactive({
  type: 'local',
  name: '',
  enabled: true,
  path: '',
  url: '',
  username: '',
  password: '',
  base_path: '',
  insecure: false,
  api_key: '',
  user_id: '',
  transcode: false
})

function resetForm() {
  form.type = 'local'
  form.name = ''
  form.enabled = true
  form.path = ''
  form.url = ''
  form.username = ''
  form.password = ''
  form.base_path = ''
  form.insecure = false
  form.api_key = ''
  form.user_id = ''
  form.transcode = false
}

function openAdd() {
  editingId.value = null
  resetForm()
  formErr.value = ''
  errMsg.value = ''
  showModal.value = true
}

// 编辑时密码与 api_key 留空表示保持原值（后端约定）
function openEdit(src) {
  editingId.value = src.id
  const c = src.config || {}
  form.type = src.type
  form.name = src.name
  form.enabled = !!src.enabled
  form.path = c.path || ''
  form.url = c.url || ''
  form.username = c.username || ''
  form.password = ''
  form.base_path = c.base_path || ''
  form.insecure = !!c.insecure
  form.api_key = ''
  form.user_id = c.user_id || ''
  form.transcode = !!c.transcode
  formErr.value = ''
  errMsg.value = ''
  showModal.value = true
}

// 按 type 组装请求体（契约：POST /sources）
function buildPayload() {
  const config = {}
  if (form.type === 'local') {
    config.path = form.path
  } else if (form.type === 'webdav') {
    config.url = form.url
    config.username = form.username
    config.password = form.password
    config.base_path = form.base_path || '/'
    config.insecure = !!form.insecure
  } else {
    // jellyfin / emby：用户名+密码优先，api_key 可选（服务端保存时自动换 token）
    config.url = form.url
    config.username = form.username
    config.password = form.password
    config.api_key = form.api_key
    config.user_id = form.user_id
    config.transcode = !!form.transcode
  }
  return { type: form.type, name: form.name, enabled: !!form.enabled, config: config }
}

async function submitForm() {
  formErr.value = ''
  if (!form.name.trim()) {
    formErr.value = '请填写名称'
    return
  }
  if (form.type === 'local' && !form.path.trim()) {
    formErr.value = '请填写路径'
    return
  }
  if (form.type !== 'local' && !form.url.trim()) {
    formErr.value = '请填写地址'
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await request('/sources/' + editingId.value, { method: 'PUT', body: buildPayload() })
    } else {
      await request('/sources', { method: 'POST', body: buildPayload() })
    }
    showModal.value = false
    await loadSources()
    loadScanStatus()
  } catch (e) {
    formErr.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

// ===== 列表操作 =====

async function scanOne(src) {
  try {
    await request('/sources/' + src.id + '/scan', { method: 'POST' })
    loadScanStatus()
  } catch (e) {
    errMsg.value = e.message || '触发扫描失败'
  }
}

async function scanAll() {
  try {
    await request('/scan/all', { method: 'POST' })
    loadScanStatus()
  } catch (e) {
    errMsg.value = e.message || '触发扫描失败'
  }
}

// 删除需 confirm 确认
async function removeSource(src) {
  if (!window.confirm('确定删除媒体源「' + src.name + '」？其缓存的剧集数据将一并清除')) return
  try {
    await request('/sources/' + src.id, { method: 'DELETE' })
    await loadSources()
  } catch (e) {
    errMsg.value = e.message || '删除失败'
  }
}

function typeLabel(t) {
  if (t === 'local') return '本地'
  if (t === 'webdav') return 'WebDAV'
  if (t === 'jellyfin') return 'Jellyfin'
  if (t === 'emby') return 'Emby'
  return t
}

// 媒体源类型图标（内联 SVG，与安卓端一致：文件夹/云/播放圆/服务器）
function typeIconPath(t) {
  if (t === 'local') {
    return 'M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z'
  }
  if (t === 'webdav') {
    return 'M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z'
  }
  if (t === 'jellyfin') {
    return 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z'
  }
  return 'M20 13H4c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h16c.55 0 1-.45 1-1v-6c0-.55-.45-1-1-1zM7 19c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zM20 3H4c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h16c.55 0 1-.45 1-1V4c0-.55-.45-1-1-1zM7 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z'
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/')
}

onMounted(function () {
  loadSources()
  loadScanStatus()
  pollTimer = setInterval(loadScanStatus, 2000) // 每 2s 轮询扫描状态
})

onBeforeUnmount(function () {
  clearInterval(pollTimer)
})
</script>

<template>
  <div class="sources-page">
    <header class="nav-bar">
      <button class="icon-btn" @click="goBack">
        <svg viewBox="0 0 24 24" width="24" height="24">
          <path d="M15.4 7.4 14 6l-6 6 6 6 1.4-1.4L10.8 12z" fill="currentColor"/>
        </svg>
      </button>
      <span class="nav-title">媒体源管理</span>
      <span class="nav-side"></span>
    </header>

    <div class="sources-actions">
      <button class="btn-primary btn-add" @click="openAdd">添加媒体源</button>
      <button class="btn-ghost" @click="scanAll">扫描全部</button>
    </div>

    <p class="error-text sources-err" v-if="errMsg">{{ errMsg }}</p>

    <div class="source-list" v-if="sources.length">
      <div class="source-card" v-for="src in sources" :key="src.id">
        <div class="sc-head">
          <span class="sc-name">
            <svg viewBox="0 0 24 24" width="16" height="16" class="sc-icon"><path :d="typeIconPath(src.type)" fill="currentColor"/></svg>
            {{ src.name }}
          </span>
          <span class="sc-badge" :class="'badge-' + src.type">{{ typeLabel(src.type) }}</span>
          <span class="sc-state" :class="{ on: src.enabled }">{{ src.enabled ? '已启用' : '已停用' }}</span>
        </div>
        <p class="sc-line">{{ src.series_count }} 部剧 · 最近扫描：{{ fmtTime(src.last_scan_at) }}</p>
        <p class="sc-error" v-if="src.last_error">{{ src.last_error }}</p>
        <div class="sc-actions">
          <button class="sc-btn" @click="scanOne(src)">扫描</button>
          <button class="sc-btn" @click="openEdit(src)">编辑</button>
          <button class="sc-btn danger" @click="removeSource(src)">删除</button>
        </div>
      </div>
    </div>
    <div class="empty-tip" v-else-if="!loading">还没有媒体源，点击「添加媒体源」开始建库</div>
    <div class="empty-tip" v-else>加载中…</div>

    <!-- 底部扫描状态（每 2s 轮询） -->
    <div class="scan-status" v-if="scanStatus">
      <template v-if="scanStatus.running">
        <span class="scan-dot"></span>
        正在扫描「{{ scanStatus.source_name || '…' }}」 · 已扫描 {{ scanStatus.series_count || 0 }} 部剧 / {{ scanStatus.episode_count || 0 }} 集
      </template>
      <template v-else>
        <span>扫描空闲 · 累计 {{ scanStatus.series_count || 0 }} 部剧 / {{ scanStatus.episode_count || 0 }} 集</span>
        <span class="scan-err" v-if="scanStatus.last_error"> · 错误：{{ scanStatus.last_error }}</span>
      </template>
    </div>

    <!-- 添加/编辑媒体源弹窗 -->
    <div class="modal-mask" v-if="showModal" @click="showModal = false">
      <div class="modal no-scrollbar" @click.stop>
        <div class="modal-head">
          <span>{{ editingId ? '编辑媒体源' : '添加媒体源' }}</span>
          <button class="icon-btn" @click="showModal = false">
            <svg viewBox="0 0 24 24" width="20" height="20">
              <path d="M19 6.4 17.6 5 12 10.6 6.4 5 5 6.4 10.6 12 5 17.6 6.4 19l5.6-5.6 5.6 5.6 1.4-1.4L13.4 12z" fill="currentColor"/>
            </svg>
          </button>
        </div>

        <div class="form-item">
          <label class="form-label">类型</label>
          <select class="form-input" v-model="form.type">
            <option value="local">local（本地目录）</option>
            <option value="webdav">webdav</option>
            <option value="jellyfin">jellyfin</option>
            <option value="emby">emby</option>
          </select>
        </div>
        <div class="form-item">
          <label class="form-label">名称</label>
          <input class="form-input" v-model="form.name" placeholder="如：本地短剧库" />
        </div>
        <div class="form-item">
          <label class="form-check">
            <input type="checkbox" v-model="form.enabled" /> 启用该媒体源
          </label>
        </div>

        <!-- local：path -->
        <template v-if="form.type === 'local'">
          <div class="form-item">
            <label class="form-label">路径</label>
            <input class="form-input" v-model="form.path" placeholder="/media/短剧" />
          </div>
        </template>

        <!-- webdav：url/username/password/base_path/insecure -->
        <template v-else-if="form.type === 'webdav'">
          <div class="form-item">
            <label class="form-label">地址</label>
            <input class="form-input" v-model="form.url" placeholder="http://192.168.1.10:5244/dav" />
          </div>
          <div class="form-item">
            <label class="form-label">用户名</label>
            <input class="form-input" v-model="form.username" placeholder="用户名" autocapitalize="off" />
          </div>
          <div class="form-item">
            <label class="form-label">密码{{ editingId ? '（留空表示保持原值）' : '' }}</label>
            <input class="form-input" type="password" v-model="form.password" placeholder="密码" />
          </div>
          <div class="form-item">
            <label class="form-label">根目录 base_path（默认 /）</label>
            <input class="form-input" v-model="form.base_path" placeholder="/短剧" />
          </div>
          <div class="form-item">
            <label class="form-check">
              <input type="checkbox" v-model="form.insecure" /> 跳过 HTTPS 证书校验
            </label>
          </div>
        </template>

        <!-- jellyfin / emby：用户名+密码优先（保存时服务端自动换取 token），API Key 可选 -->
        <template v-else>
          <div class="form-item">
            <label class="form-label">地址</label>
            <input class="form-input" v-model="form.url" :placeholder="form.type === 'jellyfin' ? 'http://192.168.1.10:8096' : 'http://192.168.1.10:8095'" />
          </div>
          <div class="form-item">
            <label class="form-label">用户名（推荐，与密码一起填可免 API Key）</label>
            <input class="form-input" v-model="form.username" placeholder="用户名" autocapitalize="off" />
          </div>
          <div class="form-item">
            <label class="form-label">密码{{ editingId ? '（留空表示保持原值）' : '' }}</label>
            <input class="form-input" type="password" v-model="form.password" placeholder="密码" />
          </div>
          <div class="form-item">
            <label class="form-label">API Key（可选）{{ editingId ? '，留空表示保持原值' : '' }}</label>
            <input class="form-input" type="password" v-model="form.api_key" placeholder="API Key" />
          </div>
          <div class="form-item">
            <label class="form-label">用户 ID（可选）</label>
            <input class="form-input" v-model="form.user_id" placeholder="user_id" autocapitalize="off" />
          </div>
          <div class="form-item">
            <label class="form-check">
              <input type="checkbox" v-model="form.transcode" /> 网页端优先 HLS 转码
            </label>
          </div>
        </template>

        <p class="error-text" v-if="formErr">{{ formErr }}</p>

        <div class="modal-btns">
          <button class="btn-ghost" @click="showModal = false">取消</button>
          <button class="btn-primary" :disabled="saving" @click="submitForm">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sources-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
  padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 70px);
}
.sources-actions {
  display: flex;
  gap: 12px;
  padding: 14px 14px 4px;
}
.btn-add {
  flex: 1;
}
.sources-actions .btn-ghost {
  flex: 1;
  height: 46px;
}
.sources-err {
  padding: 8px 16px 0;
}
.source-list {
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.source-card {
  background: var(--bg-card);
  border-radius: var(--radius);
  padding: 14px;
}
.sc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.sc-name {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sc-icon {
  flex: none;
  color: var(--primary);
}
.sc-badge {
  flex: none;
  font-size: 11px;
  border-radius: 6px;
  padding: 1px 7px;
}
.badge-local {
  background: rgba(54, 207, 201, 0.15);
  color: #36cfc9;
}
.badge-webdav {
  background: rgba(122, 162, 255, 0.15);
  color: #7aa2ff;
}
.badge-jellyfin {
  background: rgba(255, 177, 77, 0.15);
  color: #ffb14d;
}
.badge-emby {
  background: rgba(82, 196, 26, 0.15);
  color: #52c41a;
}
.sc-state {
  margin-left: auto;
  flex: none;
  font-size: 11px;
  color: var(--text-faint);
}
.sc-state.on {
  color: #52c41a;
}
.sc-line {
  font-size: 12px;
  color: var(--text-dim);
  margin-bottom: 4px;
}
.sc-error {
  font-size: 12px;
  color: #ff6b6d;
  margin-bottom: 4px;
  word-break: break-all;
}
.sc-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}
.sc-btn {
  height: 30px;
  padding: 0 14px;
  border-radius: 15px;
  border: 1px solid var(--border);
  background: var(--bg-soft);
  color: var(--text);
  font-size: 12px;
}
.sc-btn.danger {
  color: #ff6b6d;
  border-color: rgba(255, 107, 109, 0.35);
}

/* 底部扫描状态条 */
.scan-status {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  padding: 10px 14px calc(env(safe-area-inset-bottom, 0px) + 10px);
  background: rgba(20, 20, 27, 0.95);
  border-top: 1px solid var(--border);
  font-size: 12px;
  color: var(--text-dim);
}
.scan-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
  margin-right: 6px;
  animation: pulse 1s ease-in-out infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}
.scan-err {
  color: #ff6b6d;
}

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.modal {
  width: 100%;
  max-height: 86vh;
  overflow-y: auto;
  background: #15151c;
  border-radius: 16px 16px 0 0;
  padding: 16px 16px calc(env(safe-area-inset-bottom, 0px) + 20px);
}
.modal-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}
.modal-btns {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}
.modal-btns .btn-ghost,
.modal-btns .btn-primary {
  flex: 1;
}
</style>
