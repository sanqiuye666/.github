# 三秋叶启动器

Minecraft启动器与独立管理面板，使用官方Tauri、Vue、TypeScript与Java 21。

当前包含游戏版本与加载器、实例模组、本机资源管理、游戏设置、皮肤/彩字工具、诊断、自定义开屏、真实面板账号HTTP服务及独立管理入口。完整整合包、联机、部分创作工具等仍在开发；账号服务需要自行配置，不附带任何运营服务器地址。

## 构建

需要Java 21、Node.js 24、Python 3与Rust；Windows原生构建另需MSVC和WebView2。在本目录运行：

```sh
npm --prefix player-ui ci
npm --prefix admin-desktop ci
npm --prefix player-ui run build
npm --prefix admin-desktop run build:web
npm --prefix admin-desktop run build
./mvnw -B -ntp verify
```

Windows使用`mvnw.cmd`。前端必须按上述顺序构建，构建身份会阻止混用旧资源。原生EXE在相应前端完成后构建：

```sh
cargo build --release --locked --features custom-protocol --manifest-path player-ui/src-tauri/Cargo.toml
cargo build --release --locked --features custom-protocol --manifest-path admin-desktop/src-tauri/Cargo.toml
```

测试入口：两端`npm test`、`mvnw verify`、两端`cargo test --locked`、`node --test packaging/tests/*.test.mjs`。Windows GUI测试需要另传专用目录与Playwright路径，不应指向已有玩家数据。

## 配置与许可

只提供`.env.example`与`packaging/cloud/*.env.example`。账号API使用外置路径、HTTPS或显式回环联调；密钥仅从配置文件指向的私有文件读取。不要把真实配置、口令、账号库或密钥提交到仓库。

本目录是脱敏项目源码快照，不含本机开发历史、私人需求文档、测试运行数据和成品。详见[公开快照说明](PUBLICATION.md)。

基于[DreamingFish-Updater](https://github.com/hanhanyu666/DreamingFish-Updater)开发，保留上游署名与[AGPL-3.0许可证](LICENSE)。第三方许可见[docs/third-party](docs/third-party)，源码原有作者/协议/包名保持。各发行包应随附相应源码与许可。
