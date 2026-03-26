import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import './style.css'

// LingNow.cc - AI Powered Code Generator
// https://lingnow.cc

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.mount('#app')
