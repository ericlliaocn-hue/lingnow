<script setup>
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import axios from 'axios'
import {Globe, Loader2} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

const locale = ref(localStorage.getItem('lingnow_lang') || 'ZH')
const authData = ref({username: 'eric', password: 'anbs,23t'})
const loading = ref(false)
const error = ref('')

const i18n = computed(() => ({
  ZH: {
    title: '欢迎回到 LingNow',
    subtitle: '登录后继续你的项目工作流与原型生成。',
    username: '用户名',
    password: '密码',
    action: '登录',
    loading: '登录中...',
    switch_lang: 'ZH/EN',
    invalid: '用户名或密码无效',
    unavailable: '登录服务不可用，请确认前后端都已启动。',
    unexpected: '登录失败，请稍后重试。'
  },
  EN: {
    title: 'Welcome back to LingNow',
    subtitle: 'Sign in to continue your projects and generation workflow.',
    username: 'Username',
    password: 'Password',
    action: 'Sign in',
    loading: 'Signing in...',
    switch_lang: 'EN/ZH',
    invalid: 'Invalid username or password',
    unavailable: 'Login service is unavailable. Please make sure both frontend and backend are running.',
    unexpected: 'Login failed. Please try again later.'
  }
}[locale.value]))

const toggleLang = () => {
  locale.value = locale.value === 'ZH' ? 'EN' : 'ZH'
  localStorage.setItem('lingnow_lang', locale.value)
}

const resolveRedirect = () => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/'
}

const resolveLoginError = (err) => {
  const status = err?.response?.status
  if (status === 401) {
    return i18n.value.invalid
  }
  if (!err?.response) {
    return i18n.value.unavailable
  }
  return String(err?.response?.data?.message || err?.response?.data?.error || i18n.value.unexpected)
}

const handleLogin = async () => {
  if (!authData.value.username || !authData.value.password || loading.value) return
  loading.value = true
  error.value = ''
  try {
    const res = await axios.post('/api/auth/login', authData.value)
    localStorage.setItem('user', JSON.stringify(res.data))
    router.replace(resolveRedirect())
  } catch (err) {
    console.error('Login failed', err)
    error.value = resolveLoginError(err)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen relative overflow-hidden text-white selection:bg-cyan-500/30">
    <div class="pointer-events-none absolute inset-0">
      <div
          class="absolute left-1/2 top-0 h-[440px] w-[760px] -translate-x-1/2 rounded-full bg-cyan-500/10 blur-[120px]"></div>
      <div
          class="absolute bottom-[-120px] right-[-80px] h-[360px] w-[360px] rounded-full bg-purple-600/10 blur-[120px]"></div>
    </div>
    <div class="relative z-10 flex min-h-screen flex-col">
      <header class="h-16 px-8 border-b border-white/5 bg-black/40 backdrop-blur-3xl flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 flex items-center justify-center">
            <svg class="h-9 w-auto logo-heartbeat text-white fill-current" viewBox="0 0 100 100">
              <rect height="50" rx="6" width="80" x="10" y="25"/>
              <path d="M25 35 V65 H38 V35 L55 65 V35 H68 V65" fill="none" stroke="black" stroke-linecap="square"
                    stroke-width="8"/>
            </svg>
          </div>
          <h1 class="text-xl font-black tracking-tighter uppercase italic text-white">LingNow</h1>
        </div>
        <button
            class="flex items-center gap-2 px-3 py-1.5 rounded-full border border-white/10 glass-morphism text-xs font-bold transition-all hover:border-white/30"
            @click="toggleLang">
          <Globe class="w-3.5 h-3.5 text-gray-500"/>
          <span class="text-[10px] font-black">{{ i18n.switch_lang }}</span>
        </button>
      </header>

      <main class="flex-1 flex items-center justify-center px-6 py-16">
        <div class="w-full max-w-md rounded-[32px] border border-white/10 bg-black/30 p-8 shadow-2xl backdrop-blur-3xl">
          <div class="mb-8 text-center">
            <h2 class="text-4xl font-black tracking-tighter">{{ i18n.title }}</h2>
            <p class="mt-3 text-sm leading-6 text-gray-400">{{ i18n.subtitle }}</p>
          </div>

          <div class="space-y-4">
            <input
                v-model="authData.username"
                :placeholder="i18n.username"
                class="w-full rounded-2xl border border-white/10 bg-black/30 px-4 py-3 text-sm text-white outline-none focus:border-blue-500/50"
                type="text">
            <input
                v-model="authData.password"
                :placeholder="i18n.password"
                class="w-full rounded-2xl border border-white/10 bg-black/30 px-4 py-3 text-sm text-white outline-none focus:border-blue-500/50"
                type="password"
                @keydown.enter="handleLogin">
            <div v-if="error" class="rounded-2xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-xs text-red-300">
              {{ error }}
            </div>
            <button
                :disabled="loading"
                class="w-full rounded-2xl bg-gradient-to-r from-cyan-500 to-purple-600 px-4 py-3 text-sm font-black text-white transition-all hover:scale-[1.02] active:scale-[0.98] disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2 shadow-lg shadow-cyan-500/20"
                @click="handleLogin">
              <Loader2 v-if="loading" class="w-4 h-4 animate-spin"/>
              <span>{{ loading ? i18n.loading : i18n.action }}</span>
            </button>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>
