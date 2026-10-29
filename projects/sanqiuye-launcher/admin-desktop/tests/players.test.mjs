import test from 'node:test';
import assert from 'node:assert/strict';
import {dispatchOverview,filterPlayers,playerValue,playerStatusLabel} from '../src/workbench/players-model.ts';
const unknown={gameVersion:null,packVersion:null,playTimeSeconds:null,lastSeen:null};
const rows=[{id:'a',username:'Alex',groupId:'builders',status:'ACTIVE',...unknown},{id:'b',username:'Steve',groupId:'default',status:'DISABLED',...unknown},{id:'c',username:'ALEXANDER',groupId:null,status:'UNKNOWN',...unknown}];
test('player filters combine name, group and status without changing source data',()=>{
 const before=structuredClone(rows);
 assert.deepEqual(filterPlayers(rows,{query:' alex ',groupId:'builders',status:'ACTIVE'}).map(p=>p.id),['a']);
 assert.deepEqual(filterPlayers(rows,{query:'A',groupId:'',status:'UNKNOWN'}).map(p=>p.id),['c']);
 assert.deepEqual(filterPlayers(rows,{query:'b',groupId:'',status:'ALL'}).map(p=>p.id),['b']);
 assert.deepEqual(rows,before);
});
test('pending dispatch has no fabricated default group, player, policy or statistics',()=>{
 assert.deepEqual(dispatchOverview(),{state:'PENDING',players:[],groups:[],policies:[]});
 assert.equal(playerValue(null),'—');assert.equal(playerValue(0),'0');
 assert.equal(playerStatusLabel('UNKNOWN'),'未知');
 assert.equal(playerValue(rows[0].playTimeSeconds),'—');
});
