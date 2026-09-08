const test = require('node:test')
const assert = require('node:assert/strict')
const fixture = require('./helpers/loadTs.cjs')
const uuid = (n) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`
const settle = async () => { for (let i = 0; i < 8; i++) await new Promise(r => setImmediate(r)) }
function setup(options = {}) {
  global.window = { addEventListener() {}, removeEventListener() {}, setInterval() { return 1 }, clearInterval() {} }
  global.document = { addEventListener() {}, removeEventListener() {} }
  const sent = [], paths = [], writes = [], opens = new Map(), reads = new Map()
  const notification = { notificationId: uuid(2), quizSetId: uuid(3), materialId: '123', actionType: 'FOCUS_QUIZ_IN_LIST', targetAvailable: true, createdAt: new Date().toISOString(), ...options.notification }
  const {load} = fixture({
    '@/shared/api/protectedApi': { protectedApi: {
      get: async (path) => { if (options.get) return options.get(path, notification); return {data: {data: path.includes('/notifications/') ? notification : {id:uuid(3)}}} },
      put: async (...args) => { writes.push(args); if (options.readFailure) throw {status:503}; return {} },
    } },
    '@/shared/api/apiError': {unwrapApiResponse: r => r.data, toApiClientError: e => e},
    '@/app/providers/queryClient': {queryClient: {invalidateQueries() {}}},
  })
  const context = load('features/auth/model/authContext.ts'), phase = load('features/auth/model/authPhaseStore.ts')
  context.advanceAuthContext(42); phase.setAuthPhase('authenticated')
  const storage = {get: async k => opens.get(k), complete: async (r, read) => { if(options.storageFailure) throw Error('disk'); opens.set(r.key,r); if(read) reads.set(read.key,read) }, reads: async u => [...reads.values()].filter(r=>r.userId===u), update: async(r,remove) => {if(remove) reads.delete(r.key); else reads.set(r.key,r)} }
  const session = load('shared/native/pushOpenSession.ts').createPushOpenSession({sessionId:uuid(1),send:(...x)=>sent.push(x)}, async p=>paths.push(p),storage)
  const raw = () => JSON.stringify({version:1,type:'PUSH_OPEN',messageId:uuid(5),bridgeSessionId:uuid(1),authEpoch:context.getAuthContext().authEpoch,payload:{notificationId:uuid(2),bindingId:uuid(6)}})
  return {session,raw,sent,paths,writes,opens,reads,context,phase}
}
test('push tap confirms target, persists completion/read intent before ACK and deduplicates replay', async()=>{
  const h=setup({readFailure:true}); try {h.session.receive(h.raw()); await settle(); assert.equal(h.paths.length,1); assert.match(h.paths[0],/focus=/); assert.equal(h.opens.size,1); assert.equal(h.reads.size,1); assert.equal(h.sent[0][0],'PUSH_OPEN_ACK'); h.session.receive(h.raw());await settle();assert.equal(h.paths.length,1);assert.equal(h.sent.length,2)} finally {h.session.stop()}
})
test('storage failure never ACKs and notification 404 recovers without a read intent',async()=>{
  const h=setup({storageFailure:true});try {h.session.receive(h.raw());await settle();assert.equal(h.sent.length,0);h.session.receive(h.raw());await settle();assert.equal(h.paths.length,1)}finally{h.session.stop()}
  const j=setup({get:async()=>{throw {status:404}}});try{j.session.receive(j.raw());await settle();assert.equal(j.paths[0],'/notifications?unavailable=notification');assert.equal(j.sent[0][1].outcome,'UNAVAILABLE');assert.equal(j.reads.size,0)}finally{j.session.stop()}
})
test('account switch during notification lookup cannot navigate, mark read, or ACK',async()=>{
  let resolve; const h=setup({get:()=>new Promise(r=>{resolve=r})});try{h.session.receive(h.raw());await settle();h.context.advanceAuthContext(99);resolve({data:{data:{}}});await settle();assert.equal(h.paths.length,0);assert.equal(h.writes.length,0);assert.equal(h.sent.length,0)}finally{h.session.stop()}
})
test('stale epoch and anonymous selection cannot access private results',async()=>{
 const h=setup();try{const old=h.raw();h.context.advanceAuthContext(99);h.session.receive(old);await settle();assert.equal(h.sent.length,0);assert.equal(h.paths.length,0);h.phase.setAuthPhase('anonymous');h.session.receive(h.raw());await settle();assert.deepEqual(h.paths,['/login']);assert.equal(h.sent.length,0)}finally{h.session.stop()}
})

test('failed generation uses numeric material route and missing target recovers to inbox', async()=>{
 const h=setup({notification:{actionType:'RECONFIGURE_QUIZ'}});try{h.session.receive(h.raw());await settle();assert.equal(h.paths[0],'/learning/123/quiz');assert.equal(h.sent[0][1].outcome,'COMPLETED')}finally{h.session.stop()}
 const j=setup({notification:{targetAvailable:false}});try{j.session.receive(j.raw());await settle();assert.equal(j.paths[0],'/notifications?unavailable=target');assert.equal(j.sent[0][1].outcome,'COMPLETED')}finally{j.session.stop()}
})

test('recovery notice follows each current query and distinguishes notification vs target',()=>{
 const {load}=fixture();const {notificationRecoveryNotice:notice}=load('features/notification/model/notificationPresentation.ts');
 assert.equal(notice(''),undefined);assert.equal(notice('?unavailable=notification'),'알림을 찾을 수 없어요.');assert.equal(notice('?unavailable=target'),'대상을 찾을 수 없어요.');assert.equal(notice(''),undefined)
})
