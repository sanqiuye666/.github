import {expect,it} from 'vitest';
import {unwrapServerResult} from '../../../../dispatch-core/src/main/typescript/server-contract';
it('四类远端错误分别显示并保留请求编号，格式错误不能当成功',()=>{
 for(const code of ['CONNECT_FAILED','AUTH_FAILED','SERVER_ERROR','INVALID_DATA'])expect(()=>unwrapServerResult({schemaVersion:1,requestId:'request-test',source:'MOCK',attempts:1,durationMs:0,error:{code,message:'测试失败'}})).toThrow(code);
 expect(()=>unwrapServerResult({ok:true})).toThrow('INVALID_DATA');
 expect(unwrapServerResult({schemaVersion:1,requestId:'request-test',source:'MOCK',attempts:1,durationMs:0,data:{ok:true}})).toEqual({ok:true});
});
