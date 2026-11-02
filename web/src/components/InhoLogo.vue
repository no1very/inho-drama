<script setup>
// Inho 品牌图标：墨镜猴 + 播放键（来自 inho-monkey-logo 设计稿）
// variant=brand 品牌橙红渐变底（favicon/浅色场景）；variant=dark 深紫渐变底（深色 UI 顶栏/登录页）
import { computed } from 'vue'

const props = defineProps({
  size: { type: [Number, String], default: 24 },
  variant: { type: String, default: 'dark' }
})

const PALETTES = {
  brand: { fur: '#FFF6EA', patch: '#F7D8B0', inner: '#FF9E6E', line: '#3A2317', glint: 'rgba(255,255,255,.55)', play: '#FFFFFF' },
  dark: { fur: '#FFF6EA', patch: '#F2CD9F', inner: '#FF8A5C', line: '#3A2317', glint: 'rgba(255,255,255,.55)', play: '#FFFFFF' }
}
const c = computed(() => PALETTES[props.variant] || PALETTES.dark)
const gradId = 'inho-grad-' + props.variant
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 512 512" aria-hidden="true" class="inho-logo">
    <defs>
      <linearGradient :id="gradId" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" :stop-color="variant === 'brand' ? '#FF8A3C' : '#33203F'" />
        <stop offset="1" :stop-color="variant === 'brand' ? '#EE2B6C' : '#170F22'" />
      </linearGradient>
      <radialGradient v-if="variant === 'brand'" :id="gradId + '-glow'" cx="0.3" cy="0.22" r="0.9">
        <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.26" />
        <stop offset="0.55" stop-color="#FFFFFF" stop-opacity="0" />
      </radialGradient>
    </defs>
    <rect width="512" height="512" rx="116" :fill="`url(#${gradId})`" />
    <rect v-if="variant === 'brand'" width="512" height="512" rx="116" :fill="`url(#${gradId}-glow)`" />
    <!-- 墨镜猴（几何与设计稿一致） -->
    <circle cx="108" cy="248" r="56" :fill="c.fur" />
    <circle cx="404" cy="248" r="56" :fill="c.fur" />
    <circle cx="112" cy="252" r="28" :fill="c.inner" />
    <circle cx="400" cy="252" r="28" :fill="c.inner" />
    <circle cx="256" cy="262" r="156" :fill="c.fur" />
    <circle cx="256" cy="214" r="70" :fill="c.patch" />
    <circle cx="216" cy="310" r="64" :fill="c.patch" />
    <circle cx="296" cy="310" r="64" :fill="c.patch" />
    <rect x="172" y="220" width="62" height="17" rx="8.5" :fill="c.line" transform="rotate(8 203 228)" />
    <rect x="278" y="220" width="62" height="17" rx="8.5" :fill="c.line" transform="rotate(-8 309 228)" />
    <rect x="164" y="246" width="84" height="60" rx="20" :fill="c.line" />
    <rect x="264" y="246" width="84" height="60" rx="20" :fill="c.line" />
    <rect x="238" y="256" width="36" height="14" rx="7" :fill="c.line" />
    <path d="M190 290 L212 262" :stroke="c.glint" stroke-width="7" stroke-linecap="round" />
    <path d="M290 290 L312 262" :stroke="c.glint" stroke-width="7" stroke-linecap="round" />
    <circle cx="241" cy="316" r="8" :fill="c.line" />
    <circle cx="271" cy="316" r="8" :fill="c.line" />
    <rect x="214" y="334" width="84" height="58" rx="20" :fill="c.line" />
    <path d="M248 347 L248 379 L276 363 Z" :fill="c.play" />
  </svg>
</template>
