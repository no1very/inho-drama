<script setup>
// 我的：用户信息、服务器地址设置、媒体源管理入口、退出登录
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { request, serverBase, setServerBase, clearAuth } from '../api'
import { useAppStore } from '../store'

const router = useRouter()
const store = useAppStore()

const version = computed(function () {
  return store.serverInfo && store.serverInfo.version ? store.serverInfo.version : '未知'
})
const serverAddr = computed(function () {
  return serverBase()
})

const editing = ref(false)
const serverInput = ref('')
const notice = ref('')

function startEdit() {
  serverInput.value = serverBase()
  editing.value = true
  notice.value = ''
}

function cancelEdit() {
  editing.value = false
}

// 保存服务器地址：变更后原 token 通常失效，提示并要求重新登录
function saveServer() {
  const url = serverInput.value.trim().replace(/\/+$/, '')
  if (!url) {
    notice.value = '服务器地址不能为空'
    return
  }
  setServerBase(url)
  editing.value = false
  clearAuth()
  store.setUser(null)
  notice.value = '服务器地址已保存，请重新登录'
  setTimeout(function () {
    router.replace('/login')
  }, 1200)
}

async function logout() {
  try {
    await request('/auth/logout', { method: 'POST' })
  } catch (e) {
    // 即使吊销失败也清理本地登录态
  }
  clearAuth()
  store.setUser(null)
  router.replace('/login')
}

onMounted(async function () {
  try {
    const info = await request('/server-info')
    store.setServerInfo(info)
  } catch (e) {
    // 获取版本号失败不阻塞页面
  }
})
</script>

<template>
  <div class="mine-page">
    <header class="nav-bar">
      <button class="icon-btn" @click="router.back()">
        <svg viewBox="0 0 24 24" width="24" height="24">
          <path d="M15.4 7.4 14 6l-6 6 6 6 1.4-1.4L10.8 12z" fill="currentColor"/>
        </svg>
      </button>
      <span class="nav-title">我的</span>
      <span class="nav-side"></span>
    </header>

    <!-- 用户卡片 -->
    <div class="mine-card">
      <div class="avatar">{{ store.username ? store.username[0] : 'N' }}</div>
      <div class="mine-info">
        <p class="mine-name">{{ store.username || '未登录' }}</p>
        <p class="mine-sub">服务器：{{ serverAddr }}</p>
        <p class="mine-sub">版本：v{{ version }}</p>
      </div>
    </div>

    <!-- 设置项 -->
    <div class="setting-group">
      <div class="setting-item" @click="startEdit">
        <span>服务器地址</span>
        <span class="setting-value">{{ serverAddr }}</span>
      </div>
      <div class="setting-item" @click="router.push('/admin/sources')">
        <span>媒体源管理</span>
        <span class="setting-arrow">›</span>
      </div>
    </div>

    <!-- 服务器地址编辑 -->
    <div class="server-edit" v-if="editing">
      <input
        class="form-input"
        v-model="serverInput"
        placeholder="http://192.168.1.10:8080"
        inputmode="url"
        autocapitalize="off"
        autocomplete="off"
        spellcheck="false"
      />
      <div class="edit-btns">
        <button class="btn-ghost" @click="cancelEdit">取消</button>
        <button class="btn-primary" @click="saveServer">保存</button>
      </div>
    </div>

    <p class="mine-notice" v-if="notice">{{ notice }}</p>

    <button class="btn-logout" @click="logout">退出登录</button>
  </div>
</template>

<style scoped>
.mine-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
  padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 30px);
}
.mine-card {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 16px 14px;
  padding: 18px 16px;
  background: var(--bg-card);
  border-radius: var(--radius);
}
.avatar {
  width: 54px;
  height: 54px;
  border-radius: 50%;
  background: var(--grad);
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.mine-info {
  min-width: 0;
}
.mine-name {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 4px;
}
.mine-sub {
  font-size: 12px;
  color: var(--text-dim);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.setting-group {
  margin: 0 14px;
  background: var(--bg-card);
  border-radius: var(--radius);
  overflow: hidden;
}
.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 16px;
  font-size: 15px;
  cursor: pointer;
}
.setting-item + .setting-item {
  border-top: 1px solid var(--border);
}
.setting-value {
  font-size: 12px;
  color: var(--text-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}
.setting-arrow {
  color: var(--text-faint);
  font-size: 18px;
}
.server-edit {
  margin: 12px 14px 0;
}
.edit-btns {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}
.edit-btns .btn-ghost,
.edit-btns .btn-primary {
  flex: 1;
  height: 40px;
  font-size: 14px;
}
.mine-notice {
  margin: 12px 16px 0;
  font-size: 13px;
  color: var(--primary-2);
}
.btn-logout {
  display: block;
  width: calc(100% - 28px);
  margin: 24px 14px 0;
  height: 46px;
  border-radius: 23px;
  background: rgba(255, 77, 79, 0.12);
  color: #ff6b6d;
  font-size: 15px;
  font-weight: 600;
}
</style>
