<script setup>
// 播放页（核心）：
// - 全屏播放器，muted 自动播放，首次触摸解除静音
// - 直连流失败时 HLS 兜底（&hls=1 + hls.js）
// - 上下滑动切集、点按控制层、底部抽屉选集
// - 进度上报（每 5s / 暂停 / 切集前 / 页面隐藏 / 退出 keepalive）
// - 续播定位、自动连播、字幕挂载
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Hls from 'hls.js'
import { request, streamUrl, subtitleUrl, reportProgress, fmtDur } from '../api'

const route = useRoute()
const router = useRouter()

const seriesId = Number(route.params.seriesId)

// 浏览器可直连播放的容器，其余容器出错时走 HLS 兜底
const NATIVE_CONTAINERS = ['mp4', 'm4v', 'webm', 'mov']

const series = ref(null)
const episodes = ref([])
const episodeStates = ref([])
const current = ref(null)
const loading = ref(true)
const pageError = ref('')

const playing = ref(false)
const showControls = ref(true)
const showDrawer = ref(false)
const showUnsupported = ref(false)
const toastMsg = ref('')
const curTime = ref(0)
const duration = ref(0)
const seeking = ref(false)
const seekTime = ref(0)

const videoRef = ref(null)

let hls = null
let hlsTried = false // 本集是否已尝试 HLS 兜底
let netRetried = false // 本集网络错误是否已原地址重试过一次
let resumed = false // 本集是否已执行续播定位
let switching = false // 切集过程中，抑制 pause 事件触发的多余上报
let reportTimer = null
let toastTimer = null
let controlsTimer = null
let touchX = 0
let touchY = 0
let touchAt = 0
let lastTouchAt = 0

const epTitle = computed(function () {
  return current.value ? '第' + current.value.index + '集' : ''
})
const hasPrev = computed(function () {
  return !!(current.value && findEpisode(current.value.index - 1))
})
const hasNext = computed(function () {
  return !!(current.value && findEpisode(current.value.index + 1))
})
const fmtCur = computed(function () {
  return fmtDur(curTime.value)
})
const fmtTotal = computed(function () {
  return fmtDur(duration.value)
})
const seekVal = computed(function () {
  return seeking.value ? seekTime.value : curTime.value
})
const seekPercent = computed(function () {
  return duration.value > 0 ? Math.min(100, (seekVal.value / duration.value) * 100) : 0
})

function findEpisode(index) {
  return episodes.value.find(function (e) {
    return e.index === index
  }) || null
}

function playSafe(video) {
  const p = video.play()
  if (p && p.catch) p.catch(function () {})
}

function toast(msg) {
  toastMsg.value = msg
  clearTimeout(toastTimer)
  toastTimer = setTimeout(function () {
    toastMsg.value = ''
  }, 1500)
}

// ===== 数据加载与切集 =====

