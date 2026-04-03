<template>
  <div class="min-h-screen tech-grid text-white selection:bg-cyan-500/30 overflow-x-hidden">
    <!-- Navbar (Consistent) -->
    <nav
        class="fixed top-0 w-full z-50 px-8 py-6 flex justify-between items-center glass-morphism border-b border-white/5">
      <div class="flex items-center gap-2 cursor-pointer" @click="$router.push('/')">
        <img alt="Logo" class="w-8 h-8 object-contain" src="/logo-icon.png"/>
        <span class="text-xl font-bold tracking-tight text-white">LingNow</span>
      </div>

      <div class="hidden md:flex gap-8 text-sm font-medium text-gray-400">
        <router-link class="hover:text-white transition-colors" to="/solutions">{{ t('nav.solutions') }}</router-link>
        <a class="hover:text-white transition-colors cursor-pointer"
           @click="store.isPricingOpen = true">{{ t('nav.pricing') }}</a>
      </div>

      <div class="flex gap-4 items-center">
        <div class="hidden lg:flex gap-6 text-xs font-bold text-gray-500 tracking-widest mr-4">
          <router-link class="hover:text-cyan-400 transition-colors" to="/docs">{{ t('nav.docs') }}</router-link>
          <router-link class="hover:text-cyan-400 transition-colors" to="/updates">{{ t('nav.updates') }}</router-link>
        </div>
        <!-- i18n Dropdown -->
        <div class="relative group mr-2">
          <button
              class="flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-white/10 glass-morphism text-xs font-bold transition-all hover:border-white/30">
            <span>{{ currentLang === 'cn' ? '🇨🇳' : '🇺🇸' }}</span>
            <span class="text-gray-300 uppercase">{{ currentLang }}</span>
          </button>
          <div
              class="absolute right-0 mt-2 w-32 glass-morphism rounded-xl p-1.5 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all">
            <button class="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 text-xs" @click="setLang('cn')">
              🇨🇳 中文
            </button>
            <button class="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 text-xs" @click="setLang('en')">
              🇺🇸 English
            </button>
          </div>
        </div>
        <button class="px-5 py-2 bg-white text-black rounded-full text-sm font-bold hover:bg-cyan-400 transition-all"
                @click="$router.push('/workbench')">
          {{ t('nav.launch') }}
        </button>
      </div>
    </nav>

    <div class="max-w-7xl mx-auto pt-48 pb-32 px-8">
      <div class="mb-20">
        <h1 class="text-6xl font-black mb-4 tracking-tighter">{{ t('solutions.hero_title') }}</h1>
        <p class="text-gray-500 text-lg max-w-2xl">{{ t('solutions.hero_subtitle') }}</p>
        <div class="mt-8 flex gap-4">
          <a class="px-6 py-3 rounded-full border border-cyan-500/30 bg-cyan-500/5 text-cyan-400 text-sm font-bold hover:bg-cyan-500/10 transition-all flex items-center gap-2"
             href="#showcase">
            <svg fill="none" height="16" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"
                 stroke-width="2" viewBox="0 0 24 24" width="16" xmlns="http://www.w3.org/2000/svg">
              <path d="m7 13 5 5 5-5M7 6l5 5 5-5"/>
            </svg>
            {{ currentLang === 'cn' ? '查看行业落地案例' : 'Browse Success Cases' }}
          </a>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-8">
        <div v-for="sol in solList" :key="sol.id"
             class="group relative glass-morphism rounded-[2.5rem] p-10 border border-white/10 hover:border-cyan-500/30 transition-all cursor-pointer flex flex-col h-full overflow-hidden">
          <div
              class="absolute -top-10 -right-10 w-40 h-40 bg-gradient-to-br from-cyan-500/5 to-purple-600/5 rounded-full blur-[40px] opacity-0 group-hover:opacity-100 transition-opacity"></div>

          <div class="w-14 h-14 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center mb-8">
            <component :is="sol.icon" class="w-6 h-6 text-gray-400 group-hover:text-cyan-400 transition-colors"/>
          </div>

          <h3 class="text-2xl font-bold mb-4 tracking-tight group-hover:text-white transition-colors">
            {{ t(`solutions.items.${sol.id}.title`) }}
          </h3>
          <p class="text-gray-400 text-sm leading-relaxed mb-10 flex-1">
            {{ t(`solutions.items.${sol.id}.desc`) }}
          </p>

          <div class="space-y-3 mb-10">
            <div v-for="agent in t(`solutions.items.${sol.id}.agents`)" :key="agent" class="flex items-center gap-2">
              <div class="w-1 h-1 rounded-full bg-cyan-500"></div>
              <span class="text-[10px] font-black text-gray-500 uppercase tracking-widest">{{ agent }}</span>
            </div>
          </div>

          <button
              class="w-full py-4 rounded-2xl bg-white/5 border border-white/5 text-[10px] font-black uppercase tracking-widest hover:bg-white hover:text-black transition-all">
            {{ t('solutions.cta_details') }}
          </button>
        </div>
      </div>

      <!-- Detail Showcase (Consistency Model) -->
      <div
          class="mt-32 p-16 glass-morphism rounded-[3rem] border border-white/10 flex flex-col md:flex-row items-center gap-20">
        <div class="md:w-1/2">
          <h2 class="text-4xl font-black mb-6 tracking-tight" v-html="t('solutions.model.title')"></h2>
          <p class="text-gray-400 leading-relaxed mb-8">{{ t('solutions.model.desc') }}</p>
          <ul class="space-y-4 font-mono text-xs text-gray-500">
            <li v-for="(step, idx) in t('solutions.model.steps')" :key="idx" class="flex gap-4">
              <span>[{{ (idx + 1).toString().padStart(2, '0') }}]</span>
              <span :class="{ 'text-cyan-500': idx === 2 }">{{ step }}</span>
            </li>
          </ul>
        </div>
        <div class="relative group aspect-video rounded-3xl overflow-hidden glass-morphism border border-white/10">
          <div class="absolute inset-0 bg-gradient-to-br from-cyan-400/20 to-purple-600/20 mix-blend-overlay"></div>
          <div class="flex items-center justify-center h-full text-gray-600 italic text-sm">
            {{ currentLang === 'cn' ? '工业方案预览加载中...' : 'Industrial Solution Preview Loading...' }}
          </div>
        </div>
      </div>

      <!-- NEW: Integrated Case Showcase (Bento Grid) -->
      <section id="showcase" class="mt-48">
        <div class="flex justify-between items-end mb-16 px-4">
          <div>
            <div
                class="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-white/10 bg-white/5 text-[10px] font-bold tracking-widest text-cyan-400 uppercase mb-4">
              Empirical Proof
            </div>
            <h2 class="text-4xl font-black tracking-tighter">{{ t('showcase.title') }}</h2>
            <p class="text-gray-500 mt-2">{{ t('showcase.subtitle') }}</p>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          <div v-for="(caseItem, id) in mockCases" :key="id"
               :class="id === 'fitpulse' ? 'lg:col-span-2 lg:row-span-2 h-[500px]' : 'h-[320px]'"
               class="group relative overflow-hidden rounded-3xl border border-white/10 glass-morphism cursor-pointer transition-all hover:scale-[1.02] hover:border-cyan-500/50"
               @click="openCase(id)">
            <img :src="caseItem.img"
                 class="w-full h-full object-cover opacity-40 transition-transform duration-700 group-hover:scale-110"/>
            <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent"></div>
            <div class="absolute bottom-6 left-6 right-6">
              <div class="text-[9px] font-bold text-cyan-400 mb-1 uppercase tracking-widest">{{
                  caseItem.tags[0]
                }}
              </div>
              <h3 :class="id === 'fitpulse' ? 'text-4xl' : 'text-xl'"
                  class="font-bold transition-colors group-hover:text-cyan-400">{{ caseItem.title }}</h3>
              <p v-if="id === 'fitpulse'" class="text-gray-400 text-sm mt-2 max-w-md leading-relaxed line-clamp-2">
                {{ t(`showcase.cases.${id}`) }}</p>
            </div>
          </div>

          <!-- Build CTA Card -->
          <div
              class="relative group overflow-hidden rounded-3xl border-2 border-dashed border-white/10 glass-morphism h-[320px] flex flex-col items-center justify-center text-center p-8 hover:border-cyan-500/30 transition-all cursor-pointer"
              @click="router.push('/workbench')">
            <div
                class="w-16 h-16 rounded-full bg-cyan-500/10 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
              <svg class="text-cyan-400" fill="none" height="24" stroke="currentColor" stroke-linecap="round"
                   stroke-linejoin="round" stroke-width="2" viewBox="0 0 24 24" width="24"
                   xmlns="http://www.w3.org/2000/svg">
                <path d="M5 12h14"/>
                <path d="m12 5 7 7-7 7"/>
              </svg>
            </div>
            <h4 class="text-lg font-bold mb-2">{{ currentLang === 'cn' ? '部署新方案' : 'Deploy New Solution' }}</h4>
            <p class="text-[10px] text-gray-500 max-w-[180px] uppercase font-bold tracking-widest">
              {{ currentLang === 'cn' ? '立即将逻辑转化为落地原型' : 'Convert logic into reality now' }}</p>
          </div>
        </div>
      </section>
    </div>

    <!-- Case Detail Modal -->
    <Transition name="fade">
      <div v-if="selectedCase"
           class="fixed inset-0 z-[100] flex items-center justify-center px-4 py-10 bg-black/90 backdrop-blur-xl">
        <div class="absolute inset-0" @click="selectedCase = null"></div>
        <div
            class="relative w-full max-w-6xl h-full glass-morphism rounded-[2.5rem] overflow-hidden flex flex-col md:flex-row shadow-2xl border border-white/20">
          <button
              class="absolute top-6 right-6 z-10 w-10 h-10 rounded-full bg-black/50 flex items-center justify-center border border-white/20 hover:bg-white/10 transition-colors"
              @click="selectedCase = null">✕
          </button>
          <div
              class="md:w-3/5 bg-[#050505] h-2/5 md:h-full flex items-center justify-center overflow-hidden border-r border-white/5">
            <img :src="selectedCaseData.img" class="w-full h-full object-cover opacity-70"/>
          </div>
          <div class="md:w-2/5 p-12 flex flex-col h-3/5 md:h-full overflow-y-auto custom-scrollbar bg-[#0A0A0A]/80">
            <h3 class="text-4xl font-black mb-4 tracking-tighter">{{ selectedCaseData.title }}</h3>
            <div class="flex flex-wrap gap-2 mb-10">
              <span v-for="tag in selectedCaseData.tags" :key="tag"
                    class="px-3 py-1 rounded-full bg-white/5 border border-white/10 text-[9px] font-bold text-gray-400 uppercase tracking-widest">{{
                  tag
                }}</span>
            </div>
            <div class="space-y-10">
              <div>
                <h4 class="text-[10px] font-bold text-cyan-400 uppercase tracking-[0.2em] mb-4">Industrial
                  Prompt_Ref</h4>
                <div
                    class="bg-black p-5 rounded-2xl border border-white/5 font-mono text-xs leading-relaxed text-gray-400 italic shadow-inner">
                  "{{ selectedCaseData.prompt }}"
                </div>
              </div>
              <div>
                <h4 class="text-[10px] font-bold text-cyan-400 uppercase tracking-[0.2em] mb-4">Agent
                  Validation_Score</h4>
                <div class="grid grid-cols-2 gap-4">
                  <div v-for="(v, k) in selectedCaseData.scores" :key="k"
                       class="p-5 rounded-2xl bg-white/[0.02] border border-white/5 transition-colors hover:border-cyan-500/20">
                    <div class="text-[10px] text-gray-600 uppercase font-bold mb-1">{{ k }}</div>
                    <div class="text-2xl font-black text-white">{{ v }}%</div>
                  </div>
                </div>
              </div>
            </div>
            <div class="mt-auto pt-12">
              <button
                  class="w-full py-5 bg-white text-black font-black rounded-2xl hover:bg-cyan-400 transition-all active:scale-95 text-center shadow-lg hover:shadow-cyan-500/20"
                  @click="router.push({ path: '/workbench', query: { p: selectedCaseData.prompt } })">
                {{ currentLang === 'cn' ? '以此方案为蓝本生成' : 'GENERATE_WITH_TEMPLATE' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import {computed, ref} from 'vue'
import {useRouter} from 'vue-router'
import {
  LayoutDashboard as LayoutDashboardIcon,
  Share2 as Share2Icon,
  ShoppingBag as ShoppingBagIcon
} from 'lucide-vue-next'

const router = useRouter()

const currentLang = ref(localStorage.getItem('lang') || 'cn')

const setLang = (lang) => {
  currentLang.value = lang
  localStorage.setItem('lang', lang)
}

const solList = [
  {id: 'ecommerce', icon: ShoppingBagIcon},
  {id: 'fintech', icon: LayoutDashboardIcon},
  {id: 'community', icon: Share2Icon}
]

const selectedCase = ref(null)

const mockCases = {
  fitpulse: {
    title: 'FitPulse AI Pro',
    tags: ['Healthcare', 'Dashboard', 'Real-time'],
    img: 'https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?auto=format&fit=crop&w=1200',
    prompt: 'Create a high-fidelity analytics dashboard for a global fitness chain with real-time occupancy monitoring and predictive yoga class scheduling...',
    scores: {'UI Accuracy': 99, 'Backend Logic': 97, 'Security': 98, 'Performance': 99}
  },
  ecommerce: {
    title: 'SwiftCommerce HQ',
    tags: ['SaaS', 'E-commerce', 'Mobile First'],
    img: 'https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?auto=format&fit=crop&w=800',
    prompt: 'Build a production-ready shopping engine with localized currency support and autonomous inventory reconciliation...',
    scores: {'UI Accuracy': 98, 'Backend Logic': 99, 'Security': 96, 'Performance': 98}
  },
  eduflow: {
    title: 'EduFlow Learning',
    tags: ['Education', 'LMS', 'Interactive'],
    img: 'https://images.unsplash.com/photo-1501504905252-473c47e087f8?auto=format&fit=crop&w=800',
    prompt: 'Design an AI-driven adaptive learning portal with student progress heatmaps and personalized course pathing...',
    scores: {'UI Accuracy': 97, 'Backend Logic': 98, 'Security': 99, 'Performance': 96}
  },
  finpulse: {
    title: 'FinPulse Terminal',
    tags: ['Finance', 'Trading', 'High-Fi'],
    img: 'https://images.unsplash.com/photo-1611974714658-75d4f11a8117?auto=format&fit=crop&w=800',
    prompt: 'Develop a professional-grade trading terminal with millisecond execution latency and multi-asset compliance auditing...',
    scores: {'UI Accuracy': 99, 'Backend Logic': 99, 'Security': 99, 'Performance': 99}
  },
  nexusgamers: {
    title: 'Nexus Gamers Hub',
    tags: ['Social', 'Gaming', 'Community'],
    img: 'https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=800',
    prompt: 'Create a vibrant gaming community hub with real-time match tracking and automated tournament governance...',
    scores: {'UI Accuracy': 96, 'Backend Logic': 95, 'Security': 97, 'Performance': 98}
  },
  smarthome: {
    title: 'OmniHome Center',
    tags: ['IoT', 'Utility', 'Automation'],
    img: 'https://images.unsplash.com/photo-1558002038-1055907df827?auto=format&fit=crop&w=800',
    prompt: 'Implement a centralized smart home management console with predictive energy optimization and security protocol auditing...',
    scores: {'UI Accuracy': 99, 'Backend Logic': 98, 'Security': 99, 'Performance': 97}
  },
  metacommerce: {
    title: 'MetaCommerce v3',
    tags: ['Web3', 'Blockchain', 'Next-Gen'],
    img: 'https://images.unsplash.com/photo-1639762681485-074b7f938ba0?auto=format&fit=crop&w=800',
    prompt: 'Build a next-gen Web3 marketplace with seamless wallet integration and cross-chain transaction monitoring...',
    scores: {'UI Accuracy': 97, 'Backend Logic': 96, 'Security': 99, 'Performance': 95}
  },
  healthtrack: {
    title: 'HealthTrack Portal',
    tags: ['Medical', 'Analytics', 'Patient Care'],
    img: 'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=800',
    prompt: 'Design a patient-centric health portal with encrypted medical history storage and autonomous diagnostic assistance...',
    scores: {'UI Accuracy': 98, 'Backend Logic': 97, 'Security': 99, 'Performance': 98}
  }
}

const selectedCaseData = computed(() => mockCases[selectedCase.value] || {})

const openCase = (id) => {
  selectedCase.value = id
}

const translations = {
  en: {
    nav: {solutions: 'Solutions', pricing: 'Pricing', docs: 'Docs', updates: 'Updates', launch: 'Launch'},
    showcase: {
      title: 'Successful Deliveries',
      subtitle: 'Real applications built with LingNow Autonomous Agents.',
      cases: {
        fitpulse: 'Complex healthcare analytics engine with real-time data streaming.',
        ecommerce: 'Industrial E-commerce Backend Engine'
      }
    },
    solutions: {
      hero_title: 'Vertical Solutions',
      hero_subtitle: 'Specialized multi-agent infrastructure designed for complex industry requirements.',
      cta_details: 'Explore Engine Details',
      items: {
        ecommerce: {
          title: 'Cross-Border Commerce',
          desc: 'High-conversion shopping engines trained on global psychology and logistics.',
          agents: ['Psychology Agent', 'Inventory Logic Auditor', 'SEO Specialist']
        },
        fintech: {
          title: 'Financial Dashboards',
          desc: 'Real-time analytical terminals with high-performance visualizations.',
          agents: ['Data Engineer', 'Visualization Designer', 'Compliance Auditor']
        },
        community: {
          title: 'Social Hub Architect',
          desc: 'Vibrant communities with real-time match tracking and automated hubs.',
          agents: ['Interaction Designer', 'Real-time Sync Agent', 'Governance Auditor']
        }
      },
      model: {
        title: 'The "Two-Phase" <br/><span class="text-cyan-400 italic">Consistency Model</span>',
        desc: "Our solutions aren't hardcoded. They are dynamically synthesized using a unique two-phase commit strategy, where the Architectural Agent plans the logic and the Designer Agent polishes the aesthetics in parallel.",
        steps: ['Architectural Logic Commit', 'Visual Lab Synthesis', 'Final Agent Assembly & Audit']
      }
    }
  },
  cn: {
    nav: {solutions: '解决方案', pricing: '定价', docs: '文档', updates: '动态', launch: '立即启动'},
    showcase: {
      title: '行业成功案例',
      subtitle: '由 LingNow 自主专家 Agent 矩阵构建的真实落地应用。',
      cases: {fitpulse: '具备实时数据流监控的复杂医疗级分析引擎仪表盘。', ecommerce: '工业级跨境电商后端引擎'}
    },
    solutions: {
      hero_title: '行业深度解决方案',
      hero_subtitle: '专为复杂行业需求设计的专业多智能体架构。',
      cta_details: '探索引擎细节',
      items: {
        ecommerce: {
          title: '跨境电商引擎',
          desc: '基于全球消费心理模型和物流逻辑训练的高转化率购物引擎。',
          agents: ['心理学 Agent', '库存逻辑审计', 'SEO 专家']
        },
        fintech: {
          title: '金融分析仪表盘',
          desc: '具备高性能可视化能力的实时金融分析终端。',
          agents: ['数据工程师', '视觉设计 Agent', '合规审计员']
        },
        community: {
          title: '社交枢纽架构',
          desc: '支持实时比赛追踪和自动化治理的充满活力的社区。',
          agents: ['交互设计师', '实时同步 Agent', '社交治理审计']
        }
      },
      model: {
        title: '"两阶段" <br/><span class="text-cyan-400 italic">一致性交付模型</span>',
        desc: '我们的解决方案并非硬编码。它们采用独特的两阶段提交策略动态合成：架构 Agent 规划逻辑，设计 Agent 同步打磨视觉。',
        steps: ['架构逻辑提交 (Logical Commit)', '视觉实验室合成', '终极 Agent 组装与审计']
      }
    }
  }
}

const t = (path) => {
  const keys = path.split('.')
  let res = translations[currentLang.value]
  for (const key of keys) {
    res = res ? res[key] : null
  }
  return res || path
}

</script>
