import type {LauncherPage} from '../model';
export type GuideStep='identity'|'java'|'version'|'installation'|'check'|'launch';
export const GUIDE_STEPS:readonly {id:GuideStep;title:string;description:string;page:LauncherPage}[]=[
 {id:'identity',title:'选择游戏身份',description:'在账号页添加并切换临时离线身份。持久账号、微软与第三方认证按后续批次接入。',page:'accounts'},
 {id:'java',title:'准备 Java',description:'在设置中检测本机 Java。按版本要求选择，不同实例可单独设置内存与窗口。',page:'settings'},
 {id:'version',title:'选择游戏版本',description:'版本中心读取官方 Java 版目录；创建实例时会隔离游戏目录、存档与配置。',page:'versions'},
 {id:'installation',title:'安装游戏文件',description:'进入实例页，确认版本后安装文件。任务区显示真实进度，遇到错误可查看原因。',page:'instances'},
 {id:'check',title:'检查启动条件',description:'选择已安装实例，打开启动面板并检查。缺失 Java、身份或文件时会给出原因。',page:'instances'},
 {id:'launch',title:'由你确认启动',description:'检查通过后自行点击启动。日志和运行状态在实例页；看完指引不会自动启动游戏。',page:'instances'}
];
