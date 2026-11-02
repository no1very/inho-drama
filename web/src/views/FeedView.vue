<script setup>
// 首页：双模式 —— 短视频流（全屏上下滑动刷剧）与 海报墙（网格浏览），右上角切换；
// 继续观看横滑区只显示未看完的记录（finished=0），看完的剧自动从首页消失。
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { request, coverUrl } from '../api'
import { useAppStore } from '../store'
import SeriesCard from '../components/SeriesCard.vue'
import InhoLogo from '../components/InhoLogo.vue'

const router = useRouter()
const store = useAppStore()

const MODE_KEY = 'nasdrama.homeMode'
// 首页模式：feed=短视频刷剧流；wall=海报墙
const mode = ref('feed')

const items = ref([])
const nextCursor = ref(0)
const hasMore = ref(true)
const loading = ref(false)
const historyItems = ref([])

const scrollEl = ref(null)

function loadMode() {
  try {
    const m = localStorage.getItem(MODE_KEY)
    if (m === 'feed' || m === 'wall') mode.value = m
  } catch (e) {
    // 忽略
  }
}

function toggleMode() {
  mode.value = mode.value === 'feed' ? 'wall' : 'feed'
  try {
    localStorage.setItem(MODE_KEY, mode.value)
  } catch (e) {
    // 忽略
  }
}

// 加载一页 feed（reset=true 时从头加载），两种模式共用同一数据源
async function loadFeed(reset) {
  if (loading.value) return
  if (!reset && !hasMore.value) return
  loading.value = true
  try {
    const cursor = reset ? 0 : nextCursor.value
    const data = await request('/feed?cursor=' + cursor + '&limit=12')
    if (reset) items.value = []
    items.value = items.value.concat(data.items || [])
    nextCursor.value = data.next_cursor != null ? data.next_cursor : 0
    hasMore.value = data.next_cursor != null
  } catch (e) {
    // 401 已在 api 封装统一处理，这里静默
  } finally {
    loading.value = false
  }
}

// 继续观看列表（GET /history?finished=0：只取未看完，看完的不再占据首页）
async function loadHistory() {
  try {
    const data = await request('/history?finished=0')
    historyItems.value = data.items || []
    store.setHistory(historyItems.value)
  } catch (e) {
    historyItems.value = []
  }
}

// 滚动接近底部时用 next_cursor 加载下一页（两种模式的滚动容器共用）
function onScroll(e) {
  const el = e && e.target ? e.target : scrollEl.value
  if (!el || loading.value || !hasMore.value) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - el.clientHeight * 0.5) {
    loadFeed(false)
  }
}

// 历史条目进度百分比
function pctOf(it) {
  if (it.duration && it.duration > 0) {
    return Math.min(100, Math.round((it.position / it.duration) * 100))
  }
  return it.completed ? 100 : 0
}

function goPlay(it) {
  router.push('/play/' + it.series.id + '?ep=' + it.episode.index)
}

onMounted(function () {
  loadMode()
  loadFeed(true)
  loadHistory()
})
</script>

