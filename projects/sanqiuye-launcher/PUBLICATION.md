# 公开快照说明

生产源码、资源、构建配置和依赖锁文件保留原字节，未附任何本机Git对象或旧提交。公开说明单独整理，不包含私人需求PDF、聊天记录、验收截图、用户目录、真实服务器地址、运行数据库、密钥或实际连接配置。

为方便其他开发者，本机打包/验收脚本中的Python、PowerShell和JDK路径改用通用命令或JAVA_HOME；便携输出限定到本项目target/portable目录。生产代码未因此改动。源文件采用不自动转换换行的Git属性，避免破坏共同构建身份。

Windows打包在正式EXE和Java产物构建后，调用packaging/build-native-player.ps1或build-native-admin.ps1，指定NativeExecutable及本项目target/portable下的新OutputDirectory。服务器服务是独立cloud命令，需私有配置；默认不携带可用运营连接。

本快照不承诺所有规划功能已完成；实际能力以代码、公开使用说明及自测结果为准。许可证与第三方署名必须保留。
