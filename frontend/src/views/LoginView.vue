<script setup>
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import axios from 'axios'
import {Globe, Loader2, Zap} from 'lucide-vue-next'

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
    invalid: '用户名或密码无效'
  },
  EN: {
    title: 'Welcome back to LingNow',
    subtitle: 'Sign in to continue your projects and generation workflow.',
    username: 'Username',
    password: 'Password',
    action: 'Sign in',
    loading: 'Signing in...',
    switch_lang: 'EN/ZH',
    invalid: 'Invalid username or password'
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

const handleLogin = async () => {
  if (!authData.value.username || !authData.value.password || loading.value) return
  loading.value = true
  error.value = ''
  try {
    const res = await axios.post('/api/auth/login', authData.value)
    localStorage.setItem('user', JSON.stringify(res.data))
    router.replace(resolveRedirect())
  } catch (err) {
    error.value = i18n.value.invalid
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-black text-white relative overflow-hidden">
    <div
        class="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(59,130,246,0.16),transparent_35%),radial-gradient(circle_at_bottom_right,rgba(244,63,94,0.14),transparent_28%)]"></div>
    <div class="relative z-10 flex min-h-screen flex-col">
      <header class="h-16 px-8 border-b border-white/5 bg-black/40 backdrop-blur-3xl flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div
              class="w-10 h-10 bg-gradient-to-br from-blue-600 to-indigo-700 rounded-xl flex items-center justify-center shadow-lg">
            <Zap class="w-6 h-6 text-white fill-current"/>
          </div>
          <h1 class="text-xl font-black tracking-tighter uppercase italic">LingNow</h1>
        </div>
        <button
            class="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-white/5 border border-white/5 hover:border-white/20 transition-all"
            @click="toggleLang">
          <Globe class="w-3.5 h-3.5 text-gray-500"/>
          <span class="text-[10px] font-black">{{ i18n.switch_lang }}</span>
        </button>
      </header>

      <main class="flex-1 flex items-center justify-center px-6">
        <div class="w-full max-w-md rounded-[32px] border border-white/10 bg-white/5 p-8 shadow-2xl backdrop-blur-2xl">
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
                class="w-full rounded-2xl bg-blue-600 px-4 py-3 text-sm font-black text-white transition-all hover:bg-blue-500 disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2"
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
