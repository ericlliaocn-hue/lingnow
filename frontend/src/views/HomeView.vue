<template>
  <div id="top" class="min-h-screen relative text-white selection:bg-cyan-500/30">
    <!-- Top Navigation -->
    <nav :class="{'glass-morphism py-4': scrolled}"
         class="fixed top-0 w-full z-50 px-8 py-6 flex justify-between items-center transition-all duration-300">
      <div class="flex items-center gap-4 group cursor-pointer" @click="router.push('/')">
        <div class="w-10 h-10 flex items-center justify-center">
          <svg class="h-9 w-auto logo-heartbeat text-white fill-current" viewBox="0 0 100 100">
            <rect height="50" rx="6" width="80" x="10" y="25"/>
            <path d="M25 35 V65 H38 V35 L55 65 V35 H68 V65" fill="none" stroke="black" stroke-linecap="square"
                  stroke-width="8"/>
          </svg>
        </div>
        <span class="text-xl font-black tracking-tighter uppercase italic text-white">LingNow</span>
      </div>
      <div class="hidden md:flex gap-8 text-sm font-medium text-gray-400">
        <router-link class="hover:text-white transition-colors" to="/solutions">{{ t('nav.solutions') }}</router-link>
        <a class="hover:text-white transition-colors cursor-pointer"
           @click="store.isPricingOpen = true">{{ t('nav.pricing') }}</a>
      </div>
      <div class="flex gap-4 items-center">
        <div class="hidden lg:flex gap-6 text-xs font-bold text-gray-500 uppercase tracking-widest mr-4">
          <router-link class="hover:text-cyan-400 transition-colors" to="/docs">{{ t('nav.docs') }}</router-link>
          <router-link class="hover:text-cyan-400 transition-colors" to="/updates">{{ t('nav.updates') }}</router-link>
        </div>
        <!-- i18n Dropdown (Alignment with lingnow.cc) -->
        <div class="relative group mr-2">
          <button
              class="flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-white/10 glass-morphism text-xs font-bold transition-all hover:border-white/30">
            <span>{{ currentLang === 'cn' ? '🇨🇳' : '🇺🇸' }}</span>
            <span class="text-gray-300 uppercase">{{ currentLang }}</span>
            <svg class="group-hover:rotate-180 transition-transform" fill="none" height="12" stroke="currentColor" stroke-linecap="round"
                 stroke-linejoin="round" stroke-width="2" viewBox="0 0 24 24" width="12"
                 xmlns="http://www.w3.org/2000/svg">
              <path d="m6 9 6 6 6-6"/>
            </svg>
          </button>
          <div
              class="absolute right-0 mt-2 w-32 glass-morphism rounded-xl p-1.5 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-200 transform origin-top translate-y-[-10px] group-hover:translate-y-0">
            <button class="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 flex items-center gap-2 text-xs font-medium"
                    @click="setLang('cn')">
              <span>🇨🇳</span> 中文 (简体)
            </button>
            <button class="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 flex items-center gap-2 text-xs font-medium"
                    @click="setLang('en')">
              <span>🇺🇸</span> English (US)
            </button>
          </div>
        </div>

        <template v-if="isAuthenticated">
          <button
              class="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-4 py-2 text-xs font-black uppercase tracking-widest text-cyan-300 transition-all hover:border-cyan-400/40 hover:bg-cyan-500/15"
              @click="router.push('/workbench')">
            {{ t('nav.workspace') }}
          </button>
          <div class="flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1.5">
            <span class="text-[10px] font-black uppercase tracking-[0.25em] text-gray-500">Online</span>
            <span class="text-sm font-bold text-white">{{ user?.username || 'User' }}</span>
            <button
                class="rounded-full border border-white/10 px-2.5 py-1 text-[10px] font-black uppercase tracking-widest text-gray-400 transition-all hover:border-red-400/30 hover:text-red-300"
                @click="handleHomeLogout">
              {{ t('nav.logout') }}
            </button>
          </div>
        </template>
        <a
            v-else
            :href="authEntryHref"
            class="relative z-10 cursor-pointer text-sm font-medium text-gray-400 transition-colors hover:text-cyan-400"
            @click.prevent="goToAuthEntry">
          {{ authEntryLabel }}
        </a>
      </div>
    </nav>

    <!-- Hero Section -->
    <main class="relative pt-32 pb-20 px-6 flex flex-col items-center text-center">
      <!-- Background Ambient Glow -->
      <div
          class="absolute top-0 left-1/2 -translate-x-1/2 w-[800px] h-[500px] bg-cyan-500/10 blur-[120px] pointer-events-none rounded-full"></div>

      <div
          class="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-white/10 bg-white/5 text-[10px] font-bold tracking-widest text-cyan-400 uppercase mb-8">
        <span class="relative flex h-2 w-2">
          <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75"></span>
          <span class="relative inline-flex rounded-full h-2 w-2 bg-cyan-500"></span>
        </span>
        v3.0 Industrial Release
      </div>

      <h1 class="text-5xl md:text-7xl font-extrabold tracking-tight mb-6 bg-gradient-to-b from-white to-gray-500 bg-clip-text text-transparent">
        {{ t('hero.title_p1') }} <br/>{{ t('hero.title_p2') }}
      </h1>

      <p class="max-w-2xl text-gray-400 text-lg md:text-xl mb-12 leading-relaxed">
        {{ t('hero.subtitle') }}
      </p>

      <!-- Central Prompt Box (The Core Insight from alignment) -->
      <div class="w-full max-w-3xl relative group">
        <div
            class="absolute -inset-1 bg-gradient-to-r from-cyan-500 to-purple-600 rounded-2xl blur opacity-20 group-hover:opacity-40 transition duration-1000"></div>
        <div class="relative glass-morphism rounded-2xl p-2 flex flex-col gap-2 shadow-2xl">
          <div class="flex items-center gap-3 px-4 pt-4 pb-2 border-b border-white/5">
            <div class="flex gap-1.5">
              <div class="w-2.5 h-2.5 rounded-full bg-red-500/50"></div>
              <div class="w-2.5 h-2.5 rounded-full bg-yellow-500/50"></div>
              <div class="w-2.5 h-2.5 rounded-full bg-green-500/50"></div>
            </div>
            <div class="text-[10px] font-mono text-gray-500 uppercase flex-1 text-left ml-2">
              Product-Architect-Session-001
            </div>
            <div class="flex items-center gap-2 text-[10px] text-cyan-500 font-bold">
              <span class="animate-pulse">●</span> {{ t('hero.agentStatus') }}
            </div>
          </div>
          <div class="p-4 pt-2">
            <textarea
                v-model="prompt"
                :placeholder="t('hero.placeholder')"
                class="h-32 w-full resize-none border-0 bg-transparent text-xl leading-tight text-white outline-none ring-0 placeholder:text-gray-600 focus:border-0 focus:outline-none focus:ring-0 md:text-2xl"
            ></textarea>
          </div>
          <div class="flex justify-between items-center px-4 pb-4">
            <div class="flex gap-2">
              <button class="p-2 rounded-lg bg-white/5 hover:bg-white/10 transition-colors border border-white/5"
                      title="Upload Reference">
                <svg fill="none" height="16" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"
                     stroke-width="2" viewBox="0 0 24 24" width="16" xmlns="http://www.w3.org/2000/svg">
                  <path
                      d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.51a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
                </svg>
              </button>
              <button class="p-2 rounded-lg bg-white/5 hover:bg-white/10 transition-colors border border-white/5"
                      title="Voice Command">
                <svg fill="none" height="16" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"
                     stroke-width="2" viewBox="0 0 24 24" width="16" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/>
                  <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                  <line x1="12" x2="12" y1="19" y2="22"/>
                </svg>
              </button>
            </div>
            <button class="px-6 py-2 bg-gradient-to-r from-cyan-500 to-purple-600 rounded-xl font-bold text-sm hover:shadow-[0_0_20px_rgba(0,209,255,0.4)] transition-all flex items-center gap-2"
                    @click="startGeneration">
              {{ t('hero.generateBtn') }}
              <svg fill="none" height="16" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"
                   stroke-width="2" viewBox="0 0 24 24" width="16" xmlns="http://www.w3.org/2000/svg">
                <path d="m5 12 7-7 7 7"/>
                <path d="M12 19V5"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </main>

    <!-- Footer -->
    <BrandFooter/>
  </div>
