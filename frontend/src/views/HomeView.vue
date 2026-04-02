<template>
  <div id="top" class="min-h-screen tech-grid relative text-white selection:bg-cyan-500/30">
    <!-- Top Navigation -->
    <nav :class="{'glass-morphism py-4': scrolled}"
         class="fixed top-0 w-full z-50 px-8 py-6 flex justify-between items-center transition-all duration-300">
      <div class="flex items-center gap-2">
        <div
            class="w-8 h-8 bg-gradient-to-br from-cyan-400 to-purple-600 rounded-lg flex items-center justify-center font-bold text-xl text-white">
          L
        </div>
        <span class="text-xl font-bold tracking-tight text-white">LingNow</span>
      </div>
      <div class="hidden md:flex gap-8 text-sm font-medium text-gray-400">
        <router-link class="hover:text-white transition-colors" to="/">{{ t('nav.platform') }}</router-link>
        <router-link class="hover:text-white transition-colors" to="/solutions">{{ t('nav.solutions') }}</router-link>
        <a class="hover:text-white transition-colors" href="#pricing">{{ t('nav.pricing') }}</a>
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
                    @click="currentLang = 'cn'">
              <span>🇨🇳</span> 中文 (简体)
            </button>
            <button class="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 flex items-center gap-2 text-xs font-medium"
                    @click="currentLang = 'en'">
              <span>🇺🇸</span> English (US)
            </button>
          </div>
        </div>

        <router-link class="text-sm font-medium hover:text-cyan-400 transition-colors text-gray-400" to="/login">
          {{ t('nav.login') }}
        </router-link>
        <button class="px-5 py-2 bg-white text-black rounded-full text-sm font-bold hover:bg-cyan-400 transition-all active:scale-95"
                @click="$router.push('/workbench')">
          {{ t('nav.getStarted') }}
        </button>
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
                class="w-full bg-transparent border-none focus:ring-0 text-xl md:text-2xl text-white placeholder-gray-600 resize-none h-32 leading-tight"
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

      <!-- Agent Pulse Monitors -->
      <div class="mt-12 flex gap-10">
        <div v-for="agent in agents" :key="agent.name" class="flex flex-col items-center gap-2">
          <div
              class="w-10 h-10 rounded-full border border-white/10 flex items-center justify-center relative overflow-hidden group cursor-help">
            <div class="absolute inset-0 bg-cyan-500 opacity-0 group-hover:opacity-10 transition-opacity"></div>
            <component :is="agent.icon" class="w-5 h-5 text-gray-500 group-hover:text-cyan-400 transition-colors"/>
            <div
                class="absolute bottom-0 right-0 w-2.5 h-2.5 bg-green-500 rounded-full border-2 border-[#050505] animate-pulse"></div>
          </div>
          <span class="text-[10px] font-bold text-gray-500 uppercase tracking-widest">{{ agent.name }}</span>
        </div>
      </div>
    </main>

    <!-- Industry Solutions (The "Tactical" Differentiation) -->
    <section id="solutions" class="max-w-7xl mx-auto px-6 py-20 border-t border-white/5">
      <div class="text-center mb-16">
        <h2 class="text-4xl font-bold mb-4">{{ t('solutions.title') }}</h2>
        <div class="flex justify-center gap-4">
          <button v-for="cat in solutionCats" :key="cat.id"
                  :class="activeSolution === cat.id ? 'border-cyan-500 bg-cyan-500/10 text-white' : 'border-white/10 text-gray-500 hover:border-white/30'"
                  class="px-6 py-2 rounded-full border text-sm font-bold transition-all"
                  @click="activeSolution = cat.id">
            {{ t(`solutions.cats.${cat.id}`) }}
          </button>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-12 items-center">
        <div class="glass-morphism rounded-3xl p-10 border border-white/10">
          <div class="flex items-center gap-4 mb-8">
            <div
                class="w-12 h-12 rounded-2xl bg-cyan-500/20 flex items-center justify-center border border-cyan-500/30">
              <component :is="solutionCats.find(c => c.id === activeSolution).icon" class="w-6 h-6 text-cyan-400"/>
            </div>
            <h3 class="text-2xl font-bold uppercase tracking-tight">{{ t(`solutions.cats.${activeSolution}`) }}
              Engine</h3>
          </div>
          <p class="text-gray-400 mb-10 leading-relaxed">{{ t(`solutions.details.${activeSolution}`) }}</p>
          <div class="space-y-4">
            <div v-for="agent in solutionAgents[activeSolution]" :key="agent" class="flex items-center gap-3">
              <div class="w-1.5 h-1.5 rounded-full bg-cyan-500 shadow-[0_0_8px_rgba(0,209,255,0.8)]"></div>
              <span class="text-xs font-mono font-bold tracking-widest text-gray-500 uppercase">{{
                  agent
                }} ACTIVATED</span>
            </div>
          </div>
        </div>
        <div class="relative group aspect-video rounded-3xl overflow-hidden glass-morphism border border-white/10">
          <div class="absolute inset-0 bg-gradient-to-br from-cyan-500/10 to-purple-600/10 mix-blend-overlay"></div>
          <div class="flex items-center justify-center h-full text-gray-700 italic text-sm">Industrial Solution Preview
            Loading...
          </div>
        </div>
      </div>
    </section>

    <!-- App Gallery Section (Industrial Bento - Vertical Wrap) -->
    <section id="showcase" class="max-w-7xl mx-auto px-6 py-20 border-t border-white/5">
      <div class="flex justify-between items-end mb-16">
        <div>
          <h2 class="text-3xl font-bold mb-2">{{ t('nav.showcase') }}</h2>
          <p class="text-gray-500">{{ t('showcase.subtitle') }}</p>
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
            <div class="text-[9px] font-bold text-cyan-400 mb-1 uppercase tracking-widest">{{ caseItem.tags[0] }}</div>
            <h3 :class="id === 'fitpulse' ? 'text-4xl' : 'text-xl'"
                class="font-bold transition-colors group-hover:text-cyan-400">{{ caseItem.title }}</h3>
            <p v-if="id === 'fitpulse'" class="text-gray-400 text-sm mt-2 max-w-md leading-relaxed">
              {{ t(`showcase.cases.${id}`) }}</p>
          </div>
        </div>

        <!-- Call to Action Card -->
        <div class="relative group overflow-hidden rounded-3xl border-2 border-dashed border-white/10 glass-morphism h-[320px] flex flex-col items-center justify-center text-center p-8 hover:border-cyan-500/30 transition-all cursor-pointer"
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
          <h4 class="text-lg font-bold mb-2">Build Your Own</h4>
          <p class="text-xs text-gray-500 max-w-[180px]">Convert your ideas into high-fidelity products now.</p>
        </div>
      </div>
    </section>

    <!-- Case Modal -->
    <Transition name="fade">
      <div v-if="selectedCase"
           class="fixed inset-0 z-[100] flex items-center justify-center px-4 py-10 bg-black/90 backdrop-blur-xl">
        <div class="absolute inset-0" @click="selectedCase = null"></div>
        <div
            class="relative w-full max-w-6xl h-full glass-morphism rounded-[2.5rem] overflow-hidden flex flex-col md:flex-row shadow-2xl border border-white/20">
          <button class="absolute top-6 right-6 z-10 w-10 h-10 rounded-full bg-black/50 flex items-center justify-center border border-white/20 hover:bg-white/10 transition-colors"
                  @click="selectedCase = null">
            ✕
          </button>

          <!-- Modal Left: Visual -->
          <div
              class="md:w-3/5 bg-[#050505] h-2/5 md:h-full flex items-center justify-center overflow-hidden border-r border-white/5">
            <img :src="selectedCaseData.img" class="w-full h-full object-cover opacity-70"/>
          </div>

          <!-- Modal Right: Metadata -->
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
                <h4 class="text-[10px] font-bold text-cyan-400 uppercase tracking-[0.2em] mb-4">Industrial Prompt</h4>
                <div
                    class="bg-black p-5 rounded-2xl border border-white/5 font-mono text-xs leading-relaxed text-gray-400 italic shadow-inner">
                  "{{ selectedCaseData.prompt }}"
                </div>
              </div>

              <div>
                <h4 class="text-[10px] font-bold text-cyan-400 uppercase tracking-[0.2em] mb-4">Agent Audit
                  Statistics</h4>
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
              <button class="w-full py-5 bg-white text-black font-black rounded-2xl hover:bg-cyan-400 transition-all active:scale-95 text-center shadow-lg hover:shadow-cyan-500/20"
                      @click="router.push('/workbench')">
                INDUSTRIAL_CLONE_START
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Pricing Section (Commercial Readiness) -->
    <section id="pricing" class="max-w-7xl mx-auto px-6 py-32 text-center border-t border-white/5">
      <div
          class="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-white/10 bg-white/5 text-[10px] font-bold tracking-widest text-[#9D00FF] uppercase mb-6">
        Commercial Ecosystem
      </div>
      <h2 class="text-4xl font-extrabold mb-4">{{ t('pricing.title') }}</h2>
      <p class="text-gray-500 mb-16">{{ t('pricing.subtitle') }}</p>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-8 text-left">
        <!-- Explorer Plan -->
        <div
            class="glass-morphism rounded-3xl p-8 flex flex-col border border-white/10 hover:border-white/20 transition-all">
          <h3 class="text-xl font-bold mb-2">{{ t('pricing.plans.free.name') }}</h3>
          <div class="text-3xl font-extrabold mb-6">$0</div>
          <ul class="flex-1 space-y-4 mb-8">
            <li v-for="feat in t('pricing.plans.free.features')" class="flex items-center gap-2 text-sm text-gray-400">
              <svg class="w-4 h-4 text-green-500" fill="none" stroke="currentColor" stroke-linecap="round"
                   stroke-linejoin="round" stroke-width="3" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              {{ feat }}
            </li>
          </ul>
          <button class="w-full py-3 rounded-xl border border-white/10 font-bold hover:bg-white/5 transition-colors">
            {{ t('pricing.cta') }}
          </button>
        </div>

        <!-- Pro Agent Plan (Highlighted) -->
        <div
            class="glass-morphism rounded-3xl p-8 flex flex-col border border-cyan-500/30 relative overflow-hidden scale-105 shadow-2xl">
          <div class="absolute top-0 right-0 px-4 py-1 bg-cyan-500 text-black text-[10px] font-bold tracking-widest">
            MOST POPULAR
          </div>
          <h3 class="text-xl font-bold mb-2">{{ t('pricing.plans.pro.name') }}</h3>
          <div class="text-3xl font-extrabold mb-6">$20 <span class="text-sm font-normal text-gray-500">/mo</span></div>
          <ul class="flex-1 space-y-4 mb-8">
            <li v-for="feat in t('pricing.plans.pro.features')" class="flex items-center gap-2 text-sm text-gray-200">
              <svg class="w-4 h-4 text-cyan-400" fill="none" stroke="currentColor" stroke-linecap="round"
                   stroke-linejoin="round" stroke-width="3" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              {{ feat }}
            </li>
          </ul>
          <button
              class="w-full py-3 rounded-xl bg-gradient-to-r from-cyan-500 to-purple-600 font-bold hover:shadow-[0_0_20px_rgba(0,209,255,0.3)] transition-all">
            {{ t('pricing.cta') }}
          </button>
        </div>

        <!-- Enterprise -->
        <div
            class="glass-morphism rounded-3xl p-8 flex flex-col border border-white/10 hover:border-white/20 transition-all">
          <h3 class="text-xl font-bold mb-2">{{ t('pricing.plans.factory.name') }}</h3>
          <div class="text-3xl font-extrabold mb-6">Custom</div>
          <ul class="flex-1 space-y-4 mb-8">
            <li v-for="feat in t('pricing.plans.factory.features')"
                class="flex items-center gap-2 text-sm text-gray-400">
              <svg class="w-4 h-4 text-purple-500" fill="none" stroke="currentColor" stroke-linecap="round"
                   stroke-linejoin="round" stroke-width="3" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              {{ feat }}
            </li>
          </ul>
          <button class="w-full py-3 rounded-xl border border-white/10 font-bold hover:bg-white/5 transition-colors">
            Contact Sales
          </button>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer class="py-32 px-8 border-t border-white/5 bg-[#030303]">
      <div class="max-w-7xl mx-auto grid grid-cols-2 md:grid-cols-4 gap-12 mb-20 text-left">
        <div class="col-span-2">
          <div class="flex items-center gap-2 mb-6">
            <div
                class="w-8 h-8 bg-gradient-to-br from-cyan-400 to-purple-600 rounded-lg flex items-center justify-center font-bold text-xl text-white">
              L
            </div>
            <span class="text-xl font-bold tracking-tight text-white">LingNow</span>
          </div>
          <p class="text-gray-500 text-sm max-w-xs leading-relaxed">The industrial AI factory for autonomous code
            generation and high-fidelity prototypes.</p>
        </div>
        <div>
          <h4 class="text-[10px] font-black text-gray-600 uppercase tracking-widest mb-6">Product</h4>
          <ul class="space-y-4 text-sm text-gray-400">
            <li>
              <router-link class="hover:text-cyan-400 transition-colors" to="/solutions">Solutions</router-link>
            </li>
            <li>
              <router-link class="hover:text-cyan-400 transition-colors" to="/workbench">Workbench</router-link>
            </li>
            <li>
              <router-link class="hover:text-cyan-400 transition-colors" to="/updates">Updates</router-link>
            </li>
          </ul>
        </div>
        <div>
          <h4 class="text-[10px] font-black text-gray-600 uppercase tracking-widest mb-6">Company</h4>
          <ul class="space-y-4 text-sm text-gray-400">
            <li>
              <router-link class="hover:text-cyan-400 transition-colors" to="/about">About Us</router-link>
            </li>
            <li>
              <router-link class="hover:text-cyan-400 transition-colors" to="/docs">Documentation</router-link>
            </li>
            <li><a class="hover:text-cyan-400 transition-colors" href="#">Privacy Policy</a></li>
          </ul>
        </div>
      </div>
      <div class="text-gray-700 text-[10px] uppercase tracking-[0.3em] font-black font-mono">
        LingNow Factory_Matrix © 2026. AUTH_SIG_00x1
      </div>
    </footer>
  </div>