<template>
  <div class="feed-page">
    <!-- 顶部固定栏 -->
    <header class="feed-topbar">
      <span class="logo">
        <InhoLogo :size="24" variant="dark" />
        <span class="logo-text">Inho短剧</span>
      </span>
      <nav class="topbar-actions">
        <!-- 模式切换：当前为短视频流时显示海报墙图标，反之显示播放图标 -->
        <button class="icon-btn" :title="mode === 'feed' ? '切换为海报墙' : '切换为短视频'" @click="toggleMode">
          <svg v-if="mode === 'feed'" viewBox="0 0 24 24" width="22" height="22">
            <path d="M3 3h8v8H3V3zm2 2v4h4V5H5zm8-2h8v8h-8V3zm2 2v4h4V5h-4zM3 13h8v8H3v-8zm2 2v4h4v-4H5zm8-2h8v8h-8v-8zm2 2v4h4v-4h-4z" fill="currentColor"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" width="22" height="22">
            <path d="M4 5h16v11H4V5zm2 2v7h12V7H6zm4 11h4v2h-4v-2z" fill="currentColor"/>
            <path d="M10 8.5v4l3.5-2z" fill="currentColor"/>
          </svg>
        </button>
        <button class="icon-btn" title="搜索" @click="router.push('/search')">
          <svg viewBox="0 0 24 24" width="22" height="22">
            <path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z" fill="currentColor"/>
          </svg>
        </button>
        <button class="icon-btn" title="历史" @click="router.push('/history')">
          <svg viewBox="0 0 24 24" width="22" height="22">
            <path d="M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6a7 7 0 1 1 7 7 6.95 6.95 0 0 1-4.9-2.03l-1.42 1.42A8.95 8.95 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z" fill="currentColor"/>
          </svg>
        </button>
        <button class="icon-btn" title="我的" @click="router.push('/mine')">
          <svg viewBox="0 0 24 24" width="22" height="22">
            <path d="M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" fill="currentColor"/>
          </svg>
        </button>
      </nav>
    </header>

    <!-- ===== 短视频模式：继续观看(fixed) + 全屏竖滑刷剧流 ===== -->
    <template v-if="mode === 'feed'">
      <section class="continue-strip no-scrollbar" v-if="historyItems.length">
        <p class="strip-title">继续观看</p>
        <div class="strip-scroll no-scrollbar">
          <div class="strip-card" v-for="it in historyItems" :key="it.series.id" @click="goPlay(it)">
            <div class="strip-cover">
              <img :src="coverUrl(it.series.cover)" :alt="it.series.title" loading="lazy" />
            </div>
            <div class="strip-info">
              <p class="strip-name">{{ it.series.title }}</p>
              <p class="strip-ep">第{{ it.episode.index }}集</p>
              <div class="strip-bar"><i :style="{ width: pctOf(it) + '%' }"></i></div>
            </div>
          </div>
        </div>
      </section>

      <div class="feed-scroll no-scrollbar" ref="scrollEl" @scroll="onScroll">
        <section class="feed-item" v-for="s in items" :key="s.id">
          <img class="feed-cover" :src="coverUrl(s.cover)" :alt="s.title" loading="lazy" />
          <div class="feed-shade"></div>
          <div class="feed-info">
            <h2 class="feed-title">{{ s.title }}</h2>
            <p class="feed-intro" v-if="s.intro">{{ s.intro }}</p>
            <p class="feed-meta">共{{ s.episode_count }}集 · {{ s.source_name }}</p>
          </div>
          <div class="feed-actions">
            <button class="feed-btn-detail" @click="router.push('/detail/' + s.id)">详情</button>
            <button class="feed-btn-play" @click="router.push('/play/' + s.id)">立即播放</button>
          </div>
        </section>

        <div class="feed-tip" v-if="loading && items.length">加载中…</div>
        <div class="feed-tip" v-else-if="!hasMore && items.length">没有更多了</div>
        <div class="feed-empty" v-if="!loading && !items.length">
          <p>暂无剧集</p>
          <p class="sub">请在「我的 → 媒体源管理」中添加媒体源并扫描</p>
        </div>
        <div class="feed-empty" v-else-if="loading && !items.length">
          <p>加载中…</p>
        </div>
      </div>
    </template>

    <!-- ===== 海报墙模式：普通滚动，顶部继续观看(inline) + 两列海报网格 ===== -->
    <div v-else class="wall-scroll no-scrollbar" @scroll="onScroll">
      <section class="continue-strip inline no-scrollbar" v-if="historyItems.length">
        <p class="strip-title">继续观看</p>
        <div class="strip-scroll no-scrollbar">
          <div class="strip-card" v-for="it in historyItems" :key="it.series.id" @click="goPlay(it)">
            <div class="strip-cover">
              <img :src="coverUrl(it.series.cover)" :alt="it.series.title" loading="lazy" />
            </div>
            <div class="strip-info">
              <p class="strip-name">{{ it.series.title }}</p>
              <p class="strip-ep">第{{ it.episode.index }}集</p>
              <div class="strip-bar"><i :style="{ width: pctOf(it) + '%' }"></i></div>
            </div>
          </div>
        </div>
      </section>

      <div class="wall-section-title">全部剧集</div>
      <div class="wall-grid">
        <SeriesCard v-for="s in items" :key="s.id" :series="s" />
      </div>

      <div class="wall-tip" v-if="loading && items.length">加载中…</div>
      <div class="wall-tip" v-else-if="!hasMore && items.length">没有更多了</div>
      <div class="wall-empty" v-if="!loading && !items.length">
        <p>暂无剧集</p>
        <p class="sub">请在「我的 → 媒体源管理」中添加媒体源并扫描</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.feed-page {
  height: 100vh;
  height: 100dvh;
  background: var(--bg);
  overflow: hidden;
}

/* 顶部固定栏 */
.feed-topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(env(safe-area-inset-top, 0px) + 8px) 10px 8px 16px;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.65), rgba(0, 0, 0, 0));
  pointer-events: none; /* 仅图标可点，其余区域穿透 */
}
.logo {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 19px;
  font-weight: 800;
  letter-spacing: 0.5px;
}
.logo-text {
  background: var(--grad);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.topbar-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  pointer-events: auto;
}
.topbar-actions .icon-btn {
  background: rgba(0, 0, 0, 0.25);
}

