// Local UI fixture only. Never a production route; no real credentials or network API.
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ActionButton, HStack, Text } from '@seed-design/react'
import '@seed-design/css/base.css'
import 'pretendard/dist/web/variable/pretendardvariable.css'
import '../../src/app/global.css'
import { queryClient } from '../../src/app/providers/queryClient'
import { AuthenticatedProfilePage } from '../../src/pages/profile/AuthenticatedProfilePage'
import { advanceAuthContext } from '../../src/features/auth/model/authContext'
import { setAuthPhase } from '../../src/features/auth/model/authPhaseStore'
import { setSessionTokens } from '../../src/features/auth/model/tokenVault'
import { protectedApi } from '../../src/shared/api/protectedApi'
import { ApiClientError } from '../../src/shared/api/apiError'
import type { CharacterShop } from '../../src/features/character-shop/characterShop.types'
if (!import.meta.env.DEV) throw new Error('Development fixture only')

const catalog = [
  ['character-dragon','CHARACTER','꼬마 용',0,'dragon'], ['character-cat','CHARACTER','고양이',50,'cat'], ['character-bear','CHARACTER','곰',50,'bear'],
  ['room-day','ROOM','낮 공부방',0,'day'], ['room-night','ROOM','밤 공부방',30,'night'],
  ['hat-none','HAT','모자 없음',0,'none'], ['hat-beret','HAT','크림 베레모',20,'beret'], ['hat-beanie','HAT','파란 비니',20,'beanie'],
  ['top-none','TOP','기본 옷',0,'none'], ['top-sweater','TOP','크림 니트',30,'sweater'],
].map(([id,category,name,price,assetKey]) => ({id,category,name,price,assetKey})) as CharacterShop['catalog']
const initial = (): CharacterShop => ({ balance: 100, catalog, ownedItemIds: catalog.filter(i=>i.price===0).map(i=>i.id), equipment: {characterId:'character-dragon',roomId:'room-day',hatId:'hat-none',topId:'top-none'} })
let account = 1
const accounts = new Map([[1, initial()], [2, {...initial(), balance: 0}]])
let failRead = false
let failWrite = false
const user = () => ({id:account,email:'fixture@example.invalid',nickname:account===1?'테스트사용자':'다른계정',emailVerified:true,status:'ACTIVE'})
function activate() {
  advanceAuthContext(account)
  setSessionTokens({ accessToken: 'local-ui-fixture', accessExpiresAt:'2099-01-01T00:00:00Z', refreshExpiresAt:'2099-01-01T00:00:00Z' })
  queryClient.removeQueries({ queryKey: ['private'] })
  queryClient.setQueryData(['auth','me'],user())
  setAuthPhase('authenticated')
}
activate()
protectedApi.defaults.adapter = async (config) => {
  const currentAccount = account
  await new Promise(resolve=>setTimeout(resolve,250))
  const state = accounts.get(currentAccount)!
  const failure = (code: string, message: string) => { throw new ApiClientError({kind:'api',status:409,code,message}) }
  let data: unknown = state
  if (config.url === '/api/v1/users/me') data = user()
  else if (config.method === 'get' && failRead) throw new ApiClientError({kind:'api',status:503,code:'TEST',message:'테스트 조회 오류'})
  else if (config.method === 'post') {
    if (failWrite) { failWrite=false; failure('TEST','구매하지 못했어요. 다시 시도해 주세요.') }
    const item = catalog.find(item=>item.id===JSON.parse(config.data).itemId)!
    if (!state.ownedItemIds.includes(item.id)) {
      if (state.balance < item.price) failure('CHARACTER_INSUFFICIENT_COINS','코인이 부족해요.')
      state.balance-=item.price; state.ownedItemIds.push(item.id)
    }
  } else if (config.method === 'put') state.equipment=JSON.parse(config.data)
  return {data:{success:true,data:structuredClone(data),error:null},status:200,statusText:'OK',headers:{},config}
}
function Fixture() {
  return <QueryClientProvider client={queryClient}><MemoryRouter initialEntries={[new URLSearchParams(window.location.search).get('path') ?? '/profile']}>
    <HStack p="x2" gap="x2" style={{flexWrap:'wrap'}}>
      <Text textStyle="t3Bold">모의 API · 실제 코인 아님</Text>
      <ActionButton size="small" variant="neutralWeak" onClick={()=>{account=account===1?2:1;activate()}}>계정 전환</ActionButton>
      <ActionButton size="small" variant="neutralWeak" onClick={()=>{failRead=!failRead;queryClient.resetQueries({queryKey:['private','character-shop']})}}>조회 오류 전환</ActionButton>
      <ActionButton size="small" variant="neutralWeak" onClick={()=>{failWrite=true}}>다음 구매 실패</ActionButton>
    </HStack>
    <AuthenticatedProfilePage />
  </MemoryRouter></QueryClientProvider>
}
const root = createRoot(document.getElementById('root')!)
root.render(<Fixture />)
import.meta.hot?.dispose(() => root.unmount())
