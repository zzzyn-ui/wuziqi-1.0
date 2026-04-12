import { createPinia } from 'pinia'

const pinia = createPinia()

export default pinia

// 导出所有 store
export * from './modules/user'
export * from './modules/game'
export * from './modules/room'