/* 继续观看横滑区（短视频模式下 fixed 悬浮；海报墙模式下 inline 常驻） */
.continue-strip {
  position: fixed;
  top: calc(env(safe-area-inset-top, 0px) + 52px);
  left: 0;
  right: 0;
  z-index: 15;
  padding: 8px 0 10px 14px;
  background: rgba(11, 11, 15, 0.72);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}
.continue-strip.inline {
  position: static;
  /* 顶部让位已由 .wall-scroll 的 padding-top 统一提供，这里只留正常间距，
     避免两处叠加把「继续观看」推得过低 */
  padding: 8px 0 4px 14px;
  background: var(--bg);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
.strip-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-dim);
  margin-bottom: 8px;
}
.strip-title::before {
  content: '';
  width: 3px;
  height: 12px;
  border-radius: 2px;
  background: var(--grad);
}
.strip-scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
}
.strip-card {
  display: flex;
  gap: 8px;
  width: 216px;
  flex: none;
  cursor: pointer;
  padding: 6px;
  margin: -6px;
  border-radius: 10px;
  transition: background 0.15s ease, transform 0.15s ease;
}
.strip-card:hover {
  background: rgba(255, 255, 255, 0.06);
  transform: translateY(-2px);
}
.strip-cover {
  width: 60px;
  height: 80px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--bg-soft);
  flex: none;
}
.strip-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.strip-info {
  flex: 1;
  min-width: 0;
  padding-top: 4px;
}
.strip-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.strip-ep {
  font-size: 11px;
  color: var(--text-dim);
  margin: 3px 0 6px;
}
.strip-bar {
  height: 3px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.18);
  overflow: hidden;
}
.strip-bar i {
  display: block;
  height: 100%;
  background: var(--grad);
  border-radius: 2px;
}

/* ===== 短视频模式：竖向滑动刷剧流 ===== */
.feed-scroll {
  height: 100vh;
  height: 100dvh;
  overflow-y: auto;
  scroll-snap-type: y mandatory;
  -webkit-overflow-scrolling: touch;
  position: relative;
}
.feed-item {
  position: relative;
  height: 100vh;
  height: 100dvh;
  scroll-snap-align: start;
  scroll-snap-stop: always;
  overflow: hidden;
}
.feed-cover {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: var(--bg-soft);
}
.feed-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to top,
    rgba(0, 0, 0, 0.85) 0%,
    rgba(0, 0, 0, 0.35) 38%,
    rgba(0, 0, 0, 0.08) 62%,
    rgba(0, 0, 0, 0.4) 100%
  );
}
.feed-info {
  position: absolute;
  left: 16px;
  right: 92px;
  bottom: 40px;
  z-index: 2;
}
.feed-title {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.25;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.6);
}
.feed-intro {
  margin-top: 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.82);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.feed-meta {
  margin-top: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.65);
}

/* 右下角悬浮操作按钮 */
.feed-actions {
  position: absolute;
  right: 14px;
  bottom: 46px;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}
.feed-btn-detail {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  color: #fff;
  font-size: 12px;
}
.feed-btn-play {
  width: 66px;
  height: 66px;
  border-radius: 50%;
  background: var(--grad);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 6px 20px rgba(255, 77, 79, 0.45);
}
.feed-btn-play:active {
  transform: scale(0.95);
}

/* 加载/空态提示（短视频模式） */
.feed-tip {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 16px);
  z-index: 18;
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  pointer-events: none;
}
.feed-empty {
  position: fixed;
  inset: 0;
  z-index: 12;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--bg);
  color: var(--text-dim);
  font-size: 15px;
}
.feed-empty .sub {
  font-size: 12px;
  color: var(--text-faint);
}

/* ===== 海报墙模式 ===== */
.wall-scroll {
  height: 100vh;
  height: 100dvh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  /* 让出固定顶栏高度：顶栏是 fixed 悬浮，没有这一段时「全部剧集」会从
     视口第 0 像素开始排版，与 16px 左边距的 logo 叠印在一起 */
  padding-top: calc(env(safe-area-inset-top, 0px) + 52px);
  padding-right: 14px;
}
.wall-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
  padding: 14px 0 10px 16px;
}
.wall-section-title::before {
  content: '';
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: var(--grad);
}
.wall-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px 12px;
  padding: 0 2px 16px 16px;
}
@media (min-width: 640px) {
  .wall-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
.wall-tip {
  text-align: center;
  font-size: 12px;
  color: var(--text-dim);
  padding: 12px 0 20px;
}
.wall-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 80px 0;
  color: var(--text-dim);
  font-size: 15px;
}
.wall-empty .sub {
  font-size: 12px;
  color: var(--text-faint);
}
</style>
