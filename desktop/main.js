/**
 * 学情分析系统 · 桌面端主进程
 * 加载 Web 构建产物（desktop/dist），提供桌面窗口与系统托盘。
 * API 地址在构建 Web 时通过 VITE_API_BASE 注入（默认 http://127.0.0.1:8080/api）。
 */
const { app, BrowserWindow, Menu, Tray, shell } = require('electron')
const path = require('path')
const fs = require('fs')

const INDEX = path.join(__dirname, 'dist', 'index.html')

let mainWindow = null
let tray = null

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1360,
    height: 860,
    minWidth: 900,
    minHeight: 600,
    title: '学情分析系统',
    autoHideMenuBar: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  // 构建产物不存在时给出提示
  if (!fs.existsSync(INDEX)) {
    mainWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(
      '<h3 style="font-family:sans-serif;text-align:center;margin-top:80px">未找到前端构建产物（desktop/dist）。<br/>请先在 web 目录执行 npm run build 后再启动桌面端。</h3>'
    )}`)
  } else {
    mainWindow.loadFile(INDEX)
  }

  // 外部链接交给系统浏览器（保持应用内为纯本地界面）
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })

  mainWindow.on('closed', () => { mainWindow = null })
}

// 系统托盘：可最小化到托盘，方便常驻
function createTray() {
  try {
    tray = new Tray(path.join(__dirname, 'dist', 'favicon.ico'))
  } catch {
    tray = null
    return
  }
  tray.setToolTip('学情分析系统')
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: '打开主界面', click: () => { if (!mainWindow) createWindow(); mainWindow.show() } },
    { label: '退出', click: () => app.quit() }
  ]))
  tray.on('click', () => {
    if (!mainWindow) createWindow()
    mainWindow.show()
  })
}

app.whenReady().then(() => {
  createWindow()
  createTray()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  // 保留托盘运行，用户可通过托盘退出
})