</template>

<script setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import {
  LayoutDashboard as LayoutDashboardIcon,
  Share2 as Share2Icon,
  ShoppingBag as ShoppingBagIcon
} from 'lucide-vue-next'

const router = useRouter()
const prompt = ref('')
const scrolled = ref(false)
const currentLang = ref('cn')
const activeSolution = ref('ecommerce')
const selectedCase = ref(null)

const solutionCats = [
  {id: 'ecommerce', icon: 'ShoppingBagIcon'},
  {id: 'dashboard', icon: 'LayoutDashboardIcon'},
  {id: 'social', icon: 'Share2Icon'}
]

const solutionAgents = {
  ecommerce: ['Consumer Psychology Agent', 'SEO Optimization Agent', 'Inventory Logic Auditor'],
  dashboard: ['Data Engineer Agent', 'Visualization Designer', 'Scalability Architect'],
  social: ['Interaction Designer', 'Real-time Sync Agent', 'Governance Auditor']
}

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

const i18n = {
  en: {
    nav: {
      platform: 'Platform',
      solutions: 'Solutions',
      pricing: 'Pricing',
      showcase: 'Showcase',
      login: 'Log in',
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

const agents = [
  {name: 'Architect', icon: 'BoxIcon'},
  {name: 'Designer', icon: 'BrushIcon'},
  {name: 'Engineer', icon: 'CodeIcon'},
  {name: 'Auditor', icon: 'ShieldCheckIcon'}
]

const startGeneration = () => {
  if (!prompt.value.trim()) return
  // Navigate to workbench with initial prompt
  router.push({
    path: '/workbench',
    query: {p: prompt.value}
  })
}

const handleScroll = () => {
  scrolled.value = window.scrollY > 50
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})

// Mock icons as constants for demo
const BoxIcon = {template: '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/><path d="m3.3 7 8.7 5 8.7-5"/><path d="M12 22V12"/></svg>'}
const BrushIcon = {template: '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9.06 11.9 8.07-8.06a2.85 2.85 0 1 1 4.03 4.03l-8.06 8.08"/><path d="M7.07 14.94c-1.66 0-3 1.35-3 3.02 0 1.33-2.5 1.52-2 2.02 1.08 1.1 2.49 2.02 4 2.02 2.21 0 4-1.79 4-4.04 0-1.65-1.35-3.02-3-3.02Z"/></svg>'}
const CodeIcon = {template: '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>'}
const ShieldCheckIcon = {template: '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="m9 12 2 2 4-4"/></svg>'}
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
