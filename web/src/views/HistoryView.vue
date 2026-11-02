<script setup>
// 观看历史：GET /history 列表，继续播放，删除单条记录
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { request, coverUrl, fmtDur } from '../api'
import { useAppStore } from '../store'

const router = useRouter()
const store = useAppStore()

const items = ref([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const data = await request('/history')
    items.value = data.items || []
    store.setHistory(items.value)
  } catch (e) {
    // 静默
  } finally {
    loading.value = false
  }
}

// 删除某部剧的观看记录后刷新列表
async function removeItem(seriesId) {
  try {
    await request('/history/' + seriesId, { method: 'DELETE' })
    await load()
  } catch (e) {
    // 静默
  }
}

function pctOf(it) {
  if (it.duration && it.duration > 0) {
    return Math.min(100, Math.round((it.position / it.duration) * 100))
  }
  return it.completed ? 100 : 0
}

// 「看到 第X集 · 剩余mm:ss」（已看完单独标注）
function subText(it) {
  if (it.completed) return '看到 第' + it.episode.index + '集 · 已看完'
  const left = Math.max(0, (it.duration || 0) - (it.position || 0))
  return '看到 第' + it.episode.index + '集 · 剩余' + fmtDur(left)
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/')
}

onMounted(load)
</script>

<template>
  <div class="history-page">
    <header class="nav-bar">
      <button class="icon-btn" @click="goBack">
        <svg viewBox="0 0 24 24" width="24" height="24">
          <path d="M15.4 7.4 14 6l-6 6 6 6 1.4-1.4L10.8 12z" fill="currentColor"/>
        </svg>
      </button>
      <span class="nav-title">观看历史</span>
      <span class="nav-side"></span>
    </header>

    <div class="history-list" v-if="items.length">
      <div class="history-item" v-for="it in items" :key="it.series.id">
        <img class="hi-cover" :src="coverUrl(it.series.cover)" :alt="it.series.title" loading="lazy" />
        <div class="hi-info">
          <p class="hi-title">{{ it.series.title }}</p>
          <p class="hi-sub">{{ subText(it) }}</p>
          <div class="hi-bar"><i :style="{ width: pctOf(it) + '%' }"></i></div>
          <button
            class="btn-mini-primary"
            @click="router.push('/play/' + it.series.id + '?ep=' + it.episode.index)"
          >继续播放</button>
        </div>
        <button class="hi-del" title="删除记录" @click="removeItem(it.series.id)">
          <svg viewBox="0 0 24 24" width="18" height="18">
            <path d="M19 6.4 17.6 5 12 10.6 6.4 5 5 6.4 10.6 12 5 17.6 6.4 19l5.6-5.6 5.6 5.6 1.4-1.4L13.4 12z" fill="currentColor"/>
          </svg>
        </button>
      </div>
    </div>

    <div class="empty-tip" v-else-if="loading">加载中…</div>
    <div class="empty-tip" v-else>暂无观看记录</div>
  </div>
</template>

<style scoped>
.history-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
}
.history-list {
  padding: 12px 14px calc(env(safe-area-inset-bottom, 0px) + 30px);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.history-item {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 12px;
  background: var(--bg-card);
  border-radius: var(--radius);
}
.hi-cover {
  width: 78px;
  height: 104px;
  border-radius: 8px;
  object-fit: cover;
  background: var(--bg-soft);
  flex: none;
}
.hi-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.hi-title {
  font-size: 15px;
  font-weight: 600;
  padding-right: 26px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.hi-sub {
  font-size: 12px;
  color: var(--text-dim);
  margin: 4px 0 8px;
}
.hi-bar {
  width: 100%;
  height: 3px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.14);
  overflow: hidden;
  margin-bottom: 10px;
}
.hi-bar i {
  display: block;
  height: 100%;
  background: var(--grad);
  border-radius: 2px;
}
.hi-del {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--text-faint);
}
.hi-del:active {
  color: #ff6b6d;
  background: rgba(255, 107, 109, 0.12);
}
</style>
