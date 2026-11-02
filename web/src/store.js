// ===== 全局状态（pinia）：用户、服务器信息、观看历史缓存 =====
import { defineStore } from 'pinia'
import { getSavedUser } from './api'

export const useAppStore = defineStore('app', {
  state: function () {
    return {
      user: getSavedUser(),
      serverInfo: null, // { name, version, allow_register }
      historyCache: [], // 已加载的继续观看列表缓存
      historyLoadedAt: 0
    }
  },
  getters: {
    username: function (state) {
      return state.user && state.user.username ? state.user.username : ''
    }
  },
  actions: {
    setUser(user) {
      this.user = user
    },
    setServerInfo(info) {
      this.serverInfo = info
    },
    setHistory(items) {
      this.historyCache = items || []
      this.historyLoadedAt = Date.now()
    },
    removeHistory(seriesId) {
      this.historyCache = this.historyCache.filter(function (it) {
        return it.series && it.series.id !== seriesId
      })
    }
  }
})
