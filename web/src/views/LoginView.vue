<script setup>
// 登录页：服务器地址（默认同源）+ 用户名密码；allow_register=true 时可切换注册
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { request, serverBase, setServerBase, saveAuth } from '../api'
import { useAppStore } from '../store'
import InhoLogo from '../components/InhoLogo.vue'

const router = useRouter()
const store = useAppStore()

const serverInput = ref(serverBase())
const username = ref('')
const password = ref('')
const mode = ref('login') // login | register
const allowRegister = ref(false)
const serverName = ref('InhoDrama')
const loading = ref(false)
const errMsg = ref('')

onMounted(async function () {
  try {
    const info = await request('/server-info')
    allowRegister.value = !!info.allow_register
    serverName.value = info.name || 'InhoDrama'
    store.setServerInfo(info)
  } catch (e) {
    // 获取服务器信息失败不阻塞登录
  }
})

function toggleMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  errMsg.value = ''
}

async function submit() {
  errMsg.value = ''
  const server = serverInput.value.trim().replace(/\/+$/, '')
  const name = username.value.trim()
  if (!server || !name || !password.value) {
    errMsg.value = '请填写完整'
    return
  }
  // 契约约定：注册密码至少 6 位
  if (mode.value === 'register' && password.value.length < 6) {
    errMsg.value = '密码至少 6 位'
    return
  }
  loading.value = true
  try {
    setServerBase(server)
    const path = mode.value === 'login' ? '/auth/login' : '/auth/register'
    const data = await request(path, {
      method: 'POST',
      body: { username: name, password: password.value }
    })
    saveAuth(data.token, data.user)
    store.setUser(data.user)
    router.replace('/')
  } catch (e) {
    errMsg.value = e.message || '操作失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-logo-mark">
        <InhoLogo :size="76" variant="dark" />
      </div>
      <h1 class="login-logo">Inho短剧</h1>
      <p class="login-sub">{{ serverName }}</p>

      <div class="form-item">
        <label class="form-label">服务器地址</label>
        <input
          class="form-input"
          v-model="serverInput"
          placeholder="http://192.168.1.10:8080"
          inputmode="url"
          autocapitalize="off"
          autocomplete="off"
          spellcheck="false"
        />
      </div>
      <div class="form-item">
        <label class="form-label">用户名</label>
        <input
          class="form-input"
          v-model="username"
          placeholder="用户名"
          autocomplete="username"
          autocapitalize="off"
        />
      </div>
      <div class="form-item">
        <label class="form-label">密码</label>
        <input
          class="form-input"
          type="password"
          v-model="password"
          placeholder="密码"
          autocomplete="current-password"
          @keyup.enter="submit"
        />
      </div>

      <p class="error-text login-err" v-if="errMsg">{{ errMsg }}</p>

      <button class="btn-primary login-btn" :disabled="loading" @click="submit">
        {{ loading ? '请稍候…' : (mode === 'login' ? '登 录' : '注 册') }}
      </button>

      <p class="login-toggle" v-if="allowRegister">
        <a @click.prevent="toggleMode">{{ mode === 'login' ? '没有账号？注册' : '已有账号？登录' }}</a>
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  min-height: 100dvh;
  background:
    radial-gradient(ellipse 60% 40% at 50% -10%, rgba(238, 43, 108, 0.14), transparent),
    var(--bg);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.login-box {
  width: 100%;
  max-width: 380px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 28px 24px 22px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.45);
}
.login-logo-mark {
  display: flex;
  justify-content: center;
  margin-bottom: 10px;
}
.login-logo {
  font-size: 30px;
  font-weight: 800;
  text-align: center;
  letter-spacing: 1px;
  background: var(--grad);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.login-sub {
  text-align: center;
  font-size: 13px;
  color: var(--text-dim);
  margin: 4px 0 24px;
}
.login-err {
  margin: -4px 0 12px;
}
.login-btn {
  width: 100%;
}
.login-toggle {
  margin-top: 18px;
  text-align: center;
  font-size: 13px;
  color: var(--text-dim);
}
.login-toggle a {
  color: var(--primary-2);
  cursor: pointer;
}
</style>
