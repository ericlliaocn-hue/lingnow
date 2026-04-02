<template>
  <div class="min-h-screen tech-grid text-white selection:bg-cyan-500/30 overflow-x-hidden">
    <!-- Navbar (Consistent) -->
    <nav
        class="fixed top-0 w-full z-50 px-8 py-6 flex justify-between items-center glass-morphism border-b border-white/5">
      <div class="flex items-center gap-2 cursor-pointer" @click="$router.push('/')">
        <div
            class="w-8 h-8 bg-gradient-to-br from-cyan-400 to-purple-600 rounded-lg flex items-center justify-center font-bold text-xl text-white">
          L
        </div>
        <span class="text-xl font-bold tracking-tight text-white">LingNow</span>
      </div>

      <div class="hidden md:flex gap-8 text-sm font-medium text-gray-400">
        <router-link class="hover:text-white transition-colors" to="/">{{
            currentLang === 'cn' ? '平台' : 'Platform'
          }}
        </router-link>
        <router-link class="hover:text-white transition-colors" to="/solutions">
          {{ currentLang === 'cn' ? '解决方案' : 'Solutions' }}
        </router-link>
        <router-link class="hover:text-white transition-colors" to="/#pricing">
          {{ currentLang === 'cn' ? '定价' : 'Pricing' }}
        </router-link>
      </div>

      <div class="flex gap-4 items-center">
        <div class="hidden lg:flex gap-6 text-xs font-bold text-gray-500 tracking-widest mr-4">
          <router-link class="hover:text-cyan-400 transition-colors" to="/docs">
            {{ currentLang === 'cn' ? '文档' : 'Docs' }}
          </router-link>
          <router-link class="hover:text-cyan-400 transition-colors" to="/updates">
            {{ currentLang === 'cn' ? '动态' : 'Updates' }}
          </router-link>
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
            <button class="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 text-xs" @click="currentLang = 'cn'">
              🇨🇳 中文
            </button>
            <button class="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 text-xs" @click="currentLang = 'en'">
              🇺🇸 English
            </button>
          </div>
        </div>
        <button class="px-5 py-2 bg-white text-black rounded-full text-sm font-bold hover:bg-cyan-400 transition-all"
                @click="$router.push('/workbench')">
          Launch
        </button>
      </div>
    </nav>

    <div class="max-w-7xl mx-auto pt-48 pb-32 px-8">
      <div class="mb-20">
        <h1 class="text-6xl font-black mb-4 tracking-tighter">Vertical Solutions</h1>
        <p class="text-gray-500 text-lg max-w-2xl">Specialized multi-agent infrastructure designed for complex industry
          requirements.</p>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-8">
        <div v-for="sol in solutions" :key="sol.id"
             class="group relative glass-morphism rounded-[2.5rem] p-10 border border-white/10 hover:border-cyan-500/30 transition-all cursor-pointer flex flex-col h-full overflow-hidden">
          <div
              class="absolute -top-10 -right-10 w-40 h-40 bg-gradient-to-br from-cyan-500/5 to-purple-600/5 rounded-full blur-[40px] opacity-0 group-hover:opacity-100 transition-opacity"></div>

          <div class="w-14 h-14 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center mb-8">
            <component :is="sol.icon" class="w-6 h-6 text-gray-400 group-hover:text-cyan-400 transition-colors"/>
          </div>

          <h3 class="text-2xl font-bold mb-4 tracking-tight group-hover:text-white transition-colors">{{
              sol.title
            }}</h3>
          <p class="text-gray-400 text-sm leading-relaxed mb-10 flex-1">{{ sol.description }}</p>

          <div class="space-y-3 mb-10">
            <div v-for="agent in sol.agents" :key="agent" class="flex items-center gap-2">
              <div class="w-1 h-1 rounded-full bg-cyan-500"></div>
              <span class="text-[10px] font-black text-gray-500 uppercase tracking-widest">{{ agent }}</span>
            </div>
          </div>

          <button
              class="w-full py-4 rounded-2xl bg-white/5 border border-white/5 text-[10px] font-black uppercase tracking-widest hover:bg-white hover:text-black transition-all">
            Explore Engine Details
          </button>
        </div>
      </div>

      <!-- Detail Showcase (Mockup) -->
      <div
          class="mt-32 p-16 glass-morphism rounded-[3rem] border border-white/10 flex flex-col md:flex-row items-center gap-20">
        <div class="md:w-1/2">
          <h2 class="text-4xl font-black mb-6 tracking-tight">The "Two-Phase" <br/><span class="text-cyan-400 italic">Consistency Model</span>
          </h2>
          <p class="text-gray-400 leading-relaxed mb-8">Our solutions aren't hardcoded. They are dynamically synthesized
            using a unique two-phase commit strategy, where the Architectural Agent plans the logic and the Designer
            Agent polishes the aesthetics in parallel.</p>
          <ul class="space-y-4 font-mono text-xs text-gray-500">
            <li class="flex gap-4"><span>[01]</span> <span>Architectural Logic Commit</span></li>
            <li class="flex gap-4"><span>[02]</span> <span>Visual Lab Synthesis</span></li>
            <li class="flex gap-4 text-cyan-500"><span>[03]</span> <span>Final Agent Assembly & Audit</span></li>
          </ul>
        </div>
        <div
            class="md:w-1/2 relative bg-black/40 rounded-3xl aspect-[4/3] border border-white/5 flex items-center justify-center p-10">
          <div class="absolute inset-0 bg-gradient-to-tr from-cyan-500/5 to-purple-600/5 blur-[80px]"></div>
          <div class="grid grid-cols-2 gap-4 w-full h-full relative z-10">
            <div v-for="i in 4" :key="i"
                 class="border border-white/5 rounded-2xl bg-[#050505]/40 backdrop-blur flex items-center justify-center italic text-[10px] text-gray-700">
              Module_{{ i }} Preview
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {
  LayoutDashboard as LayoutDashboardIcon,
  Share2 as Share2Icon,
  ShoppingBag as ShoppingBagIcon
} from 'lucide-vue-next'

const solutions = [
  {
    id: 'ecommerce',
    title: 'Cross-Border Commerce',
    icon: ShoppingBagIcon,
    description: 'High-conversion shopping engines trained on global psychology and logistics.',
    agents: ['Psychology Agent', 'Inventory Logic Auditor', 'SEO Specialist']
  },
  {
    id: 'fintech',
    title: 'Financial Dashboards',
    icon: LayoutDashboardIcon,
    description: 'Real-time analytical terminals with high-performance visualizations.',
    agents: ['Data Engineer', 'Visualization Designer', 'Compliance Auditor']
  },
  {
    id: 'community',
    title: 'Social Hub Architect',
    icon: Share2Icon,
    description: 'Vibrant communities with real-time match tracking and automated hubs.',
    agents: ['Interaction Designer', 'Real-time Sync Agent', 'Governance Auditor']
  }
]

</script>
