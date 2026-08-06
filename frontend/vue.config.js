const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  lintOnSave: false,
  // 代理后端 API,避免开发环境跨域
  devServer: {
    port: 8081,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  // 生产构建: 将静态资源放到 ../src/main/resources/static 下,Spring Boot 可直接托管
  // 若前后端分离部署,改为普通目录即可
  outputDir: 'dist',
  assetsDir: 'static',
  productionSourceMap: false
})
