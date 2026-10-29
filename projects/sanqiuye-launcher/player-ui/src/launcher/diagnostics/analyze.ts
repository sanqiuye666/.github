import type {LaunchSnapshot} from '../launch-types';
import {SAMPLE_CHARACTERS,type LogSample} from './sample';
export interface DiagnosisFinding {code:string;title:string;confidence:'recognized'|'possible';reason:string;steps:string[]}
export interface LaunchDiagnosisReport {runId:string;status:'normal'|'cancelled'|'pending'|'failure';title:string;exitCode:number|null;findings:DiagnosisFinding[];partial:boolean;readError:boolean;sampledLines:number}
const finding=(code:string,title:string,reason:string,steps:string[],confidence:DiagnosisFinding['confidence']='possible'):DiagnosisFinding=>({code,title,reason,steps,confidence});
const backend:Record<string,[string,string]>= {
 JAVA_NOT_FOUND:['缺少匹配的Java','到“设置 → Java与启动设置”检测或选择符合游戏版本的Java。'],
 JAVA_INCOMPATIBLE:['Java不兼容','核对Java版本和位数，再重新执行启动检查。'],
 MEMORY_INSUFFICIENT:['内存设置超过可用范围','关闭不需要的程序，或降低此实例的最大内存后重新检查。'],
 FILE_CHANGED:['游戏文件已变化','在实例中校验文件，完成后重新启动检查。'],
 INSTANCE_BUSY:['实例正被占用','等待安装或游戏正常结束，不要删除锁文件。'],
 OPTIONS_CHANGED:['默认设置已变化','保留新设置，重新执行启动检查。'],
 SETTINGS_CHANGED:['启动设置已变化','重新检查Java和启动参数后，再执行启动检查。']
};
const rules:[RegExp,DiagnosisFinding][]=[
 [/\b(?:java\.lang\.)?OutOfMemoryError\b/i,finding('MEMORY','日志含内存异常','取样中出现JVM内存异常标记，尚不能确定唯一根因。',['核对该实例内存上限和当前可用内存。','先关闭其他程序，适当降低视距或资源包负担；不要无限增大内存。'])],
 [/\bUnsupportedClassVersionError\b|class file version \d+.*only recognizes/is,finding('JAVA_VERSION','日志含Java版本不匹配','日志出现字节码版本不兼容标记。',['按实际游戏、加载器和模组要求选择Java。','修改后重新执行启动检查。'])],
 [/\b(?:NoClassDefFoundError|ClassNotFoundException)\b/i,finding('MISSING_CLASS','日志含缺失类','可能缺少依赖或版本不匹配，不能仅凭类名确定应删除哪个模组。',['核对加载器、模组及前置组件的游戏版本。','保留原文件，先在副本中验证依赖，不要批量删模组。'])],
 [/Incompatible mods found!|Missing or unsupported mandatory dependencies/i,finding('MOD_DEPENDENCY','日志含模组兼容提示','取样出现加载器的模组兼容或必要依赖提示。',['查看完整日志列出的版本与前置要求。','在备份副本中逐项核对，当前启动器不会自动删除或替换模组。'])],
 [/GLFW error 65542|WGL:.*(?:OpenGL|driver)|Failed to create OpenGL context/i,finding('OPENGL','日志含图形环境异常','出现OpenGL/GLFW初始化标记，具体驱动或硬件原因仍需核对。',['使用显卡厂商的正式驱动并核对系统支持。','先关闭额外图形叠加工具，在独立测试实例中复测。'])],
 [/\bAccessDeniedException\b|java\.io\.FileNotFoundException:.*(?:Access is denied|拒绝访问)/i,finding('FILE_ACCESS','日志含文件访问受阻','取样提示文件权限或占用问题，不能因此认定文件损坏。',['检查目录写入权限、磁盘空间和文件占用。','保留存档和原文件，不要关闭安全防护或以删除目录代替排查。'])]
];
export function analyzeLaunch(run:LaunchSnapshot,sample:LogSample):LaunchDiagnosisReport{
 const report:LaunchDiagnosisReport={runId:run.id,status:'failure',title:'启动问题诊断',exitCode:run.exitCode,findings:[],partial:sample.partial,readError:sample.readError,sampledLines:Math.min(400,sample.lines.length)};
 if(run.state==='CANCELLED'){report.status='cancelled';report.title='本次操作已取消';return report;}
 if(!['EXITED','FAILED'].includes(run.state)){report.status='pending';report.title='本次运行尚无确定退出结果';return report;}
 if(run.state==='EXITED'&&run.exitCode===0){report.status='normal';report.title='本次正常退出';return report;}
 if(run.exitCode===-1073741819||run.exitCode===3221225477)report.findings.push(finding('NATIVE_ACCESS_VIOLATION','Windows原生访问冲突','退出码为0xC0000005，尚不能确定是驱动、原生库、Java、路径或其他原因。',['保留本次日志及可能生成的hs_err_pid崩溃报告。','核对Java位数和组件兼容性；在独立测试副本中复测并比较差异。'],'recognized'));
 const known=run.errorCode?backend[run.errorCode]:undefined;
 if(known)report.findings.push(finding(run.errorCode!,known[0],'启动核心返回了明确的检查原因。',[known[1]],'recognized'));
 let characters=0;const lines:string[]=[];
 for(const line of sample.lines.slice(0,400)){const remaining=SAMPLE_CHARACTERS-characters;lines.push(line.slice(0,remaining));characters+=Math.min(line.length,remaining);if(line.length>remaining){report.partial=true;break;}}
 if(sample.lines.length>400)report.partial=true;
 const text=lines.join('\n');for(const [pattern,result] of rules)if(pattern.test(text))report.findings.push({...result,steps:[...result.steps]});
 if(!report.findings.length)report.findings.push(finding('UNKNOWN_EXIT','尚未识别具体原因','当前退出结果和日志片段不足以确定原因，不代表已经修复。',['查看该次完整日志和崩溃报告，记录游戏、Java及组件版本。','保留原实例与存档；先在副本中复测，不执行一键清理。']));
 return report;
}
export function diagnosisText(report:LaunchDiagnosisReport,version:string):string{
 return ['三秋叶运行诊断',`游戏版本：${version}`,`运行编号：${report.runId}`,`结果：${report.title}`,`退出码：${report.exitCode??'未取得'}`,'取样范围：当前保留日志前部，最多400行/128Ki字符，不含完整崩溃报告。',`日志：有界取样${report.sampledLines}行${report.partial?'，证据不全':''}${report.readError?'，读取失败':''}`,...report.findings.flatMap(f=>[`${f.title} [${f.code}]`,f.reason,...f.steps.map(s=>'· '+s)]),'此报告不含日志原文，不会自动修改文件。'].join('\n');
}
