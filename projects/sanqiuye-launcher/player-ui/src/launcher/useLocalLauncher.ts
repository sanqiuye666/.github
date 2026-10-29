import {computed,onBeforeUnmount,onMounted,ref,shallowRef} from 'vue';
import {currentEntryContext} from './entry';
import {discoverLocalSession,LocalLauncherClient,operationMessage} from './local-api';
import type {InstanceListing,LocalSession} from './local-types';

export function useLocalLauncher(invalid:()=>void){
  const client=shallowRef<LocalLauncherClient|null>(null),session=shallowRef<LocalSession|null>(null);
  const connecting=ref(false),loading=ref(false),error=ref('');
  const listing=ref<InstanceListing>({instances:[],problems:[]});
  let lifetime=new AbortController(),sequence=0;
  async function discover(){
    connecting.value=true;error.value='';
    try{
      const value=await discoverLocalSession(currentEntryContext(),lifetime.signal);
      if(!lifetime.signal.aborted&&value){session.value=value;client.value=new LocalLauncherClient(value.sessionToken,()=>{disconnect();invalid();});}
    }catch(reason){if(!lifetime.signal.aborted)error.value=operationMessage(reason);}
    finally{connecting.value=false;}
  }
  async function refreshInstances(){
    const active=client.value;if(!active)return;
    const current=++sequence;loading.value=true;error.value='';
    try{const result=await active.instances(lifetime.signal);if(current===sequence)listing.value=result;}
    catch(reason){if(current===sequence)error.value=operationMessage(reason);}
    finally{if(current===sequence)loading.value=false;}
  }
  function disconnect(){++sequence;lifetime.abort();client.value?.close();client.value=null;session.value=null;listing.value={instances:[],problems:[]};loading.value=false;}
  function reconnect(){disconnect();lifetime=new AbortController();void discover();}
  onMounted(discover);onBeforeUnmount(disconnect);
  return {client,session,connecting,loading,error,listing,available:computed(()=>client.value!==null),refreshInstances,disconnect,reconnect};
}
