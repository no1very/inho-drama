const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('inho', {
  getServerUrl: () => ipcRenderer.invoke('get-server-url'),
  connect: (url) => ipcRenderer.invoke('connect-server', url)
})