</template>

<script setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import axios from 'axios'
import {store} from '../store'
import BrandFooter from '../components/BrandFooter.vue'

const router = useRouter()
const prompt = ref('')
const scrolled = ref(false)
const currentLang = ref(localStorage.getItem('lang') || 'cn')
const user = ref(null)

const setLang = (lang) => {
  currentLang.value = lang
  localStorage.setItem('lang', lang)
}

const i18n = {
  en: {
    nav: {
      platform: 'Platform',
      solutions: 'Solutions',
      pricing: 'Pricing',
      showcase: 'Showcase',
      login: 'Log in',
      workspace: 'Workbench',
      logout: 'Log out',
      getStarted: 'Get Started',
      docs: 'Docs',
      updates: 'Updates'
    },
    hero: {
      title_p1: 'Build with the',
      title_p2: 'AI Software Factory',
      subtitle: 'Scale your software development with an autonomous multi-agent network. Transform ideas into production-ready high-fi products in seconds.',
      agentStatus: 'ACTIVE_AGENT_NETWORK',
      placeholder: 'What do you want to build today?',
      generateBtn: 'Generate Now'
    },
    solutions: {
      title: 'Expert Infrastructure',
      cats: {ecommerce: 'E-Commerce', dashboard: 'Data Dashboards', social: 'Social Networks'},
      details: {
        ecommerce: 'Unlock high-conversion shopping experiences with agents trained on global consumer psychology and localized inventory logistics.',
        dashboard: 'Process massive data streams with architectural precision. High-performance visualizations built by data-specialist agents.',
        social: 'Scalable interactivity and real-time synchronization. Built-in compliance and moderation filters by security auditors.'
      }
    },
    showcase: {
      subtitle: 'Real applications built with LingNow Autonomous Agents.',
      cases: {
        fitpulse: 'Complex healthcare analytics engine with real-time data streaming.',
        ecommerce: 'E-commerce Backend Engine'
      },
      subsections: {'2phase': 'Instant Seeding & Async Polishing Technology.'}
    },
    pricing: {
      title: 'Industrialized Strategy',
      subtitle: 'Simple, transparent pricing to power your AI software workspace.',
      cta: 'Start Building',
      plans: {
        free: {name: 'Explorer', features: ['3 Weekly Projects', 'Standard Seed Output', 'Community Support']},
        pro: {
          name: 'Pro Agent',
          features: ['Unlimited Projects', 'Two-Phase Async Polishing', 'Industrial Component Library', 'Priority Agent Queue']
        },
        factory: {
          name: 'Factory',
          features: ['Custom Agent Training', 'Self-Hosted Deployments', 'Private Security Audit', '24/7 Dedicated Support']
        }
      }
    }
  },
  cn: {
    nav: {
      platform: '平台',
      solutions: '解决方案',
      pricing: '定价',
      showcase: '案例',
      login: '登录',
      workspace: '工作台',
      logout: '退出登录',
      getStarted: '即刻开始',
      docs: '文档',
      updates: '动态'
    },
    hero: {
      title_p1: '用 AI 软件工厂',
      title_p2: '即刻构建您的产品',
      subtitle: '利用多智能体网络扩展您的开发能力。将创意瞬间转化为工业级高保真产品。',
      agentStatus: '专家 Agent 矩阵已就绪',
      placeholder: '您今天想构建什么？',
      generateBtn: '立即生成'
    },
    solutions: {
      title: '行业级专家基座',
      cats: {ecommerce: '跨境电商', dashboard: '数据仪表盘', social: '社交网络'},
      details: {
        ecommerce: '基于全球消费心理模型和本地化库存逻辑，由专业 Agent 打造极具转化率的购物体验。',
        dashboard: '精密处理大规模数据流。由数据专家 Agent 生成的高性能可视化界面与弹性架构。',
        social: '极致的交互体验与实时同步能力。内置由安全审计 Agent 提供的合规性与社交治理方案。'
      }
    },
    showcase: {
      subtitle: '由 LingNow 自主专家 Agent 矩阵构建的真实应用案例。',
      cases: {fitpulse: '具备实时数据流监控的复杂医疗级分析引擎仪表盘。', ecommerce: '工业级电商后端引擎'},
      subsections: {'2phase': 'Phase 1 种子生成与 Phase 2 异步抛光技术。'}
    },
    pricing: {
      title: '工业化定价策略',
      subtitle: '简单透明的阶梯定价，为您的 AI 软件工场提供动力。',
      cta: '开始构建',
      plans: {
        free: {name: '探索版', features: ['每周 3 个项目', '标准种子原型输出', '社区支持']},
        pro: {
          name: '专业 Agent',
          features: ['无限量项目生成', '两阶段异步抛光技术', '工业级高保真组件库', '优先 Agent 队列']
        },
        factory: {
          name: '企业工厂',
          features: ['定制化 Agent 训练', '私有化部署支持', '专项安全审计', '24/7 专属技术支持']
        }
      }
    }
  }
}

