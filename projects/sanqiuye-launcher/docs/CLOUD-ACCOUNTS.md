# CLOUD-ACCOUNTS

使用packaging/cloud中的占位配置。服务命令为java -jar dfs-admin.jar cloud --config <绝对配置路径>；首次可显式加--initialize-secrets生成不覆盖的私有凭据。服务只绑定回环，公网入口自行配置HTTPS。独立SQLite保存Argon2id口令派生结果，随机会话只存HMAC摘要；公开注册不能选择管理员或任意组。不要公开真实配置或管理员token。

配置模板和构建步骤见项目README，第三方许可见third-party目录。
