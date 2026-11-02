<script setup>
// 搜索页：输入防抖 300ms 调 GET /series?keyword=，结果两列网格
import { ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../api'
import SeriesCard from '../components/SeriesCard.vue'

const router = useRouter()

const keyword = ref('')
const items = ref([])
const searched = ref(false) // 是否已执行过搜索（区分空态文案）
const searching = ref(false)
let timer = null

function onInput() {
  clearTimeout(timer)
  timer = setTimeout(doSearch, 300)
}

async function doSearch() {
  searching.value = true
  try {
    const kw = keyword.value.trim()
    const data = await request('/series?keyword=' + encodeURIComponent(kw) + '&page=1&page_size=50')
    items.value = data.items || []
    searched.value = true
  } catch (e) {
    // 失败保持原状
  } finally {
    searching.value = false
  }
}

onBeforeUnmount(function () {
  clearTimeout(timer)
})
</script>

<template>
  <div class="search-page">
    <header class="search-bar">
      <button class="icon-btn" @click="router.back()">
        <svg viewBox="0 0 24 24" width="24" height="24">
          <path d="M15.4 7.4 14 6l-6 6 6 6 1.4-1.4L10.8 12z" fill="currentColor"/>
        </svg>
      </button>
      <input
        class="search-input"
        v-model="keyword"
        @input="onInput"
        @keyup.enter="doSearch"
        type="search"
        placeholder="搜索剧名"
        enterkeyhint="search"
        autocomplete="off"
      />
    </header>

    <div class="search-body" v-if="items.length">
      <div class="search-grid">
        <SeriesCard v-for="s in items" :key="s.id" :series="s" />
      </div>
    </div>
    <div class="empty-tip" v-else-if="searching">搜索中…</div>
    <div class="empty-tip" v-else-if="searched">没有找到「{{ keyword }}」相关的剧集</div>
    <div class="empty-tip" v-else>输入剧名，发现好剧</div>
  </div>
</template>

<style scoped>
.search-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
}
.search-bar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: calc(env(safe-area-inset-top, 0px) + 6px) 8px 6px;
  background: rgba(11, 11, 15, 0.92);
  backdrop-filter: blur(10px);
}
.search-input {
  flex: 1;
  height: 38px;
  border-radius: 19px;
  border: 1px solid var(--border);
  background: var(--bg-soft);
  color: var(--text);
  padding: 0 14px;
  font-size: 14px;
  outline: none;
}
.search-input:focus {
  border-color: var(--primary);
}
.search-body {
  padding: 12px 14px 30px;
}
.search-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
</style>
