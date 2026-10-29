# DEPLOYMENT

开发构建见README。Windows原生便携包应包含完整Java运行时、后端JAR、bootstrap、资源、构建身份与许可证；不要单独发送EXE。packaging/build-native-player.ps1及build-native-admin.ps1必须显式传新输出目录，限定在项目target/portable下。旧版jpackage管理分发仍由build-distributions.ps1提供，不能当作已完成的新Tauri双平台发行。Linux脚本及账号服务部署须在目标环境另外验证。升级前备份运行数据与当前成品；实际服务器地址和认证只放仓库外私有配置，公网账号请求使用HTTPS。

配置模板和构建步骤见项目README，第三方许可见third-party目录。