// 进入时拉取剧集详情，确定起始集：
// ep 参数按 index 匹配；缺省用 continue_episode_id；再兜底第 1 集
async function load() {
  try {
    const data = await request('/series/' + seriesId)
    series.value = data.series
    episodes.value = data.episodes || []
    episodeStates.value = data.episode_states || []
    let ep = null
    const epParam = parseInt(route.query.ep, 10)
    if (!Number.isNaN(epParam)) ep = findEpisode(epParam)
    if (!ep && data.continue_episode_id) {
      ep = episodes.value.find(function (e) {
        return e.id === data.continue_episode_id
      }) || null
    }
    if (!ep && episodes.value.length) ep = episodes.value[0]
    if (!ep) {
      pageError.value = '该剧集暂无可播放的单集'
      return
    }
    switchTo(ep, false)
  } catch (e) {
    pageError.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

// 切换到指定单集（fromUser=true 时弹「第X集」提示）
function switchTo(ep, fromUser) {
  if (!ep) return
  // 切集前上报当前进度
  if (current.value) pushProgress(false, false)
  switching = true
  current.value = ep
  hlsTried = false
  netRetried = false
  resumed = false
  showUnsupported.value = false
  curTime.value = 0
  duration.value = Number(ep.duration) || 0
  seeking.value = false
  destroyHls()
  const video = videoRef.value
  if (video) {
    video.pause()
    video.src = streamUrl(ep.id, false)
    video.load()
    playSafe(video)
  }
  if (fromUser) toast('第' + ep.index + '集')
}

function destroyHls() {
  if (hls) {
    try {
      hls.destroy()
    } catch (e) {
      // 忽略
    }
    hls = null
  }
}

// HLS 兜底播放（&hls=1，仅 Jellyfin 源有效）
function startHls() {
  const video = videoRef.value
  if (!video || !current.value) return
  destroyHls()
  const url = streamUrl(current.value.id, true)
  if (Hls.isSupported()) {
    hls = new Hls()
    hls.on(Hls.Events.ERROR, function (_evt, data) {
      if (data && data.fatal) {
        destroyHls()
        showUnsupported.value = true
      }
    })
    hls.loadSource(url)
    hls.attachMedia(video)
    playSafe(video)
  } else {
    // 不支持 MSE 的浏览器直接把 m3u8 地址交给 video
    video.src = url
    video.load()
    playSafe(video)
  }
}

// ===== video 事件 =====

function onVideoError() {
  const video = videoRef.value
  switching = false
  if (!video || !video.error || !current.value || showUnsupported.value) return
  const code = video.error.code
  if (code !== 4) {
    // 网络类错误：忽略并原地址重试一次
    if (!netRetried) {
      netRetried = true
      video.load()
      playSafe(video)
    }
    return
  }
  // code=4：浏览器不支持该容器/编码，非 mp4/m4v/webm/mov 时改用 HLS 重试一次
  if (hlsTried) {
    showUnsupported.value = true
    return
  }
  if (NATIVE_CONTAINERS.indexOf(current.value.container) === -1) {
    hlsTried = true
    startHls()
  } else {
    showUnsupported.value = true
  }
}

function onLoadedMetadata() {
  const video = videoRef.value
  switching = false
  if (!video || !current.value) return
  if (video.duration && isFinite(video.duration)) duration.value = video.duration
  // 续播：position>5 且未接近结尾时跳到上次位置
  if (!resumed) {
    resumed = true
    const st = episodeStates.value.find(function (s) {
      return s.episode_id === current.value.id
    })
    if (st && st.position > 5 && st.duration > 0 && st.position < st.duration - 5) {
      try {
        video.currentTime = st.position
      } catch (e) {
        // 忽略
      }
    }
  }
  loadSubtitle()
}

// 字幕：GET /subtitle/{id} 返回 200 时挂 <track>（src 直接用 vtt 地址）
async function loadSubtitle() {
  const video = videoRef.value
  if (!video || !current.value) return
  try {
    const resp = await fetch(subtitleUrl(current.value.id))
    if (resp.status !== 200) return
    if (resp.body && resp.body.cancel) {
      try {
        resp.body.cancel()
      } catch (e) {
        // 忽略
      }
    }
    if (video.querySelector('track')) return
    const track = document.createElement('track')
    track.kind = 'subtitles'
    track.label = '字幕'
    track.srclang = 'zh'
    track.src = subtitleUrl(current.value.id)
    track.default = true
    video.appendChild(track)
  } catch (e) {
    // 无字幕，忽略
  }
}

function onTimeUpdate() {
  const video = videoRef.value
  if (!video) return
  if (!seeking.value) curTime.value = video.currentTime || 0
  if (video.duration && isFinite(video.duration)) duration.value = video.duration
}

function onPlay() {
  playing.value = true
}

function onPause() {
  playing.value = false
  if (!switching) pushProgress(false, false)
}

function onEnded() {
  playing.value = false
  // 先上报 completed=true
  pushProgress(true, false)
  const nxt = current.value ? findEpisode(current.value.index + 1) : null
  if (nxt) {
    toast('自动连播')
    switchTo(nxt, false)
  } else {
    toast('已是最后一集')
  }
}

// ===== 进度上报（多端互通核心）=====

function pushProgress(completed, keepalive) {
  const video = videoRef.value
  if (!current.value) return
  const position = video ? video.currentTime || 0 : 0
  let dur = video && video.duration && isFinite(video.duration) ? video.duration : 0
  if (!dur) dur = Number(current.value.duration) || 0
  reportProgress(
    {
      series_id: seriesId,
      episode_id: current.value.id,
      position: Math.round(position * 10) / 10,
      duration: Math.round(dur * 10) / 10,
      completed: !!completed
    },
    keepalive
  )
}

function startReportTimer() {
  stopReportTimer()
  reportTimer = setInterval(function () {
    const video = videoRef.value
    // 播放中才累计上报
    if (video && !video.paused && !video.ended) pushProgress(false, false)
  }, 5000)
}

function stopReportTimer() {
  if (reportTimer) {
    clearInterval(reportTimer)
    reportTimer = null
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'hidden') pushProgress(false, false)
}

function onBeforeUnload() {
  pushProgress(false, true) // keepalive
}

// ===== 交互：触摸滑动 / 点按 =====

function onStageTouchStart(e) {
  const t = e.touches && e.touches[0]
  if (!t) return
  touchX = t.clientX
  touchY = t.clientY
  touchAt = Date.now()
  // 首次触摸解除静音（自动播放需先 muted 解锁）
  const video = videoRef.value
  if (video && video.muted) video.muted = false
}

function onStageTouchEnd(e) {
  lastTouchAt = Date.now()
  const t = e.changedTouches && e.changedTouches[0]
  if (!t) return
  const dx = t.clientX - touchX
  const dy = t.clientY - touchY
  const adx = Math.abs(dx)
  const ady = Math.abs(dy)
  // 竖向滑动切集（水平位移小于垂直才判定）：
  // dy < -60 上滑 → 下一集；dy > 60 下滑 → 上一集
  if (ady > 60 && adx < ady) {
    if (dy < 0) {
      const nxt = current.value ? findEpisode(current.value.index + 1) : null
      if (nxt) {
        switchTo(nxt, true)
        return
      }
    } else {
      const prv = current.value ? findEpisode(current.value.index - 1) : null
      if (prv) {
        switchTo(prv, true)
        return
      }
    }
  }
  // 点按（位移很小、时间短）：切换控制层显隐
  if (adx < 10 && ady < 10 && Date.now() - touchAt < 400) {
    toggleControls()
  }
}

function onStageClick() {
  // 触摸端已在 touchend 处理过点按，避免重复触发
  if (Date.now() - lastTouchAt < 500) return
  const video = videoRef.value
  if (video && video.muted) video.muted = false
  toggleControls()
}

function toggleControls() {
  showControls.value = !showControls.value
  scheduleHide()
}

// 控制层 4 秒无操作自动隐藏
function scheduleHide() {
  clearTimeout(controlsTimer)
  if (showControls.value) {
    controlsTimer = setTimeout(function () {
      showControls.value = false
    }, 4000)
  }
}

// ===== 控制层操作 =====

function togglePlay() {
  const video = videoRef.value
  if (!video) return
  if (video.paused) playSafe(video)
  else video.pause()
}

// 上一集 / 下一集（按钮控件，与上下滑动手势等效）
function goPrevEpisode() {
  const prv = current.value ? findEpisode(current.value.index - 1) : null
  if (prv) switchTo(prv, true)
  else toast('已是第一集')
}

function goNextEpisode() {
  const nxt = current.value ? findEpisode(current.value.index + 1) : null
  if (nxt) switchTo(nxt, true)
  else toast('已是最后一集')
}

// 鼠标滚轮切集：向下滚 = 下一集，向上滚 = 上一集（600ms 节流防触控板惯性连切）
let lastWheelAt = 0
function onWheel(e) {
  if (showDrawer.value) return
  const now = Date.now()
  const delta = e.deltaY || 0
  if (Math.abs(delta) < 8 || now - lastWheelAt < 600) return
  lastWheelAt = now
  if (delta > 0) goNextEpisode()
  else goPrevEpisode()
}

function onSeekInput(e) {
  seeking.value = true
  seekTime.value = Number(e.target.value) || 0
}

function onSeekChange(e) {
  const video = videoRef.value
  const t = Number(e.target.value) || 0
  if (video && isFinite(t)) {
    try {
      video.currentTime = t
    } catch (err) {
      // 忽略
    }
    curTime.value = t
  }
  seeking.value = false
  scheduleHide()
}

function openDrawer() {
  showDrawer.value = true
}

function pickEpisode(ep) {
  showDrawer.value = false
  if (current.value && ep.id === current.value.id) return
  switchTo(ep, true)
}

function goBack() {
  pushProgress(false, true)
  if (window.history.length > 1) router.back()
  else router.replace('/')
}

onMounted(function () {
  const video = videoRef.value
  if (video) video.muted = true // 初始静音以解锁自动播放
  load()
  startReportTimer()
  document.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('beforeunload', onBeforeUnload)
  scheduleHide()
})

onBeforeUnmount(function () {
  pushProgress(false, false)
  stopReportTimer()
  clearTimeout(toastTimer)
  clearTimeout(controlsTimer)
  destroyHls()
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('beforeunload', onBeforeUnload)
})
</script>

<template>
  <div class="player-page" @wheel.prevent="onWheel">
    <!-- 播放舞台：触摸滑动切集 / 点按控制层 / 滚轮切集 -->
    <div
      class="player-stage"
      @touchstart="onStageTouchStart"
      @touchend="onStageTouchEnd"
      @click="onStageClick"
    >
      <video
        ref="videoRef"
        class="player-video"
        playsinline
        webkit-playsinline
        preload="auto"
        @loadedmetadata="onLoadedMetadata"
        @timeupdate="onTimeUpdate"
        @play="onPlay"
        @pause="onPause"
        @ended="onEnded"
        @error="onVideoError"
      ></video>
    </div>

    <!-- 轻提示 -->
    <div class="player-toast" v-if="toastMsg">{{ toastMsg }}</div>

    <!-- 控制层 -->
    <div class="player-controls" :class="{ hidden: !showControls }">
      <div class="ctrl-top">
        <button class="ctrl-btn" @click="goBack">
          <svg viewBox="0 0 24 24" width="24" height="24">
            <path d="M15.4 7.4 14 6l-6 6 6 6 1.4-1.4L10.8 12z" fill="currentColor"/>
          </svg>
        </button>
        <span class="ctrl-title">{{ series ? series.title : '' }} {{ epTitle }}</span>
      </div>

      <div class="ctrl-bottom">
        <button class="ctrl-btn" :class="{ disabled: !hasPrev }" title="上一集" @click="goPrevEpisode">
          <svg viewBox="0 0 24 24" width="24" height="24">
            <path d="M6 6h2v12H6zm3.5 6 8.5 6V6z" fill="currentColor"/>
          </svg>
        </button>
        <button class="ctrl-btn" title="播放/暂停" @click="togglePlay">
          <svg v-if="!playing" viewBox="0 0 24 24" width="26" height="26">
            <path d="M8 5v14l11-7z" fill="currentColor"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" width="26" height="26">
            <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" fill="currentColor"/>
          </svg>
        </button>
        <button class="ctrl-btn" :class="{ disabled: !hasNext }" title="下一集" @click="goNextEpisode">
          <svg viewBox="0 0 24 24" width="24" height="24">
            <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z" fill="currentColor"/>
          </svg>
        </button>
        <span class="ctrl-time">{{ fmtCur }}</span>
        <input
          class="seek-bar"
          type="range"
          min="0"
          :max="duration > 0 ? duration : 1"
          step="0.1"
          :disabled="duration <= 0"
          :value="seekVal"
          :style="{ '--percent': seekPercent + '%' }"
          @input="onSeekInput"
          @change="onSeekChange"
        />
        <span class="ctrl-time">{{ fmtTotal }}</span>
        <button class="ctrl-episodes" @click="openDrawer">选集</button>
      </div>
    </div>

    <!-- 选集抽屉 -->
    <div class="drawer-mask" v-if="showDrawer" @click="showDrawer = false">
      <div class="drawer no-scrollbar" @click.stop>
        <div class="drawer-head">
          <span>选集（共{{ episodes.length }}集）</span>
          <button class="icon-btn" @click="showDrawer = false">
            <svg viewBox="0 0 24 24" width="20" height="20">
              <path d="M19 6.4 17.6 5 12 10.6 6.4 5 5 6.4 10.6 12 5 17.6 6.4 19l5.6-5.6 5.6 5.6 1.4-1.4L13.4 12z" fill="currentColor"/>
            </svg>
          </button>
        </div>
        <div class="drawer-grid">
          <button
            v-for="ep in episodes"
            :key="ep.id"
            class="drawer-chip"
            :class="{ active: current && current.id === ep.id }"
            @click="pickEpisode(ep)"
          >{{ ep.index }}</button>
        </div>
      </div>
    </div>

    <!-- 加载中 -->
    <div class="player-mask" v-if="loading">
      <p>加载中…</p>
    </div>

    <!-- 页面级错误 -->
    <div class="player-mask" v-if="pageError">
      <p>{{ pageError }}</p>
      <button class="ctrl-episodes mask-btn" @click="goBack">返回</button>
    </div>

    <!-- 格式不支持兜底 -->
    <div class="player-mask" v-if="showUnsupported">
      <p>该格式网页端不支持，请使用安卓 App 播放</p>
      <button class="ctrl-episodes mask-btn" @click="openDrawer">选集</button>
    </div>
  </div>
</template>

<style scoped>
.player-page {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: #000;
  overflow: hidden;
}

/* 播放舞台 */
.player-stage {
  position: absolute;
  inset: 0;
}
.player-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #000;
}

/* 轻提示 */
.player-toast {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 30;
  background: rgba(0, 0, 0, 0.7);
  border-radius: 10px;
  padding: 10px 22px;
  font-size: 15px;
  pointer-events: none;
}

/* 控制层（容器不拦截事件，仅子区域可点） */
.player-controls {
  position: absolute;
  inset: 0;
  z-index: 20;
  pointer-events: none;
  transition: opacity 0.2s ease, visibility 0.2s;
}
.player-controls.hidden {
  opacity: 0;
  visibility: hidden;
}
.ctrl-top {
  pointer-events: auto;
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: calc(env(safe-area-inset-top, 0px) + 6px) 10px 12px;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.65), rgba(0, 0, 0, 0));
}
.ctrl-title {
  font-size: 15px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ctrl-bottom {
  pointer-events: auto;
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 14px calc(env(safe-area-inset-bottom, 0px) + 14px);
  background: linear-gradient(to top, rgba(0, 0, 0, 0.75), rgba(0, 0, 0, 0));
}
.ctrl-btn {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex: none;
}
.ctrl-btn.disabled {
  opacity: 0.32;
  pointer-events: none;
}
.ctrl-time {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  flex: none;
  font-variant-numeric: tabular-nums;
}
.ctrl-episodes {
  flex: none;
  height: 32px;
  padding: 0 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* 进度条（可拖拽 seek） */
.seek-bar {
  -webkit-appearance: none;
  appearance: none;
  flex: 1;
  height: 4px;
  border-radius: 2px;
  outline: none;
  background: linear-gradient(
    to right,
    #ff4d4f 0%,
    #ff7a45 var(--percent, 0%),
    rgba(255, 255, 255, 0.25) var(--percent, 0%)
  );
}
.seek-bar::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.5);
}
.seek-bar::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border: none;
  border-radius: 50%;
  background: #fff;
}
.seek-bar:disabled {
  opacity: 0.5;
}

/* 选集抽屉 */
.drawer-mask {
  position: absolute;
  inset: 0;
  z-index: 40;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
}
.drawer {
  width: 100%;
  max-height: 60vh;
  overflow-y: auto;
  background: #14141b;
  border-radius: 16px 16px 0 0;
  padding: 16px 16px calc(env(safe-area-inset-bottom, 0px) + 16px);
}
.drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
}
.drawer-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.drawer-chip {
  height: 38px;
  border-radius: 10px;
  background: var(--bg-soft);
  border: 1px solid var(--border);
  color: var(--text-dim);
  font-size: 14px;
}
.drawer-chip.active {
  background: var(--grad);
  border-color: transparent;
  color: #fff;
  font-weight: 600;
}

/* 加载 / 错误 / 不支持 遮罩 */
.player-mask {
  position: absolute;
  inset: 0;
  z-index: 35;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 0 32px;
  text-align: center;
  font-size: 15px;
  color: var(--text-dim);
}
.mask-btn {
  height: 38px;
  padding: 0 22px;
  border-radius: 19px;
  background: var(--grad);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}
</style>