const t = (path) => {
  const keys = path.split('.')
  let res = i18n[currentLang.value]
  for (const key of keys) {
    res = res[key]
  }
  return res || path
}

const syncUser = () => {
  try {
    user.value = JSON.parse(localStorage.getItem('user') || 'null')
  } catch (e) {
    user.value = null
  }
}

const isAuthenticated = computed(() => !!user.value?.token)
const authEntryHref = computed(() => isAuthenticated.value ? '/workbench' : '/login')
const authEntryLabel = computed(() => isAuthenticated.value ? t('nav.workspace') : t('nav.login'))



const startGeneration = () => {
  if (!prompt.value.trim()) return

  // Show Loading Animation for Demo
  store.isLoading = true
  sessionStorage.setItem('lingnow_seed_prompt', prompt.value.trim())
  sessionStorage.setItem('lingnow_seed_trail', JSON.stringify([
    '输入需求',
    prompt.value.trim()
  ]))

  if (!isAuthenticated.value) {
    setTimeout(() => {
      store.isLoading = false
      router.push({
        path: '/workbench',
        query: {p: prompt.value}
      })
    }, 1200)
    return
  }

  const userToken = user.value?.token
  const sessionId = `session-${Date.now()}`
  axios.post('/api/generate/plan', {
    prompt: prompt.value,
    sessionId,
    lang: currentLang.value === 'cn' ? 'ZH' : 'EN'
  }, {
    headers: userToken ? {satoken: userToken} : {}
  }).then(() => {
    router.push(`/project/${sessionId}`)
  }).catch((err) => {
    console.error('Home plan generation failed, falling back to workbench route', err)
    router.push({
      path: '/workbench',
      query: {p: prompt.value}
    })
  }).finally(() => {
    store.isLoading = false
  })
}

const goToAuthEntry = () => {
  syncUser()
  router.push(isAuthenticated.value ? '/workbench' : '/login')
}

const handleHomeLogout = () => {
  localStorage.removeItem('user')
  syncUser()
  router.push('/')
}

const handleScroll = () => {
  scrolled.value = window.scrollY > 50
}

onMounted(() => {
  syncUser()
  window.addEventListener('scroll', handleScroll)
  window.addEventListener('storage', syncUser)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('storage', syncUser)
})


</script>

<style scoped>
.hide-scrollbar::-webkit-scrollbar {
  display: none;
}

.hide-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

.animate-spin-slow {
  animation: spin 8s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes spin-slow {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
