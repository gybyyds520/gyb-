// pages/index/index.js
Page({
  data: {
    // H5 移动端地址：
    // - 本地开发：本机 Web 服务（开发者工具勾选「不校验合法域名」）
    // - 真机预览：改为电脑局域网 IP，例如 http://192.168.1.100:5173
    // - 上线发布：替换为已备案的 HTTPS 业务域名（并在小程序后台配置）
    h5Url: 'http://localhost:5173'
  },
  onMessage(e) {
    // 接收 H5 页面通过 wx.miniProgram.postMessage 回传的消息（可选）
  }
})
