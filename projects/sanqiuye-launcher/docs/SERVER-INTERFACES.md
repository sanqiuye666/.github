# SERVER-INTERFACES

dispatch-core/server/ports按账号、会话、资料、整合包、运行控制等能力划分，ServerConfig统一解析外置配置，ServerPortsFactory唯一切换mock/real。账号真实接口为account/register、account/login、account/me、account/logout、admin/accounts，前缀/api/v1/；其余真实能力按实际实现状态返回。每次调用带请求ID和有界预算，区分连接、鉴权、服务端与格式错误。

配置模板和构建步骤见项目README，第三方许可见third-party目录。
