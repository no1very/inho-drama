// ===== 路由（hash 模式）+ 登录守卫 =====
import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from './api'
import FeedView from './views/FeedView.vue'
import SearchView from './views/SearchView.vue'
import DetailView from './views/DetailView.vue'
import PlayerView from './views/PlayerView.vue'
import HistoryView from './views/HistoryView.vue'
import MineView from './views/MineView.vue'
import LoginView from './views/LoginView.vue'
import AdminSourcesView from './views/AdminSourcesView.vue'

const routes = [
  { path: '/', name: 'feed', component: FeedView },
  { path: '/search', name: 'search', component: SearchView },
  { path: '/detail/:id', name: 'detail', component: DetailView },
  { path: '/play/:seriesId', name: 'play', component: PlayerView },
  { path: '/history', name: 'history', component: HistoryView },
  { path: '/mine', name: 'mine', component: MineView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/admin/sources', name: 'admin-sources', component: AdminSourcesView },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 路由守卫：除登录页外，未登录一律跳 #/login
router.beforeEach(function (to) {
  if (to.name === 'login') return true
  if (!getToken()) return { name: 'login' }
  return true
})

export default router
