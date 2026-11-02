const { app, BrowserWindow, ipcMain, Menu, shell } = require('electron')
const path = require('path')
const fs = require('fs')

const CONFIG_FILE = path.join(app.getPath('userData'), 'config.json')

function loadConfig() {
  try {
    return JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf8'))
  } catch (e) {
    return { serverUrl: '' }
  }
}

function saveConfig(cfg) {
  try {
    fs.writeFileSync(CONFIG_FILE, JSON.stringify(cfg, null, 2))
  } catch (e) {
    console.error('save config failed', e)
  }
}

const configPage = path.join(__dirname, 'config.html')

function createWindow(url) {
  const win = new BrowserWindow({
    width: 1280,
    height: 840,
    minWidth: 900,
    minHeight: 600,
    title: 'InhoDrama',
    autoHideMenuBar: process.platform === 'darwin',
    backgroundColor: '#0f1115',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })

  if (url) {
    win.loadURL(url)
  } else {
    win.loadFile(configPage)
  }

  // 外部链接用系统浏览器打开
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//i.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })

  // 若远程页标题变化，跟随其标题
  win.webContents.on('page-title-updated', (_e, title) => {
    if (title && title.trim()) win.setTitle('InhoDrama - ' + title)
  })

  return win
}

function buildMenu(win) {
  const template = [
    {
      label: '文件',
      submenu: [
        {
          label: '设置服务器地址…',
          click: () => { win.loadFile(configPage) }
        },
        { type: 'separator' },
        process.platform === 'darwin'
          ? { role: 'quit', label: '退出 InhoDrama' }
          : { role: 'quit', label: '退出' }
      ]
    }
  ]
  if (process.platform === 'darwin') {
    template.unshift({
      label: app.name,
      submenu: [
        { role: 'about', label: '关于 InhoDrama' },
        { type: 'separator' },
        { label: '设置服务器地址…', click: () => { win.loadFile(configPage) } },
        { type: 'separator' },
        { role: 'quit', label: '退出 InhoDrama' }
      ]
    })
  }
  Menu.setApplicationMenu(Menu.buildFromTemplate(template))
}

app.whenReady().then(() => {
  ipcMain.handle('get-server-url', () => loadConfig().serverUrl || '')
  ipcMain.handle('connect-server', (_e, url) => {
    const clean = String(url || '').trim().replace(/\/+$/, '')
    if (!/^https?:\/\//i.test(clean)) return { ok: false, error: '地址需以 http:// 或 https:// 开头' }
    const cfg = loadConfig()
    cfg.serverUrl = clean
    saveConfig(cfg)
    const all = BrowserWindow.getAllWindows()
    const win = all.length ? all[0] : createWindow(clean)
    win.loadURL(clean)
    return { ok: true, url: clean }
  })

  const cfg = loadConfig()
  const win = createWindow(cfg.serverUrl)
  buildMenu(win)

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow(loadConfig().serverUrl)
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
