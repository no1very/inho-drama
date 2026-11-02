<script setup>
// 通用剧集网格卡片：搜索结果等网格场景复用，点击进详情页
import { useRouter } from 'vue-router'
import { coverUrl } from '../api'

const props = defineProps({
  series: { type: Object, required: true }
})

const router = useRouter()

function open() {
  router.push('/detail/' + props.series.id)
}
</script>

<template>
  <div class="series-card" @click="open">
    <div class="card-cover">
      <img v-if="series.cover" :src="coverUrl(series.cover)" :alt="series.title" loading="lazy" />
      <span class="card-count">{{ series.episode_count }}集</span>
    </div>
    <p class="card-title">{{ series.title }}</p>
  </div>
</template>

<style scoped>
.series-card {
  cursor: pointer;
  transition: transform 0.2s ease;
}
.series-card:hover {
  transform: translateY(-3px);
}
.card-cover {
  position: relative;
  width: 100%;
  padding-top: 133%; /* 3:4 海报比例 */
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--bg-soft);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.35);
}
.card-cover img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}
.series-card:hover .card-cover img {
  transform: scale(1.06);
}
/* 底部渐变遮罩：集数徽章在任何封面上都可读 */
.card-cover::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.45), transparent 36%);
  pointer-events: none;
}
.card-count {
  position: absolute;
  right: 6px;
  bottom: 6px;
  z-index: 1;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: var(--grad);
  border-radius: 7px;
  padding: 2px 7px;
}
.card-title {
  margin-top: 7px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.series-card:hover .card-title {
  color: #fff;
}
</style>
