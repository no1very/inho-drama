<script setup>
// 详情页：大封面背景、剧集信息、立即播放/继续播放、选集网格
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request, coverUrl } from '../api'

const route = useRoute()
const router = useRouter()

const seriesId = Number(route.params.id)

const series = ref(null)
const episodes = ref([])
const states = ref([])
const continueId = ref(null)
const loading = ref(true)
const errMsg = ref('')

// 建议续播的单集对象
const continueEp = computed(function () {
  if (!continueId.value) return null
  return episodes.value.find(function (e) {
    return e.id === continueId.value
  }) || null
})
const firstEpId = computed(function () {
  return episodes.value.length ? episodes.value[0].id : null
})
// continue_episode_id 非空且与第 1 集不同时，显示「继续播放 第X集」
const showContinue = computed(function () {
  return !!(continueEp.value && firstEpId.value && continueEp.value.id !== firstEpId.value)
})

// 单集观看状态
function stateOf(ep) {
  return states.value.find(function (s) {
    return s.episode_id === ep.id
  }) || null
}

// 选集 chip 样式：done=已看完（置灰+✓），half=看了一半（高亮边框）
function chipClass(ep) {
  const st = stateOf(ep)
  return {
    done: !!(st && st.completed),
    half: !!(st && !st.completed && st.position > 5)
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/')
}

onMounted(async function () {
  try {
    const data = await request('/series/' + seriesId)
    series.value = data.series
    episodes.value = data.episodes || []
    states.value = data.episode_states || []
    continueId.value = data.continue_episode_id
  } catch (e) {
    errMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="detail-page">
    <!-- 顶部大封面背景 -->
    <div class="detail-hero">
      <img v-if="series && series.cover" class="hero-img" :src="coverUrl(series.cover)" :alt="series.title" />
      <div class="hero-shade"></div>
      <button class="icon-btn hero-back" @click="goBack">
        <svg viewBox="0 0 24 24" width="24" height="24">
          <path d="M15.4 7.4 14 6l-6 6 6 6 1.4-1.4L10.8 12z" fill="currentColor"/>
        </svg>
      </button>
      <div class="hero-info" v-if="series">
        <h1 class="hero-title">{{ series.title }}</h1>
        <p class="hero-meta">
          <span class="hero-tag" v-if="series.category">{{ series.category }}</span>
          <span>共{{ series.episode_count }}集</span>
          <span class="hero-dot">·</span>
          <span>{{ series.source_name }}</span>
        </p>
      </div>
    </div>

    <div class="detail-body">
      <p class="error-text" v-if="errMsg">{{ errMsg }}</p>

      <template v-if="series">
        <p class="detail-intro">{{ series.intro || '暂无简介' }}</p>

        <div class="detail-btns">
          <button
            class="btn-primary detail-play"
            :disabled="!episodes.length"
            @click="router.push('/play/' + seriesId + '?ep=1')"
          >▶ 立即播放</button>
          <button
            v-if="showContinue"
            class="btn-ghost detail-continue"
            @click="router.push('/play/' + seriesId + '?ep=' + continueEp.index)"
          >继续播放 第{{ continueEp.index }}集</button>
        </div>

        <!-- 选集网格 -->
        <div class="ep-section" v-if="episodes.length">
          <h3 class="ep-title">选集</h3>
          <div class="ep-grid">
            <button
              v-for="ep in episodes"
              :key="ep.id"
              class="ep-chip"
              :class="chipClass(ep)"
              @click="router.push('/play/' + seriesId + '?ep=' + ep.index)"
            >{{ ep.index }}</button>
          </div>
        </div>
      </template>

      <div class="empty-tip" v-if="loading">加载中…</div>
    </div>
  </div>
</template>

<style scoped>
.detail-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
}
.detail-hero {
  position: relative;
  height: 340px;
  background: var(--bg-soft);
}
.hero-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.hero-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to top,
    var(--bg) 4%,
    rgba(11, 11, 15, 0.35) 45%,
    rgba(11, 11, 15, 0.4) 100%
  );
}
.hero-back {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 8px);
  left: 8px;
  z-index: 2;
  background: rgba(0, 0, 0, 0.35);
}
.hero-info {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 14px;
}
.hero-title {
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 6px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.6);
}
.hero-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-dim);
}
.hero-tag {
  background: rgba(255, 77, 79, 0.18);
  color: var(--primary-2);
  border-radius: 6px;
  padding: 1px 8px;
  font-size: 12px;
}
.hero-dot {
  color: var(--text-faint);
}
.detail-body {
  padding: 4px 16px 32px;
}
.detail-intro {
  color: var(--text-dim);
  font-size: 14px;
  line-height: 1.7;
  margin-bottom: 16px;
}
.detail-btns {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 24px;
}
.detail-play,
.detail-continue {
  width: 100%;
}
.ep-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}
.ep-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.ep-chip {
  height: 40px;
  border-radius: 10px;
  background: var(--bg-soft);
  border: 1px solid var(--border);
  color: var(--text);
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 看了一半：高亮边框 */
.ep-chip.half {
  border-color: var(--primary);
  box-shadow: 0 0 0 1px rgba(255, 77, 79, 0.35);
}
/* 已看完：置灰 + ✓ */
.ep-chip.done {
  background: #131318;
  color: var(--text-faint);
  border-color: rgba(255, 255, 255, 0.05);
}
.ep-chip.done::after {
  content: '✓';
  margin-left: 4px;
}
</style>
