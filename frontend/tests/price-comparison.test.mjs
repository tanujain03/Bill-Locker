import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
async function compiled(name,imports={}) {
 let {outputText}=ts.transpileModule(await readFile(new URL(`../src/lib/${name}.ts`,import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}});
 for(const [path,url] of Object.entries(imports))outputText=outputText.replace(`from '${path}'`,`from '${url}'`);
 return `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`;
}
const {comparePrices,getPriceSearchStatus,getPriceStoreLink,offerRetailer,isGoogleListing}=await import(await compiled('price-comparison',{'./api':await compiled('api')}));
globalThis.localStorage={getItem:()=> 'token'};globalThis.sessionStorage={getItem:()=>null};
test('search is an explicit authenticated POST and contains no provider key',async t=>{
 let request;t.mock.method(globalThis,'fetch',async(path,options)=>{request={path,options};return new Response('{"offers":[]}');});
 await comparePrices('Casio A168WA-1','A168WA-1');
 assert.equal(request.path,'/api/price-comparison/search');assert.equal(request.options.method,'POST');
 assert.equal(request.options.headers.Authorization,'Bearer token');assert.deepEqual(JSON.parse(request.options.body),{query:'Casio A168WA-1',model:'A168WA-1'});
});
test('status is a GET and quota errors retain their message',async t=>{
 t.mock.method(globalThis,'fetch',async(path,options)=>{assert.equal(path,'/api/price-comparison/status');assert.equal(options.method,'GET');return new Response('{"remaining":0}');});
 assert.equal((await getPriceSearchStatus()).remaining,0);
 t.mock.method(globalThis,'fetch',async()=>new Response('{"code":"PRICE_SEARCH_LIMIT","message":"Free limit reached"}',{status:429}));
 await assert.rejects(comparePrices('watch',''),e=>e.code==='PRICE_SEARCH_LIMIT'&&e.message==='Free limit reached');
});
test('retailer matching distinguishes Google seller labels from direct merchant domains',()=>{
 assert.equal(offerRetailer({url:'https://www.amazon.in/dp/1',seller:'Some seller'}),'Amazon');
 assert.equal(offerRetailer({url:'https://www.google.com/shopping/product/1',seller:'Myntra'}),'Myntra');
 assert.equal(offerRetailer({url:'https://www.google.com/shopping/product/1',seller:'amazon.in.evil.example'}),'Other');
 assert.equal(isGoogleListing('https://www.google.com/shopping/product/1'),true);
 assert.equal(isGoogleListing('https://www.flipkart.com/watch'),false);
});

test('store lookup sends only the selected cached search and offer identity',async t=>{
 let sent;t.mock.method(globalThis,'fetch',async(path,options)=>{sent={path,body:JSON.parse(options.body)};return new Response('{"url":"https://amazon.in/dp/123"}');});
 const search={query:'Casio',model:'A168WA-1'};
 await getPriceStoreLink(search,{url:'https://www.google.com/shopping/product/1',seller:'Amazon.in',lookupToken:'not-sent'});
 assert.equal(sent.path,'/api/price-comparison/store-link');assert.deepEqual(sent.body,{search,url:'https://www.google.com/shopping/product/1',seller:'Amazon.in'});
});
