import {expect,it} from 'vitest';
import {PendingServerWriteJournal,ServerCallError} from '../../../../dispatch-core/src/main/typescript/server-contract';
function memory(){const map=new Map<string,string>();return {getItem:(k:string)=>map.get(k)??null,setItem:(k:string,v:string)=>{map.set(k,v);},removeItem:(k:string)=>{map.delete(k);}};}
it('写结果未知时禁止换编号，离开页面后保留原编号，重试不创建新操作',()=>{
 const storage=memory(),journal=new PendingServerWriteJournal('admin',storage);
 const first=journal.begin('/upload',{name:'模拟包',fileName:'mock.zip',sizeBytes:20});
 journal.failed(new Error('响应丢失'));
 expect(()=>journal.begin('/upload',{name:'模拟包',fileName:'mock.zip',sizeBytes:20})).toThrow('未决');
 const reopened=new PendingServerWriteJournal('admin',storage);expect(reopened.pending).toEqual(first);
 expect(reopened.pending?.body.operationId).toBe(first.body.operationId);reopened.completed();expect(new PendingServerWriteJournal('admin',storage).pending).toBeNull();
});
it('确定冲突后可只读刷新与重新操作，不重放旧revision；未知连接结果仍保留',()=>{
 const journal=new PendingServerWriteJournal('admin',memory());journal.begin('/assign',{packId:'pack',revision:2,targetType:'group',targetId:'default'});
 journal.failed(new ServerCallError('CONFLICT','请刷新','request-test'));expect(journal.pending).toBeNull();
 const next=journal.begin('/assign',{packId:'pack',revision:3,targetType:'group',targetId:'default'});expect(next.body.revision).toBe(3);
 journal.failed(new ServerCallError('CONNECT_FAILED','连接不上','request-test'));expect(journal.pending).toEqual(next);
});
