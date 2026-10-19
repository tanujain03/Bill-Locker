import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
async function compiled(name, imports={}) {
 const source=await readFile(new URL(`../src/lib/${name}.ts`,import.meta.url),'utf8');
 let {outputText}=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}});
 for(const [path,url] of Object.entries(imports))outputText=outputText.replace(`from '${path}'`,`from '${url}'`);
 return `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`;
}
const {splitApi,equalShares,inviteUrl}=await import(await compiled('bill-splits',{'./api':await compiled('api')}));
globalThis.localStorage={getItem:()=> 'test-jwt'};globalThis.sessionStorage={getItem:()=>null};
globalThis.window={location:{origin:'https://locker.example'}};
const a='00000000-0000-0000-0000-000000000001',b='00000000-0000-0000-0000-000000000002',c='00000000-0000-0000-0000-000000000003';
test('equal shares preserve all paise and are independent of selection order',()=>{
 assert.deepEqual(equalShares(100,[c,b,a]),[{memberId:a,amount:33.34},{memberId:b,amount:33.33},{memberId:c,amount:33.33}]);
 assert.deepEqual(equalShares(0.01,[b,a]),[{memberId:a,amount:0.01},{memberId:b,amount:0}]);
 assert.deepEqual(equalShares(0,[]),[]);
});
test('rounding preview uses the same signed UUID ordering as Java',()=>{
 const negative='ffffffff-ffff-ffff-ffff-ffffffffffff';
 assert.deepEqual(equalShares(0.01,[a,negative]),[{memberId:negative,amount:0.01},{memberId:a,amount:0}]);
});
test('invite secrets stay in URL fragments and in authenticated POST bodies',async t=>{
 const link=new URL(inviteUrl('secret&other=value'));assert.equal(link.search,'');assert.equal(new URLSearchParams(link.hash.slice(1)).get('token'),'secret&other=value');
 const calls=[];t.mock.method(globalThis,'fetch',async(path,options)=>{calls.push({path,options});return new Response('{}');});
 await splitApi.preview('secret');await splitApi.accept('secret');
 assert.deepEqual(calls.map(c=>c.path),['/api/splits/invitations/preview','/api/splits/invitations/accept']);
 for(const c of calls){assert.equal(c.options.method,'POST');assert.equal(c.options.headers.Authorization,'Bearer test-jwt');assert.deepEqual(JSON.parse(c.options.body),{token:'secret'});}
});
test('expense and settlement requests preserve retry identifiers through the shared API wrapper',async t=>{
 const calls=[];t.mock.method(globalThis,'fetch',async(path,options)=>{calls.push({path,options});return new Response('{}');});
 const expense={requestId:'expense-retry',title:'Lunch',amount:100,expenseDate:'2026-10-07',payerId:a,mode:'EQUAL',shares:[{memberId:a,amount:null},{memberId:b,amount:null}]};
 await splitApi.expense('group',expense);await splitApi.expense('group',expense);
 const payment={requestId:'payment-retry',fromMemberId:b,toMemberId:a,amount:50};await splitApi.payment('group',payment);
 assert.equal(calls[0].options.body,calls[1].options.body);assert.deepEqual(JSON.parse(calls[2].options.body),payment);
 assert.equal(calls[0].path,'/api/splits/groups/group/expenses');assert.equal(calls[2].path,'/api/splits/groups/group/settlements');
});
test('payment validation and expired invitations preserve useful API errors',async t=>{
 t.mock.method(globalThis,'fetch',async()=>new Response(JSON.stringify({success:false,code:'VALIDATION_ERROR',message:'Check balances',fieldErrors:{amount:'Amount exceeds outstanding balance'}}),{status:400}));
 await assert.rejects(splitApi.payment('group',{}),e=>e.fieldErrors.amount==='Amount exceeds outstanding balance');
 t.mock.method(globalThis,'fetch',async()=>new Response(JSON.stringify({success:false,code:'INVITATION_UNAVAILABLE',message:'Expired invitation'}),{status:410}));
 await assert.rejects(splitApi.accept('expired'),e=>e.status===410&&e.message==='Expired invitation');
});
test('invite revocation and payment decisions use their intended mutation methods',async t=>{
 const calls=[];t.mock.method(globalThis,'fetch',async(path,options)=>{calls.push({path,method:options.method});return new Response(null,{status:204});});
 await splitApi.revoke('group','member');await splitApi.decide('group','payment','confirm');await splitApi.decide('group','payment','reject');
 assert.deepEqual(calls,[{path:'/api/splits/groups/group/members/member/invitation',method:'DELETE'},{path:'/api/splits/groups/group/settlements/payment/confirm',method:'POST'},{path:'/api/splits/groups/group/settlements/payment/reject',method:'POST'}]);
});

test('deleting a group uses the authenticated DELETE endpoint',async t=>{
 let request;t.mock.method(globalThis,'fetch',async(path,options)=>{request={path,options};return new Response(null,{status:204});});
 await splitApi.delete('group-id');assert.equal(request.path,'/api/splits/groups/group-id');assert.equal(request.options.method,'DELETE');assert.equal(request.options.headers.Authorization,'Bearer test-jwt');
});
